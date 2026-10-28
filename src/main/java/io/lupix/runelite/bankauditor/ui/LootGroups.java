package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import io.lupix.runelite.bankauditor.loot.TripLog;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Combines loot records into one box per source, stretch of time, trip or slayer task for the side panel.
 */
public final class LootGroups
{
	public enum Mode
	{
		DEFAULT("Default", "view_list.png", "Every drop on its own"),
		SOURCE("Source", "view_source.png", "Combine drops from the same NPC or source"),
		TIME("Time range", "view_day.png", "Combine drops by hour, day, week or month"),
		TRIP("Trip", "view_trip.png", "Combine drops into trips, which end when you bank"),
		TASK("Slayer task", "view_slayer.png", "Combine drops from kills that counted for each slayer task");

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
			if ("DAY".equals(name))
			{
				// Saved before the day view became the time range view
				return TIME;
			}
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
	 * How much time one box covers in the time range view. {@link #CUSTOM} is one box for the dates the user
	 * picked.
	 */
	public enum Interval
	{
		HOUR("Hour"),
		DAY("Day"),
		WEEK("Week"),
		MONTH("Month"),
		YEAR("Year"),
		CUSTOM("Custom range");

		private final String label;

		Interval(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		/**
		 * The interval saved under this name, or {@link #DAY} for anything unknown.
		 */
		public static Interval fromName(String name)
		{
			for (Interval interval : values())
			{
				if (interval.name().equals(name))
				{
					return interval;
				}
			}
			return DAY;
		}
	}

	/**
	 * Names listed in a group's detail line before the rest become "and N more".
	 */
	private static final int NAMES_SHOWN = 3;
	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT_DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
	private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	/**
	 * Key of the trip that is still going.
	 */
	static final String CURRENT_TRIP = "-1";
	/**
	 * Key of the one box in a custom range.
	 */
	static final String CUSTOM_RANGE = "range";

	/**
	 * The days a custom range covers, both ends included. Picked the wrong way round, it is turned around.
	 */
	public static final class DateRange
	{
		private final LocalDate from;
		private final LocalDate to;

		public DateRange(LocalDate from, LocalDate to)
		{
			boolean backwards = from.isAfter(to);
			this.from = backwards ? to : from;
			this.to = backwards ? from : to;
		}

		/**
		 * The last 7 days up to today.
		 */
		public static DateRange lastWeek(long now, ZoneId zone)
		{
			LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
			return new DateRange(today.minusDays(6), today);
		}

		/**
		 * The range saved as two ISO dates, or the last 7 days when either is missing or can't be read.
		 */
		public static DateRange parse(String from, String to, long now, ZoneId zone)
		{
			if (from != null && to != null)
			{
				try
				{
					return new DateRange(LocalDate.parse(from), LocalDate.parse(to));
				}
				catch (DateTimeParseException e)
				{
					// Fall through to the default
				}
			}
			return lastWeek(now, zone);
		}

		public LocalDate getFrom()
		{
			return from;
		}

		public LocalDate getTo()
		{
			return to;
		}

		public boolean contains(long timestamp, ZoneId zone)
		{
			LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
			return !date.isBefore(from) && !date.isAfter(to);
		}

		/**
		 * "12 Oct to 20 Oct", or just "12 Oct" for one day. The year goes at the end when it isn't this year,
		 * and on both ends when the range crosses into a new year.
		 */
		public String title(long now, ZoneId zone)
		{
			int thisYear = Instant.ofEpochMilli(now).atZone(zone).getYear();
			String start = SHORT_DATE_FORMAT.format(from);
			String end = SHORT_DATE_FORMAT.format(to);
			if (from.getYear() != to.getYear())
			{
				return start + " " + from.getYear() + " to " + end + " " + to.getYear();
			}
			String year = to.getYear() == thisYear ? "" : " " + to.getYear();
			return (from.equals(to) ? start : start + " to " + end) + year;
		}
	}

	private LootGroups()
	{
	}

	/**
	 * One item summed over every drop in a group. What was alched and what was left on the floor get lines
	 * of their own.
	 */
	public static final class Line
	{
		public enum Kind
		{
			KEPT,
			ALCHED,
			LEFT
		}

		/**
		 * Kept lines first, then alched, then left on the floor, each most valuable first.
		 */
		static final Comparator<Line> ORDER = Comparator.comparing(Line::getKind)
			.thenComparing(Comparator.comparingLong(Line::getValue).reversed())
			.thenComparing(Comparator.comparingLong(Line::getQuantity).reversed());

		private final int id;
		private final String name;
		private final Kind kind;
		private long quantity;
		private long value;
		private long alchPrice;

		Line(int id, String name, Kind kind)
		{
			this.id = id;
			this.name = name;
			this.kind = kind;
		}

		/**
		 * The item as up to three lines: kept, alched and left on the floor.
		 */
		static List<Line> of(LootItem item)
		{
			List<Line> lines = new ArrayList<>(3);
			if (item.getKept() > 0)
			{
				lines.add(part(item, Kind.KEPT, item.getKept()));
			}
			if (item.getAlched() > 0)
			{
				lines.add(part(item, Kind.ALCHED, item.getAlched()));
			}
			if (item.getLeft() > 0)
			{
				lines.add(part(item, Kind.LEFT, item.getLeft()));
			}
			return lines;
		}

		private static Line part(LootItem item, Kind kind, long quantity)
		{
			Line line = new Line(item.getId(), item.getName(), kind);
			line.quantity = quantity;
			line.alchPrice = item.getAlchPrice();
			line.value = (kind == Kind.ALCHED ? item.getAlchPrice() : item.getPrice()) * quantity;
			return line;
		}

		private long key()
		{
			return (long) id << 2 | kind.ordinal();
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

		public Kind getKind()
		{
			return kind;
		}

		/**
		 * Whether this is the part that was dropped but never picked up.
		 */
		public boolean isLeft()
		{
			return kind == Kind.LEFT;
		}

		public boolean isAlched()
		{
			return kind == Kind.ALCHED;
		}

		/**
		 * Gold one alch gave, for alched lines.
		 */
		public long getAlchPrice()
		{
			return alchPrice;
		}

		/**
		 * GE value at the prices each drop had when it was looted, or for alched lines the gold the alchs gave.
		 */
		public long getValue()
		{
			return value;
		}
	}

	public static final class Group
	{
		private final String title;
		private String taskName;
		private String detail;
		private int drops;
		private long oldest = Long.MAX_VALUE;
		private long newest = Long.MIN_VALUE;
		private long totalValue;
		private final Map<Long, Line> lines = new LinkedHashMap<>();
		private final Map<String, Integer> others = new LinkedHashMap<>();
		private final Map<String, Integer> sources = new LinkedHashMap<>();
		private final Map<String, LootSourceType> sourceTypes = new LinkedHashMap<>();
		private final List<LootRecord> records = new ArrayList<>();

		Group(String title)
		{
			this.title = title;
		}

		public String getTitle()
		{
			return title;
		}

		/**
		 * The task's name, in the slayer task view.
		 */
		public String getTaskName()
		{
			return taskName;
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
		 * Timestamp of the first drop in the group.
		 */
		public long getOldest()
		{
			return oldest;
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
		 * The drops in the group, in the order they were grouped.
		 */
		public List<LootRecord> getRecords()
		{
			return Collections.unmodifiableList(records);
		}

		/**
		 * Kept items first, most valuable first, then what was left on the floor.
		 */
		public List<Line> getLines()
		{
			List<Line> sorted = new ArrayList<>(lines.values());
			sorted.sort(Line.ORDER);
			return sorted;
		}
	}

	/**
	 * @param records any order
	 * @param mode    anything but {@link Mode#DEFAULT}, which shows records as they are
	 * @param trips   where trips ended, for {@link Mode#TRIP}
	 * @return groups with the most recent drop first. The slayer task view leaves out drops that weren't for a task.
	 */
	public static List<Group> group(List<LootRecord> records, Mode mode, Interval interval, TripLog trips, ZoneId zone)
	{
		return group(records, mode, interval, null, trips, zone);
	}

	/**
	 * @param range the days of a {@link Interval#CUSTOM} range; drops outside it are left out
	 */
	public static List<Group> group(List<LootRecord> records, Mode mode, Interval interval, DateRange range, TripLog trips, ZoneId zone)
	{
		boolean inRange = mode == Mode.TIME && interval == Interval.CUSTOM;
		if (inRange && range == null)
		{
			throw new IllegalArgumentException("A custom range needs its dates");
		}
		Function<LootRecord, String> key;
		Function<LootRecord, String> other;
		String joiner;
		switch (mode)
		{
			case SOURCE:
				key = LootRecord::getSource;
				other = LootGroups::place;
				joiner = " at ";
				break;
			case TIME:
				key = inRange ? r -> CUSTOM_RANGE : r -> period(r.getTimestamp(), interval, zone);
				other = LootRecord::getSource;
				joiner = " from ";
				break;
			case TRIP:
				key = r -> String.valueOf(trips.tripEnd(r.getTimestamp()));
				other = LootRecord::getSource;
				joiner = " from ";
				break;
			case TASK:
				// Each assignment gets its own box, even two of the same monster in a row
				key = r -> String.valueOf(r.getTask().getStart());
				other = LootRecord::getSource;
				joiner = " from ";
				break;
			default:
				throw new IllegalArgumentException("Nothing to group by in " + mode);
		}

		Map<String, Group> groups = new LinkedHashMap<>();
		for (LootRecord record : records)
		{
			if (mode == Mode.TASK && record.getTask() == null)
			{
				// Only kills that counted for a task
				continue;
			}
			if (inRange && !range.contains(record.getTimestamp(), zone))
			{
				continue;
			}
			Group group = groups.computeIfAbsent(key.apply(record), Group::new);
			if (mode == Mode.TASK)
			{
				group.taskName = record.getTask().getName();
			}
			group.drops++;
			group.records.add(record);
			group.oldest = Math.min(group.oldest, record.getTimestamp());
			group.newest = Math.max(group.newest, record.getTimestamp());
			group.others.merge(other.apply(record), 1, Integer::sum);
			group.sources.merge(record.getSource(), 1, Integer::sum);
			group.sourceTypes.putIfAbsent(record.getSource(), record.getType());
			for (LootItem item : record.getItems())
			{
				for (Line part : Line.of(item))
				{
					Line line = group.lines.computeIfAbsent(part.key(), k -> new Line(part.id, part.name, part.kind));
					line.quantity += part.quantity;
					line.value += part.value;
					line.alchPrice = part.alchPrice;
					if (!part.isLeft())
					{
						group.totalValue += part.value;
					}
				}
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
	 * The start of the stretch of time the timestamp falls in, like 2026-10-22T14 for an hour or 2026-10-19
	 * for the week starting that Monday. Time range groups are titled with this until {@link #periodTitle}
	 * names them.
	 */
	static String period(long timestamp, Interval interval, ZoneId zone)
	{
		ZonedDateTime time = Instant.ofEpochMilli(timestamp).atZone(zone);
		LocalDate date = time.toLocalDate();
		switch (interval)
		{
			case HOUR:
				return date + "T" + String.format(Locale.ENGLISH, "%02d", time.getHour());
			case WEEK:
				return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
			case MONTH:
				return date.withDayOfMonth(1).toString();
			case YEAR:
				return date.withDayOfYear(1).toString();
			default:
				return date.toString();
		}
	}

	/**
	 * A readable name for a period key: "Today", "Yesterday 14:00", "Last week", "September" and so on.
	 * The year is added when it isn't this year.
	 */
	public static String periodTitle(String period, Interval interval, long now, ZoneId zone)
	{
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		switch (interval)
		{
			case HOUR:
			{
				int t = period.indexOf('T');
				LocalDate date = LocalDate.parse(period.substring(0, t));
				return dayAndTime(date, period.substring(t + 1) + ":00", today);
			}
			case WEEK:
			{
				LocalDate start = LocalDate.parse(period);
				LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				if (start.equals(thisWeek))
				{
					return "This week";
				}
				if (start.equals(thisWeek.minusWeeks(1)))
				{
					return "Last week";
				}
				return "Week of " + withYear(DAY_MONTH_FORMAT.format(start), start, today);
			}
			case MONTH:
			{
				LocalDate start = LocalDate.parse(period);
				LocalDate thisMonth = today.withDayOfMonth(1);
				if (start.equals(thisMonth))
				{
					return "This month";
				}
				if (start.equals(thisMonth.minusMonths(1)))
				{
					return "Last month";
				}
				return withYear(MONTH_FORMAT.format(start), start, today);
			}
			case YEAR:
			{
				int year = LocalDate.parse(period).getYear();
				if (year == today.getYear())
				{
					return "This year";
				}
				return year == today.getYear() - 1 ? "Last year" : String.valueOf(year);
			}
			default:
			{
				LocalDate date = LocalDate.parse(period);
				if (date.equals(today))
				{
					return "Today";
				}
				if (date.equals(today.minusDays(1)))
				{
					return "Yesterday";
				}
				return withYear(DAY_FORMAT.format(date), date, today);
			}
		}
	}

	/**
	 * A trip's title: "Current trip" for the one still going, or when it started, like "Today 17:05".
	 */
	public static String tripTitle(Group group, long now, ZoneId zone)
	{
		if (CURRENT_TRIP.equals(group.getTitle()))
		{
			return "Current trip";
		}
		ZonedDateTime start = Instant.ofEpochMilli(group.getOldest()).atZone(zone);
		LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
		return dayAndTime(start.toLocalDate(), CLOCK_FORMAT.format(start), today);
	}

	private static String dayAndTime(LocalDate date, String time, LocalDate today)
	{
		if (date.equals(today))
		{
			return "Today " + time;
		}
		if (date.equals(today.minusDays(1)))
		{
			return "Yesterday " + time;
		}
		return withYear(SHORT_DAY_FORMAT.format(date), date, today) + " " + time;
	}

	private static String withYear(String title, LocalDate date, LocalDate today)
	{
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
