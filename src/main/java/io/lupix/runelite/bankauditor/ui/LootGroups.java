package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Combines loot records into one box per location, source or day for the side panel.
 */
public final class LootGroups
{
	public enum Mode
	{
		DEFAULT("Default", "view_list.png", "Every drop on its own, newest first"),
		LOCATION("Location", "view_location.png", "Combine drops from the same area, across all worlds"),
		SOURCE("Source", "view_source.png", "Combine drops from the same NPC or source"),
		DAY("Day", "view_day.png", "One box per day");

		private final String label;
		private final String icon;
		private final String description;

		Mode(String label, String icon, String description)
		{
			this.label = label;
			this.icon = icon;
			this.description = description;
		}

		public String getLabel()
		{
			return label;
		}

		/**
		 * Resource path of the view button's icon.
		 */
		public String getIcon()
		{
			return "/io/lupix/runelite/bankauditor/" + icon;
		}

		public String getDescription()
		{
			return description;
		}

		/**
		 * The mode saved under this name, or {@link #DEFAULT} for anything unknown.
		 */
		public static Mode fromName(String name)
		{
			for (Mode mode : values())
			{
				if (mode.name().equals(name))
				{
					return mode;
				}
			}
			return DEFAULT;
		}
	}

	/**
	 * Names listed in a group's detail line before the rest become "and N more".
	 */
	private static final int NAMES_SHOWN = 3;
	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

	private LootGroups()
	{
	}

	/**
	 * One item summed over every drop in a group.
	 */
	public static final class Line
	{
		private final int id;
		private final String name;
		private long quantity;
		private long value;

		Line(int id, String name)
		{
			this.id = id;
			this.name = name;
		}

		static Line of(LootItem item)
		{
			Line line = new Line(item.getId(), item.getName());
			line.quantity = item.getQuantity();
			line.value = item.getValue();
			return line;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public long getQuantity()
		{
			return quantity;
		}

		/**
		 * Value at the prices each drop had when it was looted.
		 */
		public long getValue()
		{
			return value;
		}
	}

	public static final class Group
	{
		private final String title;
		private String detail;
		private int drops;
		private long newest = Long.MIN_VALUE;
		private long totalValue;
		private final Map<Integer, Line> lines = new LinkedHashMap<>();
		private final Map<String, Integer> others = new LinkedHashMap<>();
		private final Map<String, Integer> sources = new LinkedHashMap<>();
		private final Map<String, LootSourceType> sourceTypes = new LinkedHashMap<>();

		Group(String title)
		{
			this.title = title;
		}

		public String getTitle()
		{
			return title;
		}

		/**
		 * For example "12 drops from Vorkath, Zulrah".
		 */
		public String getDetail()
		{
			return detail;
		}

		public int getDrops()
		{
			return drops;
		}

		/**
		 * Timestamp of the latest drop in the group.
		 */
		public long getNewest()
		{
			return newest;
		}

		public long getTotalValue()
		{
			return totalValue;
		}

		/**
		 * The source with the most drops in the group, for its icon.
		 */
		public String getTopSource()
		{
			return Collections.max(sources.entrySet(), Map.Entry.comparingByValue()).getKey();
		}

		public LootSourceType getTopSourceType()
		{
			return sourceTypes.get(getTopSource());
		}

		/**
		 * Most valuable first, then by quantity.
		 */
		public List<Line> getLines()
		{
			List<Line> sorted = new ArrayList<>(lines.values());
			sorted.sort(Comparator.comparingLong(Line::getValue).reversed()
				.thenComparing(Comparator.comparingLong(Line::getQuantity).reversed()));
			return sorted;
		}
	}

	/**
	 * @param records any order
	 * @param mode    anything but {@link Mode#DEFAULT}, which shows records as they are
	 * @return groups with the most recent drop first
	 */
	public static List<Group> group(List<LootRecord> records, Mode mode, ZoneId zone)
	{
		Function<LootRecord, String> key;
		Function<LootRecord, String> other;
		String joiner;
		switch (mode)
		{
			case LOCATION:
				key = LootGroups::place;
				other = LootRecord::getSource;
				joiner = " from ";
				break;
			case SOURCE:
				key = LootRecord::getSource;
				other = LootGroups::place;
				joiner = " at ";
				break;
			case DAY:
				key = r -> day(r.getTimestamp(), zone);
				other = LootRecord::getSource;
				joiner = " from ";
				break;
			default:
				throw new IllegalArgumentException("Nothing to group by in " + mode);
		}

		Map<String, Group> groups = new LinkedHashMap<>();
		for (LootRecord record : records)
		{
			Group group = groups.computeIfAbsent(key.apply(record), Group::new);
			group.drops++;
			group.newest = Math.max(group.newest, record.getTimestamp());
			group.others.merge(other.apply(record), 1, Integer::sum);
			group.sources.merge(record.getSource(), 1, Integer::sum);
			group.sourceTypes.putIfAbsent(record.getSource(), record.getType());
			for (LootItem item : record.getItems())
			{
				Line line = group.lines.computeIfAbsent(item.getId(), id -> new Line(id, item.getName()));
				line.quantity += item.getQuantity();
				line.value += item.getValue();
				group.totalValue += item.getValue();
			}
		}

		List<Group> result = new ArrayList<>(groups.values());
		for (Group group : result)
		{
			group.detail = (group.drops == 1 ? "1 drop" : group.drops + " drops") + joiner + names(group.others);
		}
		result.sort(Comparator.comparingLong(Group::getNewest).reversed());
		return result;
	}

	/**
	 * The area name, so drops from every world and every tile of an area land together.
	 */
	static String place(LootRecord record)
	{
		LootLocation location = record.getLocation();
		if (location == null)
		{
			return "Unknown location";
		}
		String area = location.getAreaName();
		return area != null ? area : "Region " + location.getRegionId();
	}

	/**
	 * The date as 2026-10-22, which is what day groups are titled with until {@link #dayTitle} names them.
	 */
	static String day(long timestamp, ZoneId zone)
	{
		return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString();
	}

	/**
	 * "Today" and "Yesterday" for the two latest days, otherwise the date like "Monday 20 October",
	 * with the year added when it isn't this year.
	 */
	public static String dayTitle(String day, long now, ZoneId zone)
	{
		LocalDate date = LocalDate.parse(day);
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		if (date.equals(today))
		{
			return "Today";
		}
		if (date.equals(today.minusDays(1)))
		{
			return "Yesterday";
		}
		String title = DAY_FORMAT.format(date);
		return date.getYear() == today.getYear() ? title : title + " " + date.getYear();
	}

	/**
	 * Most frequent first: "Vorkath, Zulrah and 2 more".
	 */
	private static String names(Map<String, Integer> counts)
	{
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < Math.min(NAMES_SHOWN, sorted.size()); i++)
		{
			if (i > 0)
			{
				sb.append(", ");
			}
			sb.append(sorted.get(i).getKey());
		}
		if (sorted.size() > NAMES_SHOWN)
		{
			sb.append(" and ").append(sorted.size() - NAMES_SHOWN).append(" more");
		}
		return sb.toString();
	}
}
