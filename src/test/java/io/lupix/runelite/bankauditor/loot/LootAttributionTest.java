package io.lupix.runelite.bankauditor.loot;

import java.util.Arrays;
import java.util.Collections;
import static io.lupix.runelite.bankauditor.loot.LootHistoryTest.item;
import static io.lupix.runelite.bankauditor.loot.LootHistoryTest.record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LootAttributionTest
{
	private static final int RUNE = 560;

	@Test
	public void newestRecordsExplainTheLootedAmount()
	{
		LootRecord newest = record(3, "Zulrah", item(RUNE, 300));
		LootRecord middle = record(2, "Vorkath", item(RUNE, 500));
		LootRecord oldest = record(1, "Kraken", item(RUNE, 400));

		LootAttribution a = LootAttribution.of(RUNE, 600, Arrays.asList(newest, middle, oldest));

		assertEquals(2, a.getShares().size());
		assertEquals("Zulrah", a.getShares().get(0).getRecord().getSource());
		assertEquals(300, a.getShares().get(0).getQuantity());
		assertEquals("Vorkath", a.getShares().get(1).getRecord().getSource());
		assertEquals(300, a.getShares().get(1).getQuantity());
		assertEquals(0, a.getUnexplained());
	}

	@Test
	public void reportsLootOlderThanTheHistory()
	{
		LootAttribution a = LootAttribution.of(RUNE, 1000, Collections.singletonList(record(1, "Zulrah", item(RUNE, 300))));

		assertEquals(1, a.getShares().size());
		assertEquals(700, a.getUnexplained());
	}

	@Test
	public void nothingLootedMeansNoShares()
	{
		LootAttribution a = LootAttribution.of(RUNE, 0, Collections.singletonList(record(1, "Zulrah", item(RUNE, 300))));

		assertTrue(a.getShares().isEmpty());
		assertEquals(0, a.getUnexplained());
	}

	@Test
	public void skipsRecordsWithoutTheItem()
	{
		LootAttribution a = LootAttribution.of(RUNE, 5, Arrays.asList(record(2, "Other", item(1, 3)), record(1, "Zulrah", item(RUNE, 9))));

		assertEquals(1, a.getShares().size());
		assertEquals("Zulrah", a.getShares().get(0).getRecord().getSource());
		assertEquals(5, a.getShares().get(0).getQuantity());
	}
}
