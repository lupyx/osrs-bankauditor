package io.lupix.runelite.bankauditor.sell;

/**
 * Decides how many of an item the player could sell.
 */
public final class SellableCalculator
{
	private SellableCalculator()
	{
	}

	/**
	 * @param mode       which items count as sellable
	 * @param tradeable  whether the item can be sold on the Grand Exchange
	 * @param owned      quantity in the bank and inventory (worn items are in use, so they don't count)
	 * @param looted     how much of the item counts as loot
	 * @param rule       the keep list entry for the item, or null
	 */
	public static long sellableQuantity(SellMode mode, boolean tradeable, long owned, long looted, KeepRule rule)
	{
		if (!tradeable || owned <= 0)
		{
			return 0;
		}
		if (rule != null && rule.keepsAll())
		{
			return 0;
		}

		long candidates = mode == SellMode.ALL_TRADEABLE ? owned : Math.min(Math.max(0, looted), owned);
		if (rule != null)
		{
			candidates = Math.min(candidates, Math.max(0, owned - rule.getAmount()));
		}
		return candidates;
	}

	/**
	 * Whether a sellable stack is worth listing given the minimum stack value setting.
	 * With a minimum of 0 everything is worth listing, even items without a known price.
	 */
	public static boolean isWorthSelling(long quantity, long priceEach, long minimumValue)
	{
		if (quantity <= 0)
		{
			return false;
		}
		if (minimumValue <= 0)
		{
			return true;
		}
		return saturatedMultiply(quantity, priceEach) >= minimumValue;
	}

	static long saturatedMultiply(long a, long b)
	{
		long hi = Math.multiplyHigh(a, b);
		long lo = a * b;
		if ((hi == 0 && lo >= 0) || (hi == -1 && lo < 0))
		{
			return lo;
		}
		return (a < 0) == (b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
	}
}
