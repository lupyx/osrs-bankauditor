package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Loot records in the order they were received, oldest first, with a cap on age and count.
 * Not thread safe: the plugin only touches it from the client thread and hands copies to the UI.
 */
public final class LootHistory
{
	private final List<LootRecord> records = new ArrayList<>();

	public LootHistory()
	{
	}

	public LootHistory(List<LootRecord> records)
	{
		if (records != null)
		{
			for (LootRecord record : records)
			{
				if (record != null)
				{
					this.records.add(record);
				}
			}
			this.records.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
		}
	}

	public void add(LootRecord record)
	{
		// Records nearly always arrive in order. Walk back for the odd one that doesn't.
		int index = records.size();
		while (index > 0 && records.get(index - 1).getTimestamp() > record.getTimestamp())
		{
			index--;
		}
		records.add(index, record);
	}

	/**
	 * Drops records older than {@code maxAgeMillis} (when positive) and then the oldest
	 * records until at most {@code maxRecords} remain (when positive).
	 *
	 * @return true when anything was removed
	 */
	public boolean prune(long now, long maxAgeMillis, int maxRecords)
	{
		int before = records.size();
		if (maxAgeMillis > 0)
		{
			long cutoff = now - maxAgeMillis;
			records.removeIf(r -> r.getTimestamp() < cutoff);
		}
		if (maxRecords > 0 && records.size() > maxRecords)
		{
			records.subList(0, records.size() - maxRecords).clear();
		}
		return records.size() != before;
	}

	/**
	 * Puts {@code updated} where {@code old} is, matched by identity. Does nothing when {@code old} has
	 * already been pruned.
	 */
	public void replace(LootRecord old, LootRecord updated)
	{
		for (int i = 0; i < records.size(); i++)
		{
			if (records.get(i) == old)
			{
				records.set(i, updated);
				return;
			}
		}
	}

	/**
	 * Deletes the given records. Each one is matched by identity, or else by its time, source and kind,
	 * since a record can be swapped for an updated copy (alched, left on the floor) after the panel got it.
	 * Each given record removes at most one record.
	 *
	 * @return how many records were removed
	 */
	public int remove(Collection<LootRecord> doomed)
	{
		int removed = 0;
		for (LootRecord record : doomed)
		{
			int index = indexOf(record);
			if (index >= 0)
			{
				records.remove(index);
				removed++;
			}
		}
		return removed;
	}

	private int indexOf(LootRecord record)
	{
		for (int i = 0; i < records.size(); i++)
		{
			if (records.get(i) == record)
			{
				return i;
			}
		}
		for (int i = 0; i < records.size(); i++)
		{
			LootRecord other = records.get(i);
			if (other.getTimestamp() == record.getTimestamp() && other.getType() == record.getType()
				&& Objects.equals(other.getSource(), record.getSource()))
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * The newest record that still holds some of the item as an item, or null. Loot that gets alched is
	 * usually what just dropped, so the newest drop is the one marked.
	 */
	public LootRecord newestHolding(int itemId)
	{
		for (int i = records.size() - 1; i >= 0; i--)
		{
			LootRecord record = records.get(i);
			if (record.quantityOf(itemId) > 0)
			{
				return record;
			}
		}
		return null;
	}

	public void clear()
	{
		records.clear();
	}

	public int size()
	{
		return records.size();
	}

	public boolean isEmpty()
	{
		return records.isEmpty();
	}

	/**
	 * Oldest first. This is the persisted order.
	 */
	public List<LootRecord> oldestFirst()
	{
		return Collections.unmodifiableList(new ArrayList<>(records));
	}

	public List<LootRecord> newestFirst()
	{
		List<LootRecord> copy = new ArrayList<>(records);
		Collections.reverse(copy);
		return copy;
	}

	/**
	 * Records that contain the given canonical item id, newest first.
	 */
	public List<LootRecord> containing(int itemId)
	{
		List<LootRecord> result = new ArrayList<>();
		for (int i = records.size() - 1; i >= 0; i--)
		{
			LootRecord record = records.get(i);
			if (record.quantityOf(itemId) > 0)
			{
				result.add(record);
			}
		}
		return result;
	}
}
