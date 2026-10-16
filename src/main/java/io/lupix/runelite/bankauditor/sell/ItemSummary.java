package io.lupix.runelite.bankauditor.sell;

/**
 * Everything the overlays, tooltips and side panel need to know about one item the player owns.
 */
public final class ItemSummary
{
	private final int id;
	private final String name;
	private final boolean tradeable;
	/**
	 * Bank plus inventory.
	 */
	private final long owned;
	private final long looted;
	private final long sellable;
	private final long price;
	private final KeepRule keepRule;

	public ItemSummary(int id, String name, boolean tradeable, long owned, long looted, long sellable, long price, KeepRule keepRule)
	{
		this.id = id;
		this.name = name;
		this.tradeable = tradeable;
		this.owned = owned;
		this.looted = looted;
		this.sellable = sellable;
		this.price = price;
		this.keepRule = keepRule;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public boolean isTradeable()
	{
		return tradeable;
	}

	public long getOwned()
	{
		return owned;
	}

	public long getLooted()
	{
		return looted;
	}

	public long getSellable()
	{
		return sellable;
	}

	public boolean isSellable()
	{
		return sellable > 0;
	}

	/**
	 * Grand Exchange price of one item, 0 when unknown.
	 */
	public long getPrice()
	{
		return price;
	}

	public long getSellableValue()
	{
		return SellableCalculator.saturatedMultiply(sellable, price);
	}

	/**
	 * Null when the item isn't on the keep list.
	 */
	public KeepRule getKeepRule()
	{
		return keepRule;
	}

	public boolean isKept()
	{
		return keepRule != null;
	}
}
