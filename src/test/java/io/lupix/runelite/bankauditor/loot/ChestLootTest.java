package io.lupix.runelite.bankauditor.loot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ChestLootTest
{
	private static final int BONES = 532;
	private static final int RUNES = 560;

	private static Map<Integer, Long> items(long bones, long runes)
	{
		Map<Integer, Long> items = new HashMap<>();
		if (bones > 0)
		{
			items.put(BONES, bones);
		}
		if (runes > 0)
		{
			items.put(RUNES, runes);
		}
		return items;
	}

	@Test
	public void lookingAgainCountsNothingNew()
	{
		ChestLoot chest = new ChestLoot();
		assertEquals(items(30, 100), chest.opened(items(30, 100)));
		assertEquals(Collections.emptyMap(), chest.opened(items(30, 100)));
	}

	@Test
	public void onlyTheNewPartOfARefilledChestCounts()
	{
		ChestLoot chest = new ChestLoot();
		chest.opened(items(30, 100));
		// More loot lands in the chest before the window opens again
		chest.contentsChanged(items(50, 100));
		assertEquals(items(20, 0), chest.opened(items(50, 100)));
	}

	@Test
	public void takingItemsOutForgetsThem()
	{
		ChestLoot chest = new ChestLoot();
		chest.opened(items(30, 100));
		// Runes taken, bones left behind (a full inventory, say)
		chest.contentsChanged(items(30, 0));
		chest.contentsChanged(items(30, 0));
		assertEquals(items(0, 100), chest.opened(items(30, 100)));

		// Everything banked, then the same loot again from the next moon
		chest.contentsChanged(Collections.emptyMap());
		assertEquals(items(30, 100), chest.opened(items(30, 100)));
	}
}
