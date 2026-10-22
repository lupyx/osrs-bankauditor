package io.lupix.runelite.bankauditor.loot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Loot Tracker reports the Lunar Chest every time its window opens with something in it. Loot left in the
 * chest and looked at again would then be counted twice, so this remembers what was already counted and is
 * still in the chest. Taking items out, to the inventory or straight to the bank, makes it forget them.
 */
public final class ChestLoot
{
	private final Map<Integer, Long> counted = new HashMap<>();

	/**
	 * The chest's contents changed. New loot arrives before the window opens, so only what went down matters.
	 */
	public void contentsChanged(Map<Integer, Long> contents)
	{
		counted.replaceAll((id, quantity) -> Math.min(quantity, contents.getOrDefault(id, 0L)));
		counted.values().removeIf(quantity -> quantity <= 0);
	}

	/**
	 * The chest's window opened showing these items.
	 *
	 * @return the part that wasn't counted before
	 */
	public Map<Integer, Long> opened(Map<Integer, Long> items)
	{
		Map<Integer, Long> fresh = new LinkedHashMap<>();
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			long more = e.getValue() - counted.getOrDefault(e.getKey(), 0L);
			if (more > 0)
			{
				fresh.put(e.getKey(), more);
			}
		}
		counted.clear();
		items.forEach((id, quantity) ->
		{
			if (quantity > 0)
			{
				counted.put(id, quantity);
			}
		});
		return fresh;
	}

	public void clear()
	{
		counted.clear();
	}
}
