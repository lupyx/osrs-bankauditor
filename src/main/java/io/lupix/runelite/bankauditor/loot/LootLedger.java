package io.lupix.runelite.bankauditor.loot;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks how much of each owned item came from loot, as opposed to what the player already had.
 *
 * <p>The client only shows the whole bank while it is open, so loot is booked in two steps.
 * Drops are added as <em>pending</em>. The next time the bank is open and settled, the plugin
 * passes in what the player owns (bank, inventory and worn items) and the ledger compares that
 * with what it saw last time plus the pending loot:
 * <ul>
 *     <li>If the player has less than expected, the shortfall comes out of the loot first. This covers
 *     loot that was never picked up, sold, alched, eaten, cast or dropped. The idea is that anything above
 *     what the player had before counts as loot, so selling or using items eats into the loot before it
 *     touches the player's own supply.</li>
 *     <li>If the player has more than expected, the extra is treated as their own (bought, crafted,
 *     withdrawn from somewhere the plugin can't see), except that loot which went missing earlier in the
 *     same bank visit is restored first. That handles emptying a looting bag into the bank.</li>
 * </ul>
 * The looted amount never exceeds what the player owns.
 *
 * <p>Not thread safe. The plugin uses it from the client thread only.
 */
public final class LootLedger
{
	/**
	 * Bookkeeping for one canonical item id. Fields are package visible for the codec and tests.
	 */
	public static final class Entry
	{
		/**
		 * Loot confirmed present at the last reconcile.
		 */
		long looted;
		/**
		 * Loot received since the last reconcile.
		 */
		long pending;
		/**
		 * Loot that went missing during the current bank visit and may still turn up.
		 */
		long missing;
		/**
		 * What the player owned at the last reconcile, null when the item was never seen.
		 */
		Long lastTotal;

		public Entry()
		{
		}

		public Entry(long looted, long pending, long missing, Long lastTotal)
		{
			this.looted = looted;
			this.pending = pending;
			this.missing = missing;
			this.lastTotal = lastTotal;
		}

		public long getLooted()
		{
			return looted;
		}

		public long getPending()
		{
			return pending;
		}

		public long getMissing()
		{
			return missing;
		}

		public Long getLastTotal()
		{
			return lastTotal;
		}

		Entry copy()
		{
			return new Entry(looted, pending, missing, lastTotal);
		}

		boolean isEmpty()
		{
			return looted == 0 && pending == 0 && missing == 0;
		}
	}

	private final Map<Integer, Entry> entries = new HashMap<>();

	public LootLedger()
	{
	}

	public LootLedger(Map<Integer, Entry> saved)
	{
		if (saved != null)
		{
			for (Map.Entry<Integer, Entry> e : saved.entrySet())
			{
				if (e.getKey() != null && e.getValue() != null)
				{
					Entry copy = e.getValue().copy();
					copy.looted = Math.max(0, copy.looted);
					copy.pending = Math.max(0, copy.pending);
					// Missing loot only means something within one bank visit
					copy.missing = 0;
					entries.put(e.getKey(), copy);
				}
			}
		}
	}

	/**
	 * Books loot that just dropped. It stays pending until the next reconcile.
	 */
	public void addLoot(int itemId, long quantity)
	{
		if (quantity <= 0)
		{
			return;
		}
		entries.computeIfAbsent(itemId, k -> new Entry()).pending += quantity;
	}

	/**
	 * Loot confirmed by the last reconcile.
	 */
	public long getLooted(int itemId)
	{
		Entry e = entries.get(itemId);
		return e == null ? 0 : e.looted;
	}

	public long getPending(int itemId)
	{
		Entry e = entries.get(itemId);
		return e == null ? 0 : e.pending;
	}

	public long getMissing(int itemId)
	{
		Entry e = entries.get(itemId);
		return e == null ? 0 : e.missing;
	}

	/**
	 * Best guess at how much of {@code currentTotal} is loot right now, counting loot that hasn't been
	 * reconciled yet. Used for display so numbers don't jump around before the first reconcile.
	 */
	public long estimateLooted(int itemId, long currentTotal)
	{
		Entry e = entries.get(itemId);
		if (e == null || currentTotal <= 0)
		{
			return 0;
		}
		return Math.min(e.looted + e.pending, currentTotal);
	}

	/**
	 * Compares what the player owns now with the ledger and moves loot in or out as described on the class.
	 * Items missing from {@code totals} are treated as not owned.
	 *
	 * @param totals canonical item id to quantity across bank, inventory and worn items
	 * @return true when any entry changed
	 */
	public boolean reconcile(Map<Integer, Long> totals)
	{
		boolean changed = false;
		for (Map.Entry<Integer, Entry> mapEntry : entries.entrySet())
		{
			Entry e = mapEntry.getValue();
			long owned = Math.max(0, totals.getOrDefault(mapEntry.getKey(), 0L));
			long available = e.looted + e.pending;
			long looted;
			long missing = e.missing;

			if (e.lastTotal == null)
			{
				// Never seen this item before, so there is no baseline. Assume the loot is there if it fits.
				looted = Math.min(available, owned);
				missing += available - looted;
			}
			else
			{
				long unexplained = owned - e.lastTotal - e.pending;
				if (unexplained >= 0)
				{
					long restored = Math.min(unexplained, missing);
					looted = available + restored;
					missing -= restored;
				}
				else
				{
					long consumed = Math.min(-unexplained, available);
					looted = available - consumed;
					missing += consumed;
				}
			}

			// Guards against bad saved data. Normal bookkeeping already keeps this true.
			looted = Math.max(0, Math.min(looted, owned));

			if (looted != e.looted || e.pending != 0 || missing != e.missing || e.lastTotal == null || e.lastTotal != owned)
			{
				changed = true;
			}
			e.looted = looted;
			e.pending = 0;
			e.missing = missing;
			e.lastTotal = owned;
		}
		return changed;
	}

	/**
	 * Called when the bank closes. Loot that hasn't turned up by now is gone for good.
	 */
	public void endBankVisit()
	{
		for (Entry e : entries.values())
		{
			e.missing = 0;
		}
	}

	/**
	 * Stops counting any of this item as loot, for example when the player says a stack is theirs.
	 * The last seen total is kept so future loot is still measured against it.
	 */
	public void forget(int itemId)
	{
		Entry e = entries.get(itemId);
		if (e != null)
		{
			e.looted = 0;
			e.pending = 0;
			e.missing = 0;
		}
	}

	public void clear()
	{
		entries.clear();
	}

	/**
	 * Ids with loot booked against them, confirmed or pending.
	 */
	public Set<Integer> lootedItemIds()
	{
		Set<Integer> ids = new HashSet<>();
		for (Map.Entry<Integer, Entry> e : entries.entrySet())
		{
			if (e.getValue().looted > 0 || e.getValue().pending > 0)
			{
				ids.add(e.getKey());
			}
		}
		return ids;
	}

	/**
	 * Deep copy for saving.
	 */
	public Map<Integer, Entry> snapshot()
	{
		Map<Integer, Entry> copy = new HashMap<>();
		for (Map.Entry<Integer, Entry> e : entries.entrySet())
		{
			copy.put(e.getKey(), e.getValue().copy());
		}
		return Collections.unmodifiableMap(copy);
	}

	public int size()
	{
		return entries.size();
	}
}
