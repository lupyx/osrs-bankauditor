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
	/**
	 * How many were turned into gold with High Level Alchemy.
	 */
	@SerializedName("a")
	private final long alched;
	/**
	 * Gold one cast gave for this item.
	 */
	@SerializedName("h")
	private final long alchPrice;

	public LootItem(int id, String name, long quantity, long price)
	{
		this(id, name, quantity, price, 0);
	}

	public LootItem(int id, String name, long quantity, long price, long left)
	{
		this(id, name, quantity, price, left, 0, 0);
	}

	public LootItem(int id, String name, long quantity, long price, long left, long alched, long alchPrice)
	{
		this.id = id;
		this.name = name;
		this.quantity = quantity;
		this.price = price;
		this.left = Math.max(0, Math.min(quantity, left));
		this.alched = Math.max(0, Math.min(quantity - this.left, alched));
		this.alchPrice = alchPrice;
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
	 * How many of these were high alched.
	 */
	public long getAlched()
	{
		return Math.max(0, Math.min(quantity - getLeft(), alched));
	}

	public long getAlchPrice()
	{
		return alchPrice;
	}

	/**
	 * How many are still items: picked up (or still on the floor) and not alched.
	 */
	public long getKept()
	{
		return quantity - getLeft() - getAlched();
	}

	/**
	 * A copy with {@code more} added to the amount left on the floor.
	 */
	public LootItem withMoreLeft(long more)
	{
		return new LootItem(id, name, quantity, price, getLeft() + more, getAlched(), alchPrice);
	}

	/**
	 * A copy with {@code more} added to the amount alched, each for {@code gold}.
	 */
	public LootItem withMoreAlched(long more, long gold)
	{
		return new LootItem(id, name, quantity, price, getLeft(), getAlched() + more, gold);
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
			&& getAlched() == other.getAlched() && alchPrice == other.alchPrice && Objects.equals(name, other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, quantity, price, getLeft(), getAlched(), alchPrice);
	}

	@Override
	public String toString()
	{
		return quantity + " x " + getName();
	}
}
