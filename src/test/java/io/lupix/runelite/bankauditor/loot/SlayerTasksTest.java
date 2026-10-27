package io.lupix.runelite.bankauditor.loot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlayerTasksTest
{
	private static boolean counts(String task, String monster)
	{
		return SlayerTasks.counts(SlayerTasks.targets(task), monster);
	}

	@Test
	public void monstersNamedAfterTheTask()
	{
		assertTrue(counts("Fire giants", "Fire giant"));
		assertTrue(counts("Fire giants", "fire giant"));
		assertTrue(counts("Dagannoth", "Dagannoth Rex"));
		assertFalse(counts("Fire giants", "Ice giant"));
		// Only whole words, so a Cow task isn't a Cowardly guard task
		assertFalse(counts("Cows", "Cowardly guard"));
		assertFalse(counts("Cows", null));
	}

	@Test
	public void monstersFromTheTaskList()
	{
		assertTrue(counts("Birds", "Chicken"));
		assertTrue(counts("Lizardmen", "Lizardman shaman"));
		assertTrue(counts("Black demons", "Demonic gorilla"));
		assertTrue(counts("Blue dragons", "Vorkath"));
		// Spaces the game writes as non-breaking still match
		assertTrue(counts("Abyssal demons", "Greater abyssal demon"));
	}

	@Test
	public void unknownTasksStillMatchTheirName()
	{
		assertTrue(counts("Frost dragons", "Frost dragon"));
		assertEquals("Frost dragons", SlayerTasks.displayName("Frost dragons"));
		assertEquals("Abyssal demons", SlayerTasks.displayName("ABYSSAL DEMONS"));
	}
}
