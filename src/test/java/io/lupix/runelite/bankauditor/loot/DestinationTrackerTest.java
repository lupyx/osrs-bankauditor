package io.lupix.runelite.bankauditor.loot;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DestinationTrackerTest
{
	private static final int BONES = 536;
	private static final int ASHES = 25775;
	private static final int ETHER = 21820;
	private static final int HIDE = 1747;
	private static final int HERB = 207;

	private static final IntFunction<Destination> EATEN_BY = id -> id == BONES ? Destination.BONECRUSHER
		: id == ASHES ? Destination.ASH_SANCTIFIER
		: id == ETHER ? Destination.ETHER_BRACELET
		: null;
	private static final IntFunction<Destination> NOTHING_OPEN = id -> null;

	private static LootRecord drop(LootItem... items)
	{
		return new LootRecord(1, "Black dragon", LootSourceType.NPC, 227, null, Arrays.asList(items));
	}

	private static LootItem bones(long quantity)
	{
		return new LootItem(BONES, "Dragon bones", quantity, 2500);
	}

	private static void assertMoved(List<DestinationTracker.Moved> moved, LootRecord record, int itemId, long quantity, Destination where)
	{
		assertEquals(1, moved.size());
		assertSame(record, moved.get(0).getRecord());
		assertEquals(itemId, moved.get(0).getItemId());
		assertEquals(quantity, moved.get(0).getQuantity());
		assertEquals(where, moved.get(0).getDestination());
	}

	@Test
	public void chargesUsedAfterTheKillCrushItsBones()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(bones(1), new LootItem(HIDE, "Black dragonhide", 1, 3000));

		assertTrue(tracker.dropRecorded(record, 10, EATEN_BY).isEmpty());
		assertMoved(tracker.chargesUsed(Destination.BONECRUSHER, 1, 11), record, BONES, 1, Destination.BONECRUSHER);
	}

	@Test
	public void chargesCanArriveBeforeTheKill()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(new LootItem(ASHES, "Infernal ashes", 1, 1000));

		assertTrue(tracker.chargesUsed(Destination.ASH_SANCTIFIER, 1, 10).isEmpty());
		assertMoved(tracker.dropRecorded(record, 10, EATEN_BY), record, ASHES, 1, Destination.ASH_SANCTIFIER);
		// The charges are spent, so a second kill gets none of them
		assertTrue(tracker.dropRecorded(drop(new LootItem(ASHES, "Infernal ashes", 1, 1000)), 10, EATEN_BY).isEmpty());
	}

	@Test
	public void chargesFarFromAKillMarkNothing()
	{
		DestinationTracker tracker = new DestinationTracker();
		tracker.dropRecorded(drop(bones(1)), 10, EATEN_BY);
		assertTrue(tracker.chargesUsed(Destination.BONECRUSHER, 1, 12).isEmpty());
		// Ashes charges don't crush bones either
		assertTrue(tracker.chargesUsed(Destination.ASH_SANCTIFIER, 1, 10).isEmpty());

		tracker.tick(20, NOTHING_OPEN);
		assertEquals(0, tracker.pendingCount());
	}

	@Test
	public void chargesAreSharedOutOverKillsOnTheSameTick()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord first = drop(bones(2));
		LootRecord second = drop(bones(2));
		tracker.dropRecorded(first, 10, EATEN_BY);
		tracker.dropRecorded(second, 10, EATEN_BY);

		// Only three charges were left, so the last bone landed on the floor
		List<DestinationTracker.Moved> moved = tracker.chargesUsed(Destination.BONECRUSHER, 3, 10);
		assertEquals(2, moved.size());
		assertSame(first, moved.get(0).getRecord());
		assertEquals(2, moved.get(0).getQuantity());
		assertSame(second, moved.get(1).getRecord());
		assertEquals(1, moved.get(1).getQuantity());
		// Nothing is counted twice
		assertTrue(tracker.chargesUsed(Destination.BONECRUSHER, 2, 10).stream().allMatch(m -> m.getRecord() == second));
	}

	@Test
	public void theBraceletTakesAllTheEther()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(new LootItem(ETHER, "Revenant ether", 14, 180));
		tracker.dropRecorded(record, 10, EATEN_BY);

		assertMoved(tracker.chargesUsed(Destination.ETHER_BRACELET, Long.MAX_VALUE, 10), record, ETHER, 14, Destination.ETHER_BRACELET);
	}

	@Test
	public void pickupThatReachesTheInventoryIsNotMarked()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(new LootItem(HIDE, "Black dragonhide", 2, 3000));

		// The inventory can be updated before or after the pile is gone
		assertTrue(tracker.gained(null, HIDE, 1, 10).isEmpty());
		assertTrue(tracker.pickedUp(record, HIDE, 1, 10).isEmpty());
		assertTrue(tracker.pickedUp(record, HIDE, 1, 20).isEmpty());
		assertTrue(tracker.gained(null, HIDE, 1, 20).isEmpty());

		assertTrue(tracker.tick(30, id -> Destination.LOOTING_BAG).isEmpty());
	}

	@Test
	public void pickupIntoAContainerTheClientSees()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(new LootItem(HIDE, "Black dragonhide", 2, 3000));

		assertTrue(tracker.pickedUp(record, HIDE, 2, 10).isEmpty());
		assertMoved(tracker.gained(Destination.LOOTING_BAG, HIDE, 2, 11), record, HIDE, 2, Destination.LOOTING_BAG);
		assertTrue(tracker.tick(20, NOTHING_OPEN).isEmpty());
	}

	@Test
	public void pickupThatTurnsUpNowhereGoesToTheOpenContainer()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(new LootItem(HERB, "Grimy ranarr weed", 1, 7000));
		tracker.pickedUp(record, HERB, 1, 10);
		// An earlier gain of the same herb doesn't count, it was a pile picked up before
		tracker.gained(null, HERB, 1, 8);

		assertTrue(tracker.tick(11, id -> Destination.HERB_SACK).isEmpty());
		assertMoved(tracker.tick(12, id -> id == HERB ? Destination.HERB_SACK : null), record, HERB, 1, Destination.HERB_SACK);
		assertTrue(tracker.tick(13, id -> Destination.HERB_SACK).isEmpty());
	}

	@Test
	public void pickupWithNowhereToGoIsDropped()
	{
		DestinationTracker tracker = new DestinationTracker();
		tracker.pickedUp(drop(new LootItem(HIDE, "Black dragonhide", 1, 3000)), HIDE, 1, 10);
		assertTrue(tracker.tick(12, NOTHING_OPEN).isEmpty());
		assertEquals(0, tracker.pendingCount());
	}

	@Test
	public void gainsGoToTheClosestPickup()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(new LootItem(HERB, "Grimy ranarr weed", 2, 7000));
		// The first herb went into the sack, which was then full, so the second reached the inventory
		tracker.pickedUp(record, HERB, 1, 10);
		tracker.pickedUp(record, HERB, 1, 11);
		tracker.gained(null, HERB, 1, 11);

		assertMoved(tracker.tick(12, id -> Destination.HERB_SACK), record, HERB, 1, Destination.HERB_SACK);
		assertTrue(tracker.tick(13, id -> Destination.HERB_SACK).isEmpty());
	}

	@Test
	public void replacedRecordsAreFollowed()
	{
		DestinationTracker tracker = new DestinationTracker();
		LootRecord record = drop(bones(1), new LootItem(HERB, "Grimy ranarr weed", 1, 7000));
		tracker.dropRecorded(record, 10, EATEN_BY);
		tracker.pickedUp(record, HERB, 1, 10);
		LootRecord updated = new LootRecord(2, "Black dragon", LootSourceType.NPC, 227, null, record.getItems());
		tracker.replace(record, updated);

		assertSame(updated, tracker.chargesUsed(Destination.BONECRUSHER, 1, 10).get(0).getRecord());
		assertSame(updated, tracker.tick(12, id -> Destination.HERB_SACK).get(0).getRecord());
	}

	@Test
	public void recordsRememberWhereLootWent()
	{
		LootRecord record = drop(bones(3), new LootItem(HERB, "Grimy ranarr weed", 2, 7000));
		LootRecord crushed = record.withMoved(BONES, 2, Destination.BONECRUSHER);
		LootItem bones = crushed.getItems().get(0);
		assertEquals(2, bones.getMoved());
		assertEquals(Destination.BONECRUSHER, bones.getDestination());
		assertEquals(1, bones.getKept());
		// Crushed bones are gone, so they no longer count
		assertEquals(1, crushed.quantityOf(BONES));
		assertEquals(2500 + 2 * 7000, crushed.getTotalValue());

		LootRecord sacked = crushed.withMoved(HERB, 5, Destination.HERB_SACK);
		LootItem herbs = sacked.getItems().get(1);
		assertEquals(2, herbs.getMoved());
		assertEquals(0, herbs.getKept());
		// Herbs in the sack are still the player's
		assertEquals(2, sacked.quantityOf(HERB));
		assertEquals(2500 + 2 * 7000, sacked.getTotalValue());

		// An item keeps one destination, and nothing is marked twice
		assertSame(sacked, sacked.withMoved(HERB, 1, Destination.HERB_SACK));
		assertSame(crushed, crushed.withMoved(BONES, 1, Destination.ASH_SANCTIFIER));
		assertEquals(3, crushed.withMoved(BONES, 1, Destination.BONECRUSHER).getItems().get(0).getMoved());
		// Left and alched amounts still fit around what moved
		LootItem mixed = new LootItem(HERB, "Grimy ranarr weed", 5, 7000, 1, 1, 100, 9, Destination.HERB_SACK);
		assertEquals(3, mixed.getMoved());
		assertEquals(0, mixed.getKept());
		assertEquals(3, mixed.getOwned());
	}
}
