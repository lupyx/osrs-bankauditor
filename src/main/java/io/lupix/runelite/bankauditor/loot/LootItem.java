package io.lupix.runelite.bankauditor.loot;

import java.util.Objects;

/**
 * One item line of a loot drop. The id is always the canonical (unnoted) id so that
 * noted drops and banked stacks share a key.
 */
public final class LootItem
{
	private final int id;
	private final String name;
	private final long quantity;
	/**
	 * Grand Exchange price of a single item when it was looted, 0 if unknown or untradeable.
	 */
	private final long price;

	public LootItem(int id, String name, long quantity, long price)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
		this.price = price;
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name == null ? "Item " + id : name;
	}

	public long getQuantity()
	{
		return quantity;
	}

	public long getPrice()
	{
		return price;
	}

	public long getValue()
	{
		return price * quantity;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof LootItem))
		{
			return false;
		}
		LootItem other = (LootItem) o;
		return id == other.id && quantity == other.quantity && price == other.price && Objects.equals(name, other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, quantity, price);
	}

	@Override
	public String toString()
	{
		return quantity + " x " + getName();
	}
}
