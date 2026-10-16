package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explains a looted amount with the loot records behind it.
 *
 * <p>The ledger doesn't know which drop a given item came from, so this assumes the oldest loot is used
 * or sold first and that what is left is the most recent loot. Records are taken newest first until
 * they cover the looted amount.
 */
public final class LootAttribution
{
	/**
	 * A record and how much of the looted amount it accounts for.
	 */
	public static final class Share
	{
		private final LootRecord record;
		private final long quantity;

		Share(LootRecord record, long quantity)
		{
			this.record = record;
			this.quantity = quantity;
		}

		public LootRecord getRecord()
		{
			return record;
		}

		public long getQuantity()
		{
			return quantity;
		}
	}

	private final List<Share> shares;
	private final long unexplained;

	private LootAttribution(List<Share> shares, long unexplained)
	{
		this.shares = shares;
		this.unexplained = unexplained;
	}

	/**
	 * @param itemId canonical item id
	 * @param looted how much of the item currently counts as loot
	 * @param newestFirst records containing the item, newest first (see {@link LootHistory#containing(int)})
	 */
	public static LootAttribution of(int itemId, long looted, List<LootRecord> newestFirst)
	{
		List<Share> shares = new ArrayList<>();
		long remaining = Math.max(0, looted);
		for (LootRecord record : newestFirst)
		{
			if (remaining <= 0)
			{
				break;
			}
			long quantity = record.quantityOf(itemId);
			if (quantity <= 0)
			{
				continue;
			}
			long share = Math.min(quantity, remaining);
			shares.add(new Share(record, share));
			remaining -= share;
		}
		return new LootAttribution(Collections.unmodifiableList(shares), remaining);
	}

	public List<Share> getShares()
	{
		return shares;
	}

	/**
	 * Looted amount not covered by any kept record, for example after old history was pruned.
	 */
	public long getUnexplained()
	{
		return unexplained;
	}
}
