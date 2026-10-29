package io.lupix.runelite.bankauditor.loot;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LootStateCodecTest
{
	private final LootStateCodec codec = new LootStateCodec(new Gson());

	@Test
	public void ledgerRoundTrip()
	{
		LootLedger ledger = new LootLedger();
		ledger.addLoot(560, 1200);
		Map<Integer, Long> owned = new HashMap<>();
		owned.put(560, 6200L);
		ledger.reconcile(owned);
		ledger.addLoot(385, 3);

		LootLedger loaded = codec.readLedger(codec.writeLedger(ledger));

		assertEquals(1200, loaded.getLooted(560));
		assertEquals(Long.valueOf(6200), loaded.snapshot().get(560).getLastTotal());
		assertEquals(3, loaded.getPending(385));
		assertNull(loaded.snapshot().get(385).getLastTotal());
	}

	@Test
	public void historyRoundTrip()
	{
		LootHistory history = new LootHistory();
		history.add(new LootRecord(1000, "Vorkath", LootSourceType.NPC, 732, new LootLocation(2272, 4065, 0, 9023, 302),
			Arrays.asList(new LootItem(536, "Dragon bones", 2, 2500), new LootItem(1747, "Black dragonhide", 2, 3000))));
		history.add(new LootRecord(2000, "Clue Scroll (Hard)", LootSourceType.EVENT, -1, null,
			Arrays.asList(new LootItem(995, "Coins", 15000, 1))));

		LootHistory loaded = codec.readHistory(codec.writeHistory(history));

		assertEquals(history.oldestFirst(), loaded.oldestFirst());
		LootRecord first = loaded.oldestFirst().get(0);
		assertEquals(9023, first.getLocation().getRegionId());
		assertEquals(5000, first.getItems().get(0).getValue());
		assertEquals(LootSourceType.EVENT, loaded.oldestFirst().get(1).getType());
	}

	@Test
	public void leftOnTheFloorIsSavedAndOldDataHasNone()
	{
		LootHistory history = new LootHistory();
		history.add(new LootRecord(1000, "Vorkath", LootSourceType.NPC, 732, null,
			Arrays.asList(new LootItem(1747, "Black dragonhide", 2, 3000, 1))));

		LootHistory loaded = codec.readHistory(codec.writeHistory(history));
		assertEquals(1, loaded.oldestFirst().get(0).getItems().get(0).getLeft());

		LootHistory old = codec.readHistory("[{\"t\":1000,\"s\":\"Vorkath\",\"k\":\"NPC\",\"c\":732,"
			+ "\"i\":[{\"id\":1747,\"n\":\"Black dragonhide\",\"q\":2,\"p\":3000}]}]");
		assertEquals(0, old.oldestFirst().get(0).getItems().get(0).getLeft());
		assertEquals(2, old.oldestFirst().get(0).quantityOf(1747));
	}

	@Test
	public void whereLootWentIsSavedAndOldDataHasNone()
	{
		LootHistory history = new LootHistory();
		history.add(new LootRecord(1000, "Black dragon", LootSourceType.NPC, 227, null,
			Arrays.asList(new LootItem(536, "Dragon bones", 1, 2500, 0, 0, 0, 1, Destination.BONECRUSHER),
				new LootItem(207, "Grimy ranarr weed", 2, 7000, 0, 0, 0, 1, Destination.HERB_SACK))));

		String json = codec.writeHistory(history);
		assertTrue(json.contains("\"w\":\"bc\""));
		LootHistory loaded = codec.readHistory(json);
		assertEquals(history.oldestFirst(), loaded.oldestFirst());
		LootItem herbs = loaded.oldestFirst().get(0).getItems().get(1);
		assertEquals(Destination.HERB_SACK, herbs.getDestination());
		assertEquals(1, herbs.getMoved());
		assertEquals(1, herbs.getKept());

		LootHistory old = codec.readHistory("[{\"t\":1000,\"s\":\"Vorkath\",\"k\":\"NPC\",\"c\":732,"
			+ "\"i\":[{\"id\":1747,\"n\":\"Black dragonhide\",\"q\":2,\"p\":3000}]}]");
		assertNull(old.oldestFirst().get(0).getItems().get(0).getDestination());
		assertEquals(2, old.oldestFirst().get(0).getItems().get(0).getKept());

		// A destination from a newer version reads as nothing moved
		LootHistory newer = codec.readHistory("[{\"t\":1000,\"s\":\"Vorkath\",\"k\":\"NPC\",\"c\":732,"
			+ "\"i\":[{\"id\":1747,\"n\":\"Black dragonhide\",\"q\":2,\"p\":3000,\"m\":2,\"w\":\"zz\"}]}]");
		assertEquals(0, newer.oldestFirst().get(0).getItems().get(0).getMoved());
		assertEquals(2, newer.oldestFirst().get(0).quantityOf(1747));
	}

	@Test
	public void slayerTaskIsSavedOnDropsAndOldDataHasNone()
	{
		LootHistory history = new LootHistory();
		SlayerTask task = new SlayerTask("Abyssal demons", 500);
		history.add(new LootRecord(1000, "Abyssal demon", LootSourceType.NPC, 124, null,
			Arrays.asList(new LootItem(560, "Death rune", 20, 200)), task));

		String json = codec.writeHistory(history);
		assertEquals(task, codec.readHistory(json).oldestFirst().get(0).getTask());

		LootHistory old = codec.readHistory("[{\"t\":1000,\"s\":\"Vorkath\",\"k\":\"NPC\",\"c\":732,\"i\":[]}]");
		assertNull(old.oldestFirst().get(0).getTask());
	}

	@Test
	public void currentSlayerTaskRoundTrip()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		tracker.update("Abyssal demons", 120, 180, 500, 1);
		String json = codec.writeSlayerTask(tracker.save());

		SlayerTaskTracker loaded = new SlayerTaskTracker();
		loaded.restore(codec.readSlayerTask(json));
		assertEquals(tracker.getCurrent(), loaded.getCurrent());
		// Logging back in to the same task keeps it, even after more kills on another client
		loaded.update("Abyssal demons", 100, 180, 9_000, 50);
		assertEquals(500, loaded.getCurrent().getStart());

		assertNull(codec.writeSlayerTask(new SlayerTaskTracker().save()));
		assertNull(codec.readSlayerTask("not json"));
		assertNull(codec.readSlayerTask(null));
	}

	@Test
	public void tripsRoundTrip()
	{
		TripLog trips = new TripLog(Arrays.asList(1000L, 5000L));
		assertEquals(Arrays.asList(1000L, 5000L), codec.readTrips(codec.writeTrips(trips)).getEnds());
		assertEquals(0, codec.readTrips(null).getEnds().size());
		assertEquals(0, codec.readTrips("{not json").getEnds().size());
	}

	@Test
	public void badDataLoadsEmpty()
	{
		assertEquals(0, codec.readLedger("{not json").size());
		assertEquals(0, codec.readLedger("[1,2,3]").size());
		assertEquals(0, codec.readLedger(null).size());
		assertTrue(codec.readHistory("{\"oops\":true}").isEmpty());
		assertTrue(codec.readHistory("").isEmpty());
	}

	@Test
	public void toleratesMissingFields()
	{
		LootHistory loaded = codec.readHistory("[{\"t\":5,\"s\":\"Goblin\"}]");

		assertEquals(1, loaded.size());
		LootRecord record = loaded.oldestFirst().get(0);
		assertEquals("Goblin", record.getSource());
		assertEquals(LootSourceType.OTHER, record.getType());
		assertTrue(record.getItems().isEmpty());
	}

	@Test
	public void countsRoundTripAndDropJunk()
	{
		Map<Integer, Long> counts = new HashMap<>();
		counts.put(560, 6200L);
		counts.put(4151, 1L);

		assertEquals(counts, codec.readCounts(codec.writeCounts(counts)));
		assertEquals(1, codec.readCounts("{\"560\":5,\"1\":0,\"2\":-3}").size());
		assertNull(codec.readCounts("nope"));
		assertNull(codec.readCounts(""));
	}
}
