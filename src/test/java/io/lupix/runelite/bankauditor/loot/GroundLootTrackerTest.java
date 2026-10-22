package io.lupix.runelite.bankauditor.loot;

import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GroundLootTrackerTest
{
	private static final int BONES = 526;
	private static final int HIDE = 1747;
	private static final long TILE = GroundLootTracker.point(2272, 4065, 0);
	// Drops stay private for 100 ticks and despawn after 200
	private static final int VISIBLE = 100;
	private static final int DESPAWN = 200;

	private static LootRecord drop()
	{
		return new LootRecord(1, "Vorkath", LootSourceType.NPC, 732, null, Arrays.asList(
			new LootItem(BONES, "Superior dragon bones", 2, 8000),
			new LootItem(HIDE, "Blue dragonhide", 2, 1500)));
	}

	@Test
	public void privatePileThatDisappearsWasPickedUp()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object pile = new Object();
		tracker.dropRecorded(record, 0);
		tracker.spawned(pile, HIDE, 2, TILE, VISIBLE, DESPAWN, 0);

		assertNull(tracker.despawned(pile, 10));
		assertEquals(0, tracker.followedCount());
	}

	@Test
	public void pileThatTimesOutWasLeft()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object pile = new Object();
		// Piles can show up the tick before the loot event
		tracker.spawned(pile, HIDE, 2, TILE, VISIBLE, DESPAWN, 0);
		tracker.dropRecorded(record, 1);

		GroundLootTracker.Left left = tracker.despawned(pile, DESPAWN);
		assertSame(record, left.getRecord());
		assertEquals(HIDE, left.getItemId());
		assertEquals(2, left.getQuantity());
	}

	@Test
	public void publicPileCountsAsPickedUpOnlyWhenClicked()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object taken = new Object();
		Object stolen = new Object();
		long otherTile = GroundLootTracker.point(2273, 4065, 0);
		tracker.dropRecorded(record, 0);
		tracker.spawned(taken, HIDE, 2, TILE, VISIBLE, DESPAWN, 0);
		tracker.spawned(stolen, BONES, 2, otherTile, VISIBLE, DESPAWN, 0);

		tracker.takeClicked(HIDE, TILE);
		assertNull(tracker.despawned(taken, 150));
		assertEquals(BONES, tracker.despawned(stolen, 150).getItemId());
	}

	@Test
	public void separatePilesOfOneItemAreFollowedApart()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object first = new Object();
		Object second = new Object();
		Object third = new Object();
		tracker.dropRecorded(record, 0);
		tracker.spawned(first, BONES, 1, TILE, VISIBLE, DESPAWN, 0);
		tracker.spawned(second, BONES, 1, TILE, VISIBLE, DESPAWN, 0);
		// More than the drop had, so this one is somebody else's or the player's own
		tracker.spawned(third, BONES, 1, TILE, VISIBLE, DESPAWN, 0);

		assertNull(tracker.despawned(first, 5));
		assertEquals(1, tracker.despawned(second, DESPAWN).getQuantity());
		assertNull(tracker.despawned(third, DESPAWN));
	}

	@Test
	public void unrelatedPilesAreIgnored()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		Object pile = new Object();
		tracker.spawned(pile, HIDE, 2, TILE, VISIBLE, DESPAWN, 0);
		tracker.tick(5);
		tracker.dropRecorded(drop(), 5);

		assertNull(tracker.despawned(pile, DESPAWN));
	}

	@Test
	public void pilesMissingAfterASceneReloadWereLeft()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object hide = new Object();
		Object bones = new Object();
		tracker.dropRecorded(record, 0);
		tracker.spawned(hide, HIDE, 2, TILE, VISIBLE, DESPAWN, 0);
		tracker.spawned(bones, BONES, 2, TILE, VISIBLE, DESPAWN, 0);

		tracker.sceneReloading();
		// The hide is still in the new scene, the bones are not
		Object hideAgain = new Object();
		tracker.spawned(hideAgain, HIDE, 2, TILE, VISIBLE, DESPAWN, 3);
		List<GroundLootTracker.Left> left = tracker.tick(4);

		assertEquals(1, left.size());
		assertEquals(BONES, left.get(0).getItemId());
		assertNull(tracker.despawned(hideAgain, 10));
	}

	@Test
	public void replacedRecordsAreReported()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object hide = new Object();
		Object bones = new Object();
		tracker.dropRecorded(record, 0);
		tracker.spawned(hide, HIDE, 2, TILE, VISIBLE, DESPAWN, 0);
		tracker.spawned(bones, BONES, 2, TILE, VISIBLE, DESPAWN, 0);

		GroundLootTracker.Left hideLeft = tracker.despawned(hide, DESPAWN);
		LootRecord updated = record.withLeft(hideLeft.getItemId(), hideLeft.getQuantity());
		tracker.replace(record, updated);

		assertSame(updated, tracker.despawned(bones, DESPAWN).getRecord());
	}

	@Test
	public void withoutTimersOnlyAClickCounts()
	{
		GroundLootTracker tracker = new GroundLootTracker();
		LootRecord record = drop();
		Object hide = new Object();
		Object bones = new Object();
		tracker.dropRecorded(record, 50);
		tracker.spawned(hide, HIDE, 2, TILE, 0, 0, 50);
		tracker.spawned(bones, BONES, 2, TILE, 0, 0, 50);

		tracker.takeClicked(HIDE, TILE);
		assertNull(tracker.despawned(hide, 55));
		assertEquals(BONES, tracker.despawned(bones, 55).getItemId());
	}

	@Test
	public void recordsKeepTrackOfWhatWasLeft()
	{
		LootRecord record = drop();
		LootRecord updated = record.withLeft(HIDE, 1);

		assertEquals(2, updated.getItems().get(1).getQuantity());
		assertEquals(1, updated.getItems().get(1).getLeft());
		assertEquals(1, updated.quantityOf(HIDE));
		assertEquals(2 * 8000 + 1500, updated.getTotalValue());
		// Never more than the drop had
		assertEquals(0, updated.withLeft(HIDE, 5).quantityOf(HIDE));
		assertSame(record, record.withLeft(995, 1));
		assertTrue(record.getItems().get(1).getLeft() == 0);
	}
}
