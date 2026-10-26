package io.lupix.runelite.bankauditor.loot;

import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class NativeLootSourcesTest
{
	@Test
	public void keyedChestsAreRecognised()
	{
		assertTrue(NativeLootSources.isChestMessage("You find some treasure in the chest!"));
		assertTrue(NativeLootSources.isChestMessage("You have opened Larran's big chest 12 times."));
		assertTrue(NativeLootSources.isChestMessage("You open the chest and find some loot."));
		assertFalse(NativeLootSources.isChestMessage("You open the chest."));
		assertEquals("Brimstone Chest", NativeLootSources.CHEST_EVENT_TYPES.get(5179));
		assertEquals("Crystal Chest", NativeLootSources.CHEST_EVENT_TYPES.get(11573));
	}

	@Test
	public void clueTiersGetTheLootTrackerNames()
	{
		assertEquals("Hard", NativeLootSources.clueTier("hard"));
		assertEquals("Beginner", NativeLootSources.clueTier("Beginner"));
		assertNull(NativeLootSources.clueTier("impossible"));
		assertEquals("Medium", NativeLootSources.capitalize("mEDIUM"));
	}

	@Test
	public void inventoryChangesSplitIntoAddedAndRemoved()
	{
		Map<Integer, Integer> before = Map.of(405, 1, 995, 100, 560, 20);
		Map<Integer, Integer> after = Map.of(995, 2_100, 560, 20, 1_623, 1);

		assertEquals(Map.of(995, 2_000, 1_623, 1), NativeLootSources.difference(after, before));
		assertEquals(Map.of(405, 1), NativeLootSources.difference(before, after));
	}
}
