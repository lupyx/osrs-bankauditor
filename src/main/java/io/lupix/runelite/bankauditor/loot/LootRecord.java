package io.lupix.runelite.bankauditor.loot;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A single loot event: what dropped, when, from what and where.
 */
public final class LootRecord
{
	// Short names keep the saved history small, it is synced with the RuneLite account
	@SerializedName("t")
	private final long timestamp;
	@SerializedName("s")
	private final String source;
	@SerializedName("k")
	private final LootSourceType type;
	@SerializedName("c")
	private final int combatLevel;
	@SerializedName("l")
	private final LootLocation location;
	@SerializedName("i")
	private final List<LootItem> items;

	public LootRecord(long timestamp, String source, LootSourceType type, int combatLevel, LootLocation location, List<LootItem> items)
	{
		this.timestamp = timestamp;
		this.source = source;
		this.type = type;
		this.combatLevel = combatLevel;
		this.location = location;
		this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
	}

	/**
	 * Epoch milliseconds.
	 */
	public long getTimestamp()
	{
		return timestamp;
	}

	public String getSource()
	{
		return source == null || source.isEmpty() ? "Unknown" : source;
	}

	public LootSourceType getType()
	{
		return type == null ? LootSourceType.OTHER : type;
	}

	/**
	 * Combat level of the NPC or player, or a value of 0 or below when there isn't one.
	 */
	public int getCombatLevel()
	{
		return combatLevel;
	}

	/**
	 * May be null when the location could not be read, for example before the player was loaded.
	 */
	public LootLocation getLocation()
	{
		return location;
	}

	public List<LootItem> getItems()
	{
		// Gson can leave the field null when loading hand-edited or truncated data
		return items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
	}

	public long quantityOf(int itemId)
	{
		long total = 0;
		for (LootItem item : getItems())
		{
			if (item.getId() == itemId)
			{
				total += item.getQuantity();
			}
		}
		return total;
	}

	public long getTotalValue()
	{
		long total = 0;
		for (LootItem item : getItems())
		{
			total += item.getValue();
		}
		return total;
	}

	/**
	 * The source name with its combat level when it has one, for example "Vorkath (lvl 732)".
	 */
	/**
	 * Where the loot dropped, for display, or null when unknown. The area name is left out when it only
	 * repeats the source, like Vorkath at Vorkath.
	 */
	public String describeLocation()
	{
		if (location == null)
		{
			return null;
		}
		String area = location.getAreaName();
		if (area != null && area.equalsIgnoreCase(getSource()))
		{
			return location.getWorld() > 0 ? "world " + location.getWorld() : null;
		}
		return location.describe();
	}

	public String describeSource()
	{
		if (combatLevel > 0)
		{
			return getSource() + " (lvl " + combatLevel + ")";
		}
		return getSource();
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof LootRecord))
		{
			return false;
		}
		LootRecord other = (LootRecord) o;
		return timestamp == other.timestamp
			&& combatLevel == other.combatLevel
			&& Objects.equals(source, other.source)
			&& type == other.type
			&& Objects.equals(location, other.location)
			&& Objects.equals(items, other.items);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(timestamp, source, type, combatLevel, location, items);
	}

	@Override
	public String toString()
	{
		return describeSource() + " @ " + timestamp + " " + getItems();
	}
}
