package io.lupix.runelite.bankauditor.sell;

/**
 * Which items count as sellable.
 */
public enum SellMode
{
	LOOT_ONLY("Looted items only"),
	ALL_TRADEABLE("All tradeable items");

	private final String label;

	SellMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
