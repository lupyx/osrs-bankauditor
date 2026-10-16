package io.lupix.runelite.bankauditor.sell;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntToLongFunction;

/**
 * Items the player still owns that sit in Grand Exchange sell offers, or in the collection box after a sale was
 * aborted. The client doesn't show the collection box, so for an aborted offer the plugin assumes that whatever the
 * player gained of the item since the abort was collected from it.
 *
 * <p>Not thread safe. The plugin uses it from the client thread only.
 */
public final class SellOffers
{
	public enum Kind
	{
		SELLING,
		CANCELLED,
		OTHER
	}

	private static final class Slot
	{
		final int itemId;
		final long unsold;
		final boolean cancelled;
		/**
		 * What the player held of the item outside the Grand Exchange when the offer was aborted.
		 */
		final long heldWhenCancelled;

		Slot(int itemId, long unsold, boolean cancelled, long heldWhenCancelled)
		{
			this.itemId = itemId;
			this.unsold = unsold;
			this.cancelled = cancelled;
			this.heldWhenCancelled = heldWhenCancelled;
		}
	}

	private final Map<Integer, Slot> slots = new HashMap<>();

	/**
	 * @param slot   Grand Exchange slot index
	 * @param itemId canonical item id
	 * @param held   what the player holds of the item outside the Grand Exchange right now
	 */
	public void update(int slot, int itemId, Kind kind, long total, long sold, long held)
	{
		long unsold = total - sold;
		if (kind == Kind.OTHER || itemId <= 0 || unsold <= 0)
		{
			slots.remove(slot);
			return;
		}

		boolean cancelled = kind == Kind.CANCELLED;
		long heldWhenCancelled = 0;
		if (cancelled)
		{
			Slot previous = slots.get(slot);
			heldWhenCancelled = previous != null && previous.cancelled && previous.itemId == itemId
				? previous.heldWhenCancelled
				: held;
		}
		slots.put(slot, new Slot(itemId, unsold, cancelled, heldWhenCancelled));
	}

	public void clear()
	{
		slots.clear();
	}

	/**
	 * @param held what the player holds of an item outside the Grand Exchange right now
	 * @return item id to the quantity still in offers or waiting to be collected
	 */
	public Map<Integer, Long> counts(IntToLongFunction held)
	{
		if (slots.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<Integer, Long> counts = new HashMap<>();
		for (Slot slot : slots.values())
		{
			long quantity = slot.unsold;
			if (slot.cancelled)
			{
				long collected = Math.max(0, held.applyAsLong(slot.itemId) - slot.heldWhenCancelled);
				quantity = Math.max(0, quantity - collected);
			}
			if (quantity > 0)
			{
				counts.merge(slot.itemId, quantity, Long::sum);
			}
		}
		return counts;
	}
}
