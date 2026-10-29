package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Works out where loot went when it never reached the inventory.
 *
 * <p>Some devices take items the moment they drop: a bonecrusher crushes bones, an ash sanctifier scatters
 * demonic ashes and a bracelet of ethereum soaks up revenant ether. Nothing lands on the floor, but the
 * device's charges change, so a kill and a change of charges on the same tick, give or take one, are paired
 * up. The two can arrive in either order.
 *
 * <p>Other loot does land on the floor but skips the inventory when it is picked up, going into an open herb
 * sack, seed box, gem bag or looting bag, the rune pouch or the ammo slot. Pickpocketed items can do the same.
 * A pickup is matched with the inventory or a container the client can see gaining that item around the same
 * tick. When none does, the plugin is asked which open container takes that kind of item.
 *
 * <p>Not thread safe; the plugin calls it from the client thread. Records are matched by identity, and the
 * plugin reports a replaced record with {@link #replace}.
 */
public final class DestinationTracker
{
	/**
	 * A kill and the charges it used can arrive a tick apart, in either order.
	 */
	static final int MATCH_TICKS = 1;
	/**
	 * How far apart a pickup and the item turning up somewhere can be.
	 */
	static final int PICKUP_TICKS = 1;

	/**
	 * Some loot that went somewhere other than the inventory.
	 */
	public static final class Moved
	{
		private final LootRecord record;
		private final int itemId;
		private final long quantity;
		private final Destination destination;

		Moved(LootRecord record, int itemId, long quantity, Destination destination)
		{
			this.record = record;
			this.itemId = itemId;
			this.quantity = quantity;
			this.destination = destination;
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

		public Destination getDestination()
		{
			return destination;
		}
	}

	private static final class Drop
	{
		LootRecord record;
		final int tick;
		/**
		 * What each device could still take from the drop, by item id.
		 */
		final Map<Destination, Map<Integer, Long>> edible = new EnumMap<>(Destination.class);

		Drop(LootRecord record, int tick)
		{
			this.record = record;
			this.tick = tick;
		}
	}

	private static final class Charges
	{
		final Destination device;
		long count;
		final int tick;

		Charges(Destination device, long count, int tick)
		{
			this.device = device;
			this.count = count;
			this.tick = tick;
		}
	}

	private static final class Pickup
	{
		LootRecord record;
		final int itemId;
		long quantity;
		final int tick;

		Pickup(LootRecord record, int itemId, long quantity, int tick)
		{
			this.record = record;
			this.itemId = itemId;
			this.quantity = quantity;
			this.tick = tick;
		}
	}

	private static final class Gain
	{
		/**
		 * Null for the inventory.
		 */
		final Destination where;
		final int itemId;
		long quantity;
		final int tick;

		Gain(Destination where, int itemId, long quantity, int tick)
		{
			this.where = where;
			this.itemId = itemId;
			this.quantity = quantity;
			this.tick = tick;
		}
	}

	private final List<Drop> drops = new ArrayList<>();
	private final List<Charges> charges = new ArrayList<>();
	private final List<Pickup> pickups = new ArrayList<>();
	private final List<Gain> gains = new ArrayList<>();

	/**
	 * A kill's loot was recorded.
	 *
	 * @param eatenBy the device that takes an item as it drops, or null for items no device takes
	 * @return loot that charges seen just before already account for
	 */
	public List<Moved> dropRecorded(LootRecord record, int tick, IntFunction<Destination> eatenBy)
	{
		Drop drop = new Drop(record, tick);
		for (LootItem item : record.getItems())
		{
			Destination device = eatenBy.apply(item.getId());
			if (device != null && item.getKept() > 0)
			{
				drop.edible.computeIfAbsent(device, d -> new LinkedHashMap<>()).merge(item.getId(), item.getKept(), Long::sum);
			}
		}
		if (drop.edible.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Moved> moved = new ArrayList<>();
		for (Iterator<Charges> it = charges.iterator(); it.hasNext(); )
		{
			Charges used = it.next();
			if (Math.abs(used.tick - tick) <= MATCH_TICKS)
			{
				eat(drop, used, moved);
				if (used.count <= 0)
				{
					it.remove();
				}
			}
		}
		drops.add(drop);
		return moved;
	}

	/**
	 * A device took items in by itself.
	 *
	 * @param count how many it took, as told by its charges
	 * @return loot from kills seen just before that this accounts for
	 */
	public List<Moved> chargesUsed(Destination device, long count, int tick)
	{
		Charges used = new Charges(device, count, tick);
		List<Moved> moved = new ArrayList<>();
		for (Drop drop : drops)
		{
			if (used.count > 0 && Math.abs(drop.tick - tick) <= MATCH_TICKS)
			{
				eat(drop, used, moved);
			}
		}
		if (used.count > 0)
		{
			charges.add(used);
		}
		return moved;
	}

	private static void eat(Drop drop, Charges used, List<Moved> moved)
	{
		Map<Integer, Long> items = drop.edible.get(used.device);
		if (items == null)
		{
			return;
		}
		for (Map.Entry<Integer, Long> item : items.entrySet())
		{
			long take = Math.min(used.count, item.getValue());
			if (take > 0)
			{
				item.setValue(item.getValue() - take);
				used.count -= take;
				moved.add(new Moved(drop.record, item.getKey(), take, used.device));
			}
		}
	}

	/**
	 * The player picked up some loot, or was handed it by pickpocketing.
	 *
	 * @return where it went, when something already gained it
	 */
	public List<Moved> pickedUp(LootRecord record, int itemId, long quantity, int tick)
	{
		Pickup pickup = new Pickup(record, itemId, quantity, tick);
		List<Moved> moved = new ArrayList<>();
		while (pickup.quantity > 0)
		{
			Gain gain = closest(gains, g -> g.itemId == itemId && g.quantity > 0, g -> g.tick, tick);
			if (gain == null)
			{
				break;
			}
			match(pickup, gain, moved);
			if (gain.quantity <= 0)
			{
				gains.remove(gain);
			}
		}
		if (pickup.quantity > 0)
		{
			pickups.add(pickup);
		}
		return moved;
	}

	/**
	 * The inventory or a container gained an item.
	 *
	 * @param where null for the inventory
	 * @return loot this accounts for
	 */
	public List<Moved> gained(Destination where, int itemId, long quantity, int tick)
	{
		Gain gain = new Gain(where, itemId, quantity, tick);
		List<Moved> moved = new ArrayList<>();
		while (gain.quantity > 0)
		{
			Pickup pickup = closest(pickups, p -> p.itemId == itemId && p.quantity > 0, p -> p.tick, tick);
			if (pickup == null)
			{
				break;
			}
			match(pickup, gain, moved);
			if (pickup.quantity <= 0)
			{
				pickups.remove(pickup);
			}
		}
		if (gain.quantity > 0)
		{
			gains.add(gain);
		}
		return moved;
	}

	private static void match(Pickup pickup, Gain gain, List<Moved> moved)
	{
		long quantity = Math.min(pickup.quantity, gain.quantity);
		pickup.quantity -= quantity;
		gain.quantity -= quantity;
		// The inventory is where loot is meant to go, so it needs no note
		if (gain.where != null)
		{
			moved.add(new Moved(pickup.record, pickup.itemId, quantity, gain.where));
		}
	}

	/**
	 * The entry within {@link #PICKUP_TICKS} of the tick that is nearest to it, the oldest on a tie.
	 */
	private static <T> T closest(List<T> list, Predicate<T> test, ToIntFunction<T> tickOf, int tick)
	{
		T best = null;
		int bestGap = Integer.MAX_VALUE;
		for (T t : list)
		{
			int gap = Math.abs(tickOf.applyAsInt(t) - tick);
			if (gap <= PICKUP_TICKS && gap < bestGap && test.test(t))
			{
				best = t;
				bestGap = gap;
			}
		}
		return best;
	}

	/**
	 * Once per game tick.
	 *
	 * @param storedIn for a picked up item that turned up nowhere the client can see, the open container that
	 *                 would have taken it, or null when none would
	 * @return picked up loot that went into a container
	 */
	public List<Moved> tick(int tick, IntFunction<Destination> storedIn)
	{
		drops.removeIf(d -> tick - d.tick > MATCH_TICKS);
		charges.removeIf(c -> tick - c.tick > MATCH_TICKS);
		gains.removeIf(g -> tick - g.tick > PICKUP_TICKS);
		List<Moved> moved = new ArrayList<>();
		for (Iterator<Pickup> it = pickups.iterator(); it.hasNext(); )
		{
			Pickup pickup = it.next();
			if (tick - pickup.tick <= PICKUP_TICKS)
			{
				continue;
			}
			it.remove();
			Destination where = storedIn.apply(pickup.itemId);
			if (where != null)
			{
				moved.add(new Moved(pickup.record, pickup.itemId, pickup.quantity, where));
			}
		}
		return moved;
	}

	/**
	 * The plugin swapped a record for an updated copy.
	 */
	public void replace(LootRecord old, LootRecord updated)
	{
		for (Drop drop : drops)
		{
			if (drop.record == old)
			{
				drop.record = updated;
			}
		}
		for (Pickup pickup : pickups)
		{
			if (pickup.record == old)
			{
				pickup.record = updated;
			}
		}
	}

	public void clear()
	{
		drops.clear();
		charges.clear();
		pickups.clear();
		gains.clear();
	}

	int pendingCount()
	{
		return drops.size() + charges.size() + pickups.size() + gains.size();
	}
}
