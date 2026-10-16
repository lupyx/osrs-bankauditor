package io.lupix.runelite.bankauditor.loot;

import java.util.Objects;

/**
 * Where the player stood when loot was received. Inside instances this is the
 * template location, so the region id points at the real area (Vorkath's island, a raid, ...).
 */
public final class LootLocation
{
	private final int x;
	private final int y;
	private final int plane;
	private final int regionId;
	private final int world;

	public LootLocation(int x, int y, int plane, int regionId, int world)
	{
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.regionId = regionId;
		this.world = world;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getPlane()
	{
		return plane;
	}

	public int getRegionId()
	{
		return regionId;
	}

	public int getWorld()
	{
		return world;
	}

	/**
	 * Short form used in tooltips and the side panel, for example "region 9023, world 302".
	 */
	public String describe()
	{
		StringBuilder sb = new StringBuilder("region ").append(regionId);
		if (world > 0)
		{
			sb.append(", world ").append(world);
		}
		return sb.toString();
	}

	/**
	 * Long form with the tile, for example "2272, 4065, 0 (region 9023, world 302)".
	 */
	public String describeWithTile()
	{
		return x + ", " + y + ", " + plane + " (" + describe() + ")";
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof LootLocation))
		{
			return false;
		}
		LootLocation other = (LootLocation) o;
		return x == other.x && y == other.y && plane == other.plane && regionId == other.regionId && world == other.world;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y, plane, regionId, world);
	}

	@Override
	public String toString()
	{
		return describeWithTile();
	}
}
