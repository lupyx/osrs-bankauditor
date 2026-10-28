package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import io.lupix.runelite.bankauditor.loot.SlayerTask;
import io.lupix.runelite.bankauditor.loot.TripLog;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
	private static final TripLog NO_TRIPS = new TripLog();

	private static LootRecord drop(long hoursAgo, String source, int region, int world, LootItem... items)
	{
		return new LootRecord(NOW - hoursAgo * HOUR, source, LootSourceType.NPC, 0,
			region == 0 ? null : new LootLocation(1, 2, 0, region, world), Arrays.asList(items));
	}

	private static LootRecord taskDrop(long hoursAgo, String source, SlayerTask task, LootItem... items)
	{
		return new LootRecord(NOW - hoursAgo * HOUR, source, LootSourceType.NPC, 0, null, Arrays.asList(items), task);
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
	public void sourceCombinesWorldsAndAddsUpItems()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, runes(10, 200), bones(1)),
			drop(2, "Vorkath", VORKATH, 302, runes(100, 200)),
			drop(3, "Cow", LUMBRIDGE, 330, bones(1)),
			drop(4, "Goblin", LUMBRIDGE, 302, runes(5, 180)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.SOURCE, LootGroups.Interval.DAY, NO_TRIPS, ZONE);

		assertEquals(3, groups.size());
		LootGroups.Group goblin = groups.get(0);
		assertEquals("Goblin", goblin.getTitle());
		assertEquals("2 drops at Lumbridge", goblin.getDetail());
		assertEquals(NOW - HOUR, goblin.getNewest());
		assertEquals(2000 + 900, goblin.getTotalValue());
		List<LootGroups.Line> lines = goblin.getLines();
		assertEquals("Death rune", lines.get(0).getName());
		assertEquals(15, lines.get(0).getQuantity());
		assertEquals(2900, lines.get(0).getValue());
		assertEquals(1, lines.get(1).getQuantity());
		assertEquals("Vorkath", groups.get(1).getTitle());
		assertEquals("1 drop at Vorkath", groups.get(1).getDetail());
	}

	@Test
	public void unnamedAndUnknownPlaces()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", 1, 301, bones(1)),
			drop(2, "Goblin", 0, 0, bones(1)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.SOURCE, LootGroups.Interval.DAY, NO_TRIPS, ZONE);

		assertEquals("2 drops at Region 1, Unknown location", groups.get(0).getDetail());
	}

	@Test
	public void slayerTasksGetABoxEachAndOtherDropsAreLeftOut()
	{
		SlayerTask first = new SlayerTask("Abyssal demons", NOW - 10 * HOUR);
		SlayerTask second = new SlayerTask("Abyssal demons", NOW - 3 * HOUR);
		List<LootRecord> records = Arrays.asList(
			taskDrop(1, "Greater abyssal demon", second, runes(10, 200)),
			drop(2, "Goblin", LUMBRIDGE, 301, bones(1)),
			taskDrop(2, "Abyssal demon", second, runes(5, 200)),
			taskDrop(8, "Abyssal demon", first, runes(1, 200)));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.TASK, LootGroups.Interval.DAY, NO_TRIPS, ZONE);

		assertEquals(2, groups.size());
		assertEquals("Abyssal demons", groups.get(0).getTaskName());
		assertEquals("2 drops from Greater abyssal demon, Abyssal demon", groups.get(0).getDetail());
		assertEquals(15, groups.get(0).getLines().get(0).getQuantity());
		assertEquals("1 drop from Abyssal demon", groups.get(1).getDetail());
	}

	@Test
	public void theLocationViewIsGone()
	{
		assertEquals(LootGroups.Mode.DEFAULT, LootGroups.Mode.fromName("LOCATION"));
		assertEquals(LootGroups.Mode.TASK, LootGroups.Mode.values()[LootGroups.Mode.TRIP.ordinal() + 1]);
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

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.SOURCE, LootGroups.Interval.DAY, NO_TRIPS, ZONE);

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

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.DAY, NO_TRIPS, ZONE);

		assertEquals(4, groups.size());
		assertEquals("Today", title(groups.get(0), LootGroups.Interval.DAY));
		assertEquals("2 drops from Goblin", groups.get(0).getDetail());
		assertEquals(4, groups.get(0).getLines().get(0).getQuantity());
		assertEquals("Yesterday", title(groups.get(1), LootGroups.Interval.DAY));
		assertEquals("Monday 19 October", title(groups.get(2), LootGroups.Interval.DAY));
		assertEquals("Wednesday 22 October 2025", title(groups.get(3), LootGroups.Interval.DAY));
	}

	private static String title(LootGroups.Group group, LootGroups.Interval interval)
	{
		return LootGroups.periodTitle(group.getTitle(), interval, NOW, ZONE);
	}

	@Test
	public void tripsSplitWhereTheyEnded()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Vorkath", VORKATH, 301, runes(10, 200)),
			drop(2, "Vorkath", VORKATH, 301, runes(10, 200)),
			drop(5, "Zulrah", ZULRAH, 301, bones(1)),
			drop(6, "Zulrah", ZULRAH, 301, bones(1)),
			drop(30, "Goblin", LUMBRIDGE, 301, bones(1)));
		TripLog trips = new TripLog(Arrays.asList(NOW - 29 * HOUR, NOW - 4 * HOUR));

		List<LootGroups.Group> groups = LootGroups.group(records, LootGroups.Mode.TRIP, LootGroups.Interval.DAY, trips, ZONE);

		assertEquals(3, groups.size());
		assertEquals("Current trip", LootGroups.tripTitle(groups.get(0), NOW, ZONE));
		assertEquals("2 drops from Vorkath", groups.get(0).getDetail());
		assertEquals(20, groups.get(0).getLines().get(0).getQuantity());
		assertEquals("Today 12:00", LootGroups.tripTitle(groups.get(1), NOW, ZONE));
		assertEquals("2 drops from Zulrah", groups.get(1).getDetail());
		assertEquals(HOUR, groups.get(1).getNewest() - groups.get(1).getOldest());
		assertEquals("Yesterday 12:00", LootGroups.tripTitle(groups.get(2), NOW, ZONE));
	}

	@Test
	public void hoursWeeksAndMonths()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, bones(1)),
			drop(2, "Goblin", LUMBRIDGE, 301, bones(1)),
			drop(24 + 1, "Goblin", LUMBRIDGE, 301, bones(1)),
			drop(24 * 10, "Goblin", LUMBRIDGE, 301, bones(1)),
			drop(24 * 60, "Goblin", LUMBRIDGE, 301, bones(1)));

		List<LootGroups.Group> hours = LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.HOUR, NO_TRIPS, ZONE);
		assertEquals("Today 17:00", title(hours.get(0), LootGroups.Interval.HOUR));
		assertEquals("Today 16:00", title(hours.get(1), LootGroups.Interval.HOUR));
		assertEquals("Yesterday 17:00", title(hours.get(2), LootGroups.Interval.HOUR));
		assertEquals("Mon 12 Oct 18:00", title(hours.get(3), LootGroups.Interval.HOUR));

		// Thursday 22 October, so this week started on Monday the 19th
		List<LootGroups.Group> weeks = LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.WEEK, NO_TRIPS, ZONE);
		assertEquals(3, weeks.size());
		assertEquals("This week", title(weeks.get(0), LootGroups.Interval.WEEK));
		assertEquals("3 drops from Goblin", weeks.get(0).getDetail());
		assertEquals("Last week", title(weeks.get(1), LootGroups.Interval.WEEK));
		assertEquals("Week of 17 August", title(weeks.get(2), LootGroups.Interval.WEEK));

		List<LootGroups.Group> months = LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.MONTH, NO_TRIPS, ZONE);
		assertEquals(2, months.size());
		assertEquals("This month", title(months.get(0), LootGroups.Interval.MONTH));
		assertEquals("August", title(months.get(1), LootGroups.Interval.MONTH));
	}

	@Test
	public void alchedItemsGetTheirOwnLineWorthTheGold()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, new LootItem(RUNE, "Death rune", 10, 200, 2, 3, 120)));

		LootGroups.Group group = LootGroups.group(records, LootGroups.Mode.SOURCE, LootGroups.Interval.DAY, NO_TRIPS, ZONE).get(0);

		List<LootGroups.Line> lines = group.getLines();
		assertEquals(3, lines.size());
		assertEquals(LootGroups.Line.Kind.KEPT, lines.get(0).getKind());
		assertEquals(5, lines.get(0).getQuantity());
		assertEquals(LootGroups.Line.Kind.ALCHED, lines.get(1).getKind());
		assertEquals(3 * 120, lines.get(1).getValue());
		assertEquals(LootGroups.Line.Kind.LEFT, lines.get(2).getKind());
		assertEquals(5 * 200 + 3 * 120, group.getTotalValue());
	}

	@Test
	public void savedModeNames()
	{
		assertEquals(LootGroups.Mode.TIME, LootGroups.Mode.fromName("DAY"));
		assertEquals(LootGroups.Interval.WEEK, LootGroups.Interval.fromName("WEEK"));
		assertEquals(LootGroups.Interval.DAY, LootGroups.Interval.fromName(null));
		assertEquals(LootGroups.Mode.SOURCE, LootGroups.Mode.fromName("SOURCE"));
		assertEquals(LootGroups.Mode.DEFAULT, LootGroups.Mode.fromName("something old"));
		assertEquals(LootGroups.Mode.DEFAULT, LootGroups.Mode.fromName(null));
		assertEquals(Collections.emptyList(), LootGroups.group(Collections.emptyList(), LootGroups.Mode.TIME, LootGroups.Interval.DAY, NO_TRIPS, ZONE));
	}

	@Test
	public void leftOnTheFloorGetsItsOwnLineAndDoesNotCount()
	{
		List<LootRecord> records = Arrays.asList(
			drop(1, "Goblin", LUMBRIDGE, 301, new LootItem(RUNE, "Death rune", 10, 200, 4)),
			drop(2, "Goblin", LUMBRIDGE, 301, new LootItem(RUNE, "Death rune", 5, 200, 0)));

		LootGroups.Group group = LootGroups.group(records, LootGroups.Mode.SOURCE, LootGroups.Interval.DAY, NO_TRIPS, ZONE).get(0);

		assertEquals(2, group.getLines().size());
		assertEquals(11, group.getLines().get(0).getQuantity());
		assertEquals(false, group.getLines().get(0).isLeft());
		assertEquals(4, group.getLines().get(1).getQuantity());
		assertEquals(true, group.getLines().get(1).isLeft());
		assertEquals(11 * 200, group.getTotalValue());
	}

	@Test
	public void yearsAndACustomRange()
	{
		LootRecord today = drop(1, "Goblin", LUMBRIDGE, 301, runes(10, 200));
		LootRecord tenDays = drop(24 * 10, "Vorkath", VORKATH, 301, runes(5, 200));
		LootRecord lastYear = drop(24 * 300, "Goblin", LUMBRIDGE, 301, bones(1));
		LootRecord longAgo = drop(24 * 800, "Goblin", LUMBRIDGE, 301, bones(1));
		List<LootRecord> records = Arrays.asList(today, tenDays, lastYear, longAgo);

		List<LootGroups.Group> years = LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.YEAR, NO_TRIPS, ZONE);
		assertEquals(3, years.size());
		assertEquals("This year", title(years.get(0), LootGroups.Interval.YEAR));
		assertEquals("Last year", title(years.get(1), LootGroups.Interval.YEAR));
		assertEquals("2024", title(years.get(2), LootGroups.Interval.YEAR));

		// 22 October is today, so 12 October to today holds the drops of today and ten days ago
		LootGroups.DateRange range = new LootGroups.DateRange(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 12));
		assertEquals(LocalDate.of(2026, 10, 12), range.getFrom());
		List<LootGroups.Group> custom = LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.CUSTOM, range, NO_TRIPS, ZONE);
		assertEquals(1, custom.size());
		assertEquals(2, custom.get(0).getDrops());
		assertEquals(Arrays.asList(today, tenDays), custom.get(0).getRecords());
		assertEquals(3000, custom.get(0).getTotalValue());

		LootGroups.DateRange empty = new LootGroups.DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		assertTrue(LootGroups.group(records, LootGroups.Mode.TIME, LootGroups.Interval.CUSTOM, empty, NO_TRIPS, ZONE).isEmpty());
	}

	@Test
	public void customRangeDaysAndTitles()
	{
		LootGroups.DateRange range = new LootGroups.DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 20));
		long startOfTwelfth = ZonedDateTime.of(2026, 10, 12, 0, 0, 0, 0, ZONE).toInstant().toEpochMilli();
		long endOfTwentieth = ZonedDateTime.of(2026, 10, 21, 0, 0, 0, 0, ZONE).toInstant().toEpochMilli() - 1;
		assertTrue(range.contains(startOfTwelfth, ZONE));
		assertTrue(range.contains(endOfTwentieth, ZONE));
		assertFalse(range.contains(startOfTwelfth - 1, ZONE));
		assertFalse(range.contains(endOfTwentieth + 1, ZONE));

		assertEquals("12 Oct to 20 Oct", range.title(NOW, ZONE));
		assertEquals("12 Oct", new LootGroups.DateRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12)).title(NOW, ZONE));
		assertEquals("3 Mar to 9 Mar 2025", new LootGroups.DateRange(LocalDate.of(2025, 3, 3), LocalDate.of(2025, 3, 9)).title(NOW, ZONE));
		assertEquals("28 Dec 2025 to 4 Jan 2026", new LootGroups.DateRange(LocalDate.of(2025, 12, 28), LocalDate.of(2026, 1, 4)).title(NOW, ZONE));
	}

	@Test
	public void savedRangeFallsBackToTheLastWeek()
	{
		LootGroups.DateRange saved = LootGroups.DateRange.parse("2026-09-01", "2026-09-30", NOW, ZONE);
		assertEquals(LocalDate.of(2026, 9, 1), saved.getFrom());
		assertEquals(LocalDate.of(2026, 9, 30), saved.getTo());

		for (String broken : new String[]{"", "yesterday", null})
		{
			LootGroups.DateRange fallback = LootGroups.DateRange.parse(broken, "2026-09-30", NOW, ZONE);
			assertEquals(LocalDate.of(2026, 10, 16), fallback.getFrom());
			assertEquals(LocalDate.of(2026, 10, 22), fallback.getTo());
		}
		assertEquals(LootGroups.Interval.CUSTOM, LootGroups.Interval.fromName("CUSTOM"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void customRangeNeedsItsDays()
	{
		LootGroups.group(Collections.emptyList(), LootGroups.Mode.TIME, LootGroups.Interval.CUSTOM, NO_TRIPS, ZONE);
	}
}
