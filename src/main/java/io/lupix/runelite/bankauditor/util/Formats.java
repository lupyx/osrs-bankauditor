package io.lupix.runelite.bankauditor.util;

import java.util.Locale;

/**
 * Number and time formatting shared by the overlays, tooltips and panel.
 */
public final class Formats
{
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;

	private Formats()
	{
	}

	/**
	 * 1234567 becomes "1,234,567".
	 */
	public static String commas(long value)
	{
		return String.format(Locale.ENGLISH, "%,d", value);
	}

	/**
	 * Compact form for small labels: 950, 1.2K, 25K, 1.5M, 2.1B.
	 */
	public static String compact(long value)
	{
		long abs = Math.abs(value);
		if (abs < 1_000)
		{
			return Long.toString(value);
		}
		if (abs < 1_000_000)
		{
			return scaled(value, 1_000, "K");
		}
		if (abs < 1_000_000_000)
		{
			return scaled(value, 1_000_000, "M");
		}
		return scaled(value, 1_000_000_000, "B");
	}

	private static String scaled(long value, long unit, String suffix)
	{
		// One decimal below 10 units ("1.2K"), none above ("25K"). Round down so 999,999 doesn't show as "1000K".
		long tenths = value * 10 / unit;
		if (Math.abs(tenths) < 100)
		{
			long whole = tenths / 10;
			long fraction = Math.abs(tenths % 10);
			String sign = value < 0 && whole == 0 ? "-" : "";
			return fraction == 0 ? sign + whole + suffix : sign + whole + "." + fraction + suffix;
		}
		return (value / unit) + suffix;
	}

	/**
	 * How long something took: "under 1 min", "43 min", "2 h" or "1 h 5 min".
	 */
	public static String duration(long millis)
	{
		if (millis < MINUTE)
		{
			return "under 1 min";
		}
		long hours = millis / HOUR;
		long minutes = millis % HOUR / MINUTE;
		if (hours == 0)
		{
			return minutes + " min";
		}
		return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
	}

	/**
	 * Rough age of a timestamp: "just now", "5 min ago", "3 h ago", "2 days ago".
	 */
	public static String ago(long now, long then)
	{
		long elapsed = now - then;
		if (elapsed < MINUTE)
		{
			return "just now";
		}
		if (elapsed < HOUR)
		{
			return (elapsed / MINUTE) + " min ago";
		}
		if (elapsed < DAY)
		{
			return (elapsed / HOUR) + " h ago";
		}
		long days = elapsed / DAY;
		return days == 1 ? "1 day ago" : days + " days ago";
	}
}
