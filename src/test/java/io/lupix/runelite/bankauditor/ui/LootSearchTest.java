package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class LootSearchTest
{
	// Region ids: 9023 is Vorkath's island, 12850 is Lumbridge
	private static final LootRecord VORKATH = new LootRecord(3, "Vorkath", LootSourceType.NPC, 732,
		new LootLocation(1, 2, 0, 9023, 302), Arrays.asList(
			new LootItem(22124, "Superior dragon bones", 2, 8000),
			new LootItem(1747, "Black dragonhide", 25, 2000)));
	private static final LootRecord GOBLIN = new LootRecord(2, "Goblin", LootSourceType.NPC, 2,
		new LootLocation(1, 2, 0, 12850, 302), Arrays.asList(
			new LootItem(526, "Bones", 1, 100),
			new LootItem(995, "Coins", 5, 1)));
	private static final LootRecord COW = new LootRecord(1, "Cow", LootSourceType.NPC, 2,
		new LootLocation(1, 2, 0, 12850, 302), Arrays.asList(new LootItem(1739, "Cowhide", 1, 150)));
	private static final List<LootRecord> ALL = Arrays.asList(VORKATH, GOBLIN, COW);

	@Test
	public void blankKeepsEverything()
	{
		assertSame(ALL, LootSearch.filter(ALL, "  ", true, true, true));
		assertSame(ALL, LootSearch.filter(ALL, null, true, true, true));
	}

	@Test
	public void sourceAndLocationKeepWholeDrops()
	{
		assertEquals(Arrays.asList(VORKATH), LootSearch.filter(ALL, "VORK", true, false, false));
		assertEquals(Arrays.asList(GOBLIN, COW), LootSearch.filter(ALL, "lumbridge", false, true, false));
		assertEquals(Arrays.asList(), LootSearch.filter(ALL, "lumbridge", true, false, true));
	}

	@Test
	public void itemMatchesKeepOnlyThoseItems()
	{
		List<LootRecord> bones = LootSearch.filter(ALL, "bones", false, false, true);

		assertEquals(2, bones.size());
		assertEquals(1, bones.get(0).getItems().size());
		assertEquals("Superior dragon bones", bones.get(0).getItems().get(0).getName());
		assertEquals(16000, bones.get(0).getTotalValue());
		assertEquals("Vorkath", bones.get(0).getSource());
		assertEquals("Bones", bones.get(1).getItems().get(0).getName());
	}

	@Test
	public void wholeDropKeptWhenEveryItemOrTheSourceMatches()
	{
		// Both of Vorkath's items have "dragon" in their name
		assertSame(VORKATH, LootSearch.filter(ALL, "dragon", false, false, true).get(0));
		// "cow" matches the source, so the drop stays whole even with item matching on
		assertSame(COW, LootSearch.filter(ALL, "cow", true, false, true).get(0));
	}
}
