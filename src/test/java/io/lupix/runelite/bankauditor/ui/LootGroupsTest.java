package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LootGroupsTest
{
	private static final ZoneId ZONE = ZoneId.of("Europe/Amsterdam");
	private static final long HOUR = 3_600_000L;
	// Thursday 22 October 2026, 18:00 in Amsterdam
	private static final long NOW = ZonedDateTime.of(2026, 10, 22, 18, 0, 0, 0, ZONE).toInstant().toEpochMilli();
	private static final int RUNE = 560;
	private static final int BONES = 526;
	// Region ids: 9007 is Zulrah's shrine, 9023 is Vorkath's island, 12850 is Lumbridge, 1 has no name
	private static final int ZULRAH = 9007;
	private static final int VORKATH = 9023;
	private static final int LUMBRIDGE = 12850;

	private static LootRecord drop(long hoursAgo, String source, int region, int world, LootItem... items)
	{
		return new LootRecord(NOW - hoursAgo * HOUR, source, LootSourceType.NPC, 0,
			region == 0 ? null : new LootLocation(1, 2, 0, region, world), Arrays.asList(items));
	}

	private static LootItem runes(long quantity, long price)
	{
		return new LootItem(RUNE, "Death rune", quantity, price);
	}

	private static LootItem bones(long quantity)
	{
		return new LootItem(BONES, "Bones", quantity, 0);
	}

	@Test
	public void locationCombinesWorldsAndSources()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, runes(10, 200), bones(1)),
			drop(2, "Vorkath", VORKATH, 302, runes(100, 200)),
			drop(3, "Cow", LUMBRIDGE, 330, bones(1)),
			drop(4, "Goblin", LUMBRIDGE, 302, runes(5, 180)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.LOCATION, ZONE);

		assertEquals(2, groups.size());
		LootGroups.Group lumbridge = groups.get(0);
		assertEquals("Lumbridge", lumbridge.getTitle());
		assertEquals("3 drops from Goblin, Cow", lumbridge.getDetail());
		assertEquals(NOW - HOUR, lumbridge.getNewest());
		assertEquals(2000 + 900, lumbridge.getTotalValue());
		List<LootGroups.Line> lines = lumbridge.getLines();
		assertEquals("Death rune", lines.get(0).getName());
		assertEquals(15, lines.get(0).getQuantity());
		assertEquals(2900, lines.get(0).getValue());
		assertEquals(2, lines.get(1).getQuantity());
		assertEquals("Vorkath", groups.get(1).getTitle());
		assertEquals("1 drop from Vorkath", groups.get(1).getDetail());
	}

	@Test
	public void unnamedAndUnknownPlaces()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", 1, 301, bones(1)),
			drop(2, "Goblin", 0, 0, bones(1)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.LOCATION, ZONE);

		assertEquals("Region 1", groups.get(0).getTitle());
		assertEquals("Unknown location", groups.get(1).getTitle());
	}

	@Test
	public void sourceListsPlacesMostFrequentFirst()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", 1, 301, bones(1)),
			drop(2, "Goblin", LUMBRIDGE, 301, bones(1)),
			drop(3, "Goblin", LUMBRIDGE, 301, bones(1)),
			drop(4, "Goblin", VORKATH, 301, bones(1)),
			drop(5, "Goblin", ZULRAH, 301, bones(1)),
			drop(6, "Zulrah", ZULRAH, 301, runes(50, 200)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.SOURCE, ZONE);

		assertEquals(2, groups.size());
		assertEquals("Goblin", groups.get(0).getTitle());
		assertEquals("5 drops at Lumbridge, Region 1, Vorkath and 1 more", groups.get(0).getDetail());
		assertEquals(5, groups.get(0).getLines().get(0).getQuantity());
		assertEquals("Zulrah", groups.get(1).getTitle());
	}

	@Test
	public void daysUseTheLocalCalendar()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, bones(1)),
			// 23:00 the evening before
			drop(19, "Cow", LUMBRIDGE, 301, bones(2)),
			// 01:00 this morning
			drop(17, "Goblin", LUMBRIDGE, 301, bones(3)),
			drop(24 * 3, "Goblin", LUMBRIDGE, 301, bones(4)),
			drop(24 * 365, "Goblin", LUMBRIDGE, 301, bones(5)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.DAY, ZONE);

		assertEquals(4, groups.size());
		assertEquals("Today", LootGroups.dayTitle(groups.get(0).getTitle(), NOW, ZONE));
		assertEquals("2 drops from Goblin", groups.get(0).getDetail());
		assertEquals(4, groups.get(0).getLines().get(0).getQuantity());
		assertEquals("Yesterday", LootGroups.dayTitle(groups.get(1).getTitle(), NOW, ZONE));
		assertEquals("Monday 19 October", LootGroups.dayTitle(groups.get(2).getTitle(), NOW, ZONE));
		assertEquals("Wednesday 22 October 2025", LootGroups.dayTitle(groups.get(3).getTitle(), NOW, ZONE));
	}

	@Test
	public void savedModeNames()
	{
		assertEquals(LootGroups.Mode.SOURCE, LootGroups.Mode.fromName("SOURCE"));
		assertEquals(LootGroups.Mode.DEFAULT, LootGroups.Mode.fromName("something old"));
		assertEquals(LootGroups.Mode.DEFAULT, LootGroups.Mode.fromName(null));
		assertEquals(Collections.emptyList(), LootGroups.group(Collections.emptyList(), LootGroups.Mode.DAY, ZONE));
	}

	@Test
	public void leftOnTheFloorGetsItsOwnLineAndDoesNotCount()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, new LootItem(RUNE, "Death rune", 10, 200, 4)),
			drop(2, "Goblin", LUMBRIDGE, 301, new LootItem(RUNE, "Death rune", 5, 200, 0)));

		LootGroups.Group group = LootGroups.group(records, LootGroups.Mode.SOURCE, ZONE).get(0);

		assertEquals(2, group.getLines().size());
		assertEquals(11, group.getLines().get(0).getQuantity());
		assertEquals(false, group.getLines().get(0).isLeft());
		assertEquals(4, group.getLines().get(1).getQuantity());
		assertEquals(true, group.getLines().get(1).isLeft());
		assertEquals(11 * 200, group.getTotalValue());
	}
}
