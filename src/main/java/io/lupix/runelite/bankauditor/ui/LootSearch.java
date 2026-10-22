package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filters loot records by text in the source name, the location name or the item names.
 */
public final class LootSearch
{
	private LootSearch()
	{
	}

	/**
	 * @param records newest first
	 * @param query   case does not matter; blank keeps everything
	 * @return the matching drops in the same order, each with all of its items
	 */
	public static List<LootRecord> filter(List<LootRecord> records, String query, boolean source, boolean location, boolean item)
	{
		String needle = normalize(query);
		if (needle.isEmpty())
		{
			return records;
		}

		List<LootRecord> result = new ArrayList<>();
		for (LootRecord record : records)
		{
			if ((source && contains(record.getSource(), needle))
				|| (location && contains(LootGroups.place(record), needle))
				|| (item && record.getItems().stream().anyMatch(i -> contains(i.getName(), needle))))
			{
				result.add(record);
			}
		}
		return result;
	}

	/**
	 * Whether the text contains the query, ignoring case. A blank query matches nothing.
	 */
	public static boolean matches(String text, String query)
	{
		String needle = normalize(query);
		return !needle.isEmpty() && contains(text, needle);
	}

	/**
	 * The text as HTML with every match of the query wrapped in a highlight, or null when nothing matches.
	 *
	 * @param color background of the highlight, like "#8a5a00"
	 */
	public static String highlight(String text, String query, String color)
	{
		String needle = normalize(query);
		if (text == null || needle.isEmpty())
		{
			return null;
		}
		String lower = text.toLowerCase(Locale.ENGLISH);
		int at = lower.indexOf(needle);
		if (at < 0)
		{
			return null;
		}
		StringBuilder sb = new StringBuilder("<html>");
		int from = 0;
		while (at >= 0)
		{
			sb.append(escape(text.substring(from, at)))
				.append("<span style='background-color:").append(color).append(";color:#ffffff'>")
				.append(escape(text.substring(at, at + needle.length())))
				.append("</span>");
			from = at + needle.length();
			at = lower.indexOf(needle, from);
		}
		return sb.append(escape(text.substring(from))).append("</html>").toString();
	}

	private static String normalize(String query)
	{
		return query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static boolean contains(String text, String needle)
	{
		return text != null && text.toLowerCase(Locale.ENGLISH).contains(needle);
	}
}
