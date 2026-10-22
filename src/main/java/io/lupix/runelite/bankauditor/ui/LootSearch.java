package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
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
	 * @return the matching records in the same order. A drop that matches only by item keeps just the
	 * matching items, so totals in the grouped views add up what was searched for.
	 */
	public static List<LootRecord> filter(List<LootRecord> records, String query, boolean source, boolean location, boolean item)
	{
		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ENGLISH);
		if (needle.isEmpty())
		{
			return records;
		}

		List<LootRecord> result = new ArrayList<>();
		for (LootRecord record : records)
		{
			if ((source && contains(record.getSource(), needle))
				|| (location && contains(LootGroups.place(record), needle)))
			{
				result.add(record);
				continue;
			}
			if (!item)
			{
				continue;
			}
			List<LootItem> items = new ArrayList<>();
			for (LootItem loot : record.getItems())
			{
				if (contains(loot.getName(), needle))
				{
					items.add(loot);
				}
			}
			if (items.size() == record.getItems().size())
			{
				result.add(record);
			}
			else if (!items.isEmpty())
			{
				result.add(new LootRecord(record.getTimestamp(), record.getSource(), record.getType(), record.getCombatLevel(),
					record.getLocation(), items));
			}
		}
		return result;
	}

	private static boolean contains(String text, String needle)
	{
		return text != null && text.toLowerCase(Locale.ENGLISH).contains(needle);
	}
}
