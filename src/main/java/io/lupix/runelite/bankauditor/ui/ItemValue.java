package io.lupix.runelite.bankauditor.ui;

/**
 * What the side panel needs to know about an item beyond its loot record: its high alchemy price and
 * whether it can go on the Grand Exchange. Read from the item definition on the client thread.
 */
public final class ItemValue
{
	public static final int COINS = 995;

	private final int haPrice;
	private final boolean tradeable;

	public ItemValue(int haPrice, boolean tradeable)
	{
		this.haPrice = haPrice;
		this.tradeable = tradeable;
	}

	public int getHaPrice()
	{
		return haPrice;
	}

	public boolean isTradeable()
	{
		return tradeable;
	}

	/**
	 * Untradeable items can't be alched, and neither can items without an alch price.
	 */
	public boolean isAlchable()
	{
		return tradeable && haPrice > 0;
	}

	/**
	 * High alchemy value of a stack. Coins count at face value, since they are already gold.
	 *
	 * @param value null when the item definition is not known, which counts as not alchable
	 */
	public static long haValue(int itemId, long quantity, ItemValue value)
	{
		if (itemId == COINS)
		{
			return quantity;
		}
		return value != null && value.isAlchable() ? value.haPrice * quantity : 0;
	}
}
