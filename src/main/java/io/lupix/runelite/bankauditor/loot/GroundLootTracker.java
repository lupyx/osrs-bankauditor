package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Follows the piles a kill drops on the floor to tell which ones were picked up and which were left.
 *
 * <p>Only piles that belong to the player are followed. A pile that disappears while it is still private
 * can only have been taken by the player. One that disappears when its timer runs out was left. In between,
 * when other players can see it too, it counts as picked up only if the player clicked to take it. When the
 * scene reloads (teleporting, hopping, logging out) the client drops every pile without telling us, so
 * piles that don't come back with the new scene were left behind.
 *
 * <p>Not thread safe; the plugin calls it from the client thread. Records are matched by identity, and
 * the plugin reports a replaced record with {@link #replace}.
 */
public final class GroundLootTracker
{
	/**
	 * The loot event and the piles it drops can arrive a tick apart, in either order.
	 */
	static final int MATCH_TICKS = 1;
	/**
	 * Piles still followed after this long are forgotten without a verdict. Own drops are gone well before.
	 */
	static final int GIVE_UP_TICKS = 1000;

	/**
	 * What became of a followed pile: picked up, or left on the floor.
	 */
	public static final class Outcome
	{
		private final LootRecord record;
		private final int itemId;
		private final long quantity;
		private final boolean left;

		Outcome(LootRecord record, int itemId, long quantity, boolean left)
		{
			this.record = record;
			this.itemId = itemId;
			this.quantity = quantity;
			this.left = left;
		}

		/**
		 * Whether it stayed on the floor. Otherwise the player picked it up.
		 */
		public boolean isLeft()
		{
			return left;
		}

		public LootRecord getRecord()
		{
			return record;
		}

		public int getItemId()
		{
			return itemId;
		}

		public long getQuantity()
		{
			return quantity;
		}
	}

	private static final class Pile
	{
		Object handle;
		final int itemId;
		final long quantity;
		final long point;
		int visibleTime;
		int despawnTime;
		final int spawnTick;
		LootRecord record;
		boolean clicked;
		boolean seen = true;

		Pile(Object handle, int itemId, long quantity, long point, int visibleTime, int despawnTime, int spawnTick)
		{
			this.handle = handle;
			this.itemId = itemId;
			this.quantity = quantity;
			this.point = point;
			this.visibleTime = visibleTime;
			this.despawnTime = despawnTime;
			this.spawnTick = spawnTick;
		}
	}

	private static final class Drop
	{
		LootRecord record;
		final int tick;
		final Map<Integer, Long> unmatched = new HashMap<>();

		Drop(LootRecord record, int tick)
		{
			this.record = record;
			this.tick = tick;
			for (LootItem item : record.getItems())
			{
				unmatched.merge(item.getId(), item.getQuantity(), Long::sum);
			}
		}
	}

	private final List<Drop> recentDrops = new ArrayList<>();
	private final List<Pile> waiting = new ArrayList<>();
	private final List<Pile> followed = new ArrayList<>();
	private boolean reloading;

	/**
	 * A kill's loot was recorded. Only call this for loot that lands on the floor.
	 */
	public void dropRecorded(LootRecord record, int tick)
	{
		Drop drop = new Drop(record, tick);
		for (Iterator<Pile> it = waiting.iterator(); it.hasNext(); )
		{
			Pile pile = it.next();
			if (Math.abs(pile.spawnTick - tick) <= MATCH_TICKS && claim(drop, pile))
			{
				it.remove();
			}
		}
		recentDrops.add(drop);
	}

	/**
	 * A pile owned by the player appeared.
	 *
	 * @param handle      anything that identifies the pile again when it despawns
	 * @param itemId      canonical id
	 * @param point       the tile, packed by {@link #point}
	 */
	public void spawned(Object handle, int itemId, long quantity, long point, int visibleTime, int despawnTime, int tick)
	{
		if (reloading)
		{
			// The same pile coming back with the new scene
			for (Pile pile : followed)
			{
				if (!pile.seen && pile.itemId == itemId && pile.quantity == quantity && pile.point == point)
				{
					pile.seen = true;
					pile.handle = handle;
					// The tick count can restart after logging back in, so take the timers from the new copy
					pile.visibleTime = visibleTime;
					pile.despawnTime = despawnTime;
					return;
				}
			}
		}

		Pile pile = new Pile(handle, itemId, quantity, point, visibleTime, despawnTime, tick);
		for (Drop drop : recentDrops)
		{
			if (Math.abs(drop.tick - tick) <= MATCH_TICKS && claim(drop, pile))
			{
				return;
			}
		}
		waiting.add(pile);
	}

	private boolean claim(Drop drop, Pile pile)
	{
		Long open = drop.unmatched.get(pile.itemId);
		if (open == null || open < pile.quantity)
		{
			return false;
		}
		drop.unmatched.put(pile.itemId, open - pile.quantity);
		pile.record = drop.record;
		followed.add(pile);
		return true;
	}

	/**
	 * The player clicked to take (or telegrab) an item on this tile.
	 */
	public void takeClicked(int itemId, long point)
	{
		for (Pile pile : followed)
		{
			if (pile.itemId == itemId && pile.point == point)
			{
				pile.clicked = true;
			}
		}
	}

	/**
	 * A pile disappeared from the floor.
	 *
	 * @return whether it was picked up or left behind, or null when it isn't a followed pile
	 */
	public Outcome despawned(Object handle, int tick)
	{
		for (Iterator<Pile> it = followed.iterator(); it.hasNext(); )
		{
			Pile pile = it.next();
			if (pile.handle != handle)
			{
				continue;
			}
			it.remove();
			// A timer at or before the spawn means the client didn't get one, so only the click can tell
			boolean expired = pile.despawnTime > pile.spawnTick && tick >= pile.despawnTime - 1;
			boolean onlyMine = pile.visibleTime > pile.spawnTick && tick < pile.visibleTime;
			boolean pickedUp = !expired && (onlyMine || pile.clicked);
			return new Outcome(pile.record, pile.itemId, pile.quantity, !pickedUp);
		}
		return null;
	}

	/**
	 * The scene is about to reload, so every pile vanishes without a despawn.
	 */
	public void sceneReloading()
	{
		reloading = true;
		for (Pile pile : followed)
		{
			pile.seen = false;
		}
		waiting.clear();
	}

	/**
	 * Once per game tick while logged in.
	 *
	 * @return piles that didn't come back after a scene reload, and so were left behind
	 */
	public List<Outcome> tick(int tick)
	{
		List<Outcome> left = new ArrayList<>();
		if (reloading)
		{
			reloading = false;
			for (Iterator<Pile> it = followed.iterator(); it.hasNext(); )
			{
				Pile pile = it.next();
				if (!pile.seen)
				{
					it.remove();
					left.add(new Outcome(pile.record, pile.itemId, pile.quantity, true));
				}
			}
		}
		recentDrops.removeIf(d -> tick - d.tick > MATCH_TICKS);
		waiting.removeIf(p -> tick - p.spawnTick > MATCH_TICKS);
		followed.removeIf(p -> tick - p.spawnTick > GIVE_UP_TICKS);
		return left;
	}

	/**
	 * The plugin swapped a record for an updated copy.
	 */
	public void replace(LootRecord old, LootRecord updated)
	{
		for (Pile pile : followed)
		{
			if (pile.record == old)
			{
				pile.record = updated;
			}
		}
		for (Drop drop : recentDrops)
		{
			if (drop.record == old)
			{
				drop.record = updated;
			}
		}
	}

	public void clear()
	{
		recentDrops.clear();
		waiting.clear();
		followed.clear();
		reloading = false;
	}

	/**
	 * Packs a tile into one number.
	 */
	public static long point(int x, int y, int plane)
	{
		return ((long) plane << 40) | ((long) (x & 0xFFFFF) << 20) | (y & 0xFFFFF);
	}

	int followedCount()
	{
		return followed.size();
	}
}
