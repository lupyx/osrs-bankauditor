package io.lupix.runelite.bankauditor.loot;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * One item line of a loot drop. The id is always the canonical (unnoted) id so that
 * noted drops and banked stacks share a key.
 */
public final class LootItem
{
	private final int id;
	@SerializedName("n")
	private final String name;
	@SerializedName("q")
	private final long quantity;
	/**
	 * Grand Exchange price of a single item when it was looted, 0 if unknown or untradeable.
	 */
	@SerializedName("p")
	private final long price;
	/**
	 * How many were left on the floor and never picked up. Missing (0) in data saved before this existed.
	 */
	@SerializedName("u")
	private final long left;

	public LootItem(int id, String name, long quantity, long price)
	{
		this(id, name, quantity, price, 0);
	}

	public LootItem(int id, String name, long quantity, long price, long left)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
		this.price = price;
		this.left = Math.max(0, Math.min(quantity, left));
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

	/**
	 * How many of these were left on the floor.
	 */
	public long getLeft()
	{
		return Math.max(0, Math.min(quantity, left));
	}

	/**
	 * How many were actually picked up, or are still on the floor waiting.
	 */
	public long getKept()
	{
		return quantity - getLeft();
	}

	/**
	 * A copy with {@code more} added to the amount left on the floor.
	 */
	public LootItem withMoreLeft(long more)
	{
		return new LootItem(id, name, quantity, price, getLeft() + more);
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
		return id == other.id && quantity == other.quantity && price == other.price && getLeft() == other.getLeft()
			&& Objects.equals(name, other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, quantity, price, getLeft());
	}

	@Override
	public String toString()
	{
		return quantity + " x " + getName();
	}
}
