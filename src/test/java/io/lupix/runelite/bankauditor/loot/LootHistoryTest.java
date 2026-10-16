package io.lupix.runelite.bankauditor.loot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LootHistoryTest
{
	private static final long HOUR = 3_600_000L;
	private static final int SHARK = 385;
	private static final int WHIP = 4151;

	static LootRecord record(long timestamp, String source, LootItem... items)
	{
		return new LootRecord(timestamp, source, LootSourceType.NPC, 0, null, Arrays.asList(items));
	}

	static LootItem item(int id, long quantity)
	{
		return new LootItem(id, "Item " + id, quantity, 10);
	}

	@Test
	public void keepsRecordsInTimeOrder()
	{
		LootHistory history = new LootHistory();
		history.add(record(3 * HOUR, "c"));
		history.add(record(1 * HOUR, "a"));
		history.add(record(2 * HOUR, "b"));

		List<LootRecord> oldest = history.oldestFirst();
		assertEquals("a", oldest.get(0).getSource());
		assertEquals("b", oldest.get(1).getSource());
		assertEquals("c", oldest.get(2).getSource());
		assertEquals("c", history.newestFirst().get(0).getSource());
	}

	@Test
	public void loadingSortsAndSkipsNulls()
	{
		LootHistory history = new LootHistory(Arrays.asList(record(2, "b"), null, record(1, "a")));

		assertEquals(2, history.size());
		assertEquals("a", history.oldestFirst().get(0).getSource());
	}

	@Test
	public void pruneByAgeThenCount()
	{
		LootHistory history = new LootHistory();
		for (int i = 0; i < 10; i++)
		{
			history.add(record(i * HOUR, "r" + i));
		}

		// At hour 10, a 5.5 hour window keeps r5..r9
		assertTrue(history.prune(10 * HOUR, (long) (5.5 * HOUR), 0));
		assertEquals(5, history.size());
		assertEquals("r5", history.oldestFirst().get(0).getSource());

		assertTrue(history.prune(10 * HOUR, 0, 2));
		assertEquals(2, history.size());
		assertEquals("r8", history.oldestFirst().get(0).getSource());

		assertFalse(history.prune(10 * HOUR, 0, 0));
	}

	@Test
	public void containingReturnsNewestFirst()
	{
		LootHistory history = new LootHistory();
		history.add(record(1, "old", item(SHARK, 2)));
		history.add(record(2, "whip", item(WHIP, 1)));
		history.add(record(3, "new", item(SHARK, 4), item(WHIP, 1)));

		List<LootRecord> sharks = history.containing(SHARK);
		assertEquals(2, sharks.size());
		assertEquals("new", sharks.get(0).getSource());
		assertEquals("old", sharks.get(1).getSource());
		assertEquals(Collections.emptyList(), history.containing(1));
	}

	@Test
	public void recordTotals()
	{
		LootRecord r = record(1, "x", item(SHARK, 2), item(SHARK, 3), item(WHIP, 1));

		assertEquals(5, r.quantityOf(SHARK));
		assertEquals(60, r.getTotalValue());
	}

	@Test
	public void recordDescribesSource()
	{
		LootRecord npc = new LootRecord(1, "Vorkath", LootSourceType.NPC, 732, null, null);
		LootRecord event = new LootRecord(1, null, null, -1, null, null);

		assertEquals("Vorkath (lvl 732)", npc.describeSource());
		assertEquals("Unknown", event.describeSource());
		assertEquals(LootSourceType.OTHER, event.getType());
		assertTrue(event.getItems().isEmpty());
	}
}
