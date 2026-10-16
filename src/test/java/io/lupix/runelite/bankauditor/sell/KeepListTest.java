package io.lupix.runelite.bankauditor.sell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class KeepListTest
{
	@Test
	public void emptyTextKeepsNothing()
	{
		assertTrue(KeepList.parse(null).isEmpty());
		assertTrue(KeepList.parse("  \n , ").isEmpty());
		assertNull(KeepList.parse("").match("Shark"));
	}

	@Test
	public void matchesNamesIgnoringCaseAndSpacing()
	{
		KeepList list = KeepList.parse("shark,  Prayer   potion(4)\nRanarr weed");

		assertTrue(list.match("Shark").keepsAll());
		assertTrue(list.match("Prayer potion(4)").keepsAll());
		assertTrue(list.match("RANARR WEED").keepsAll());
		assertNull(list.match("Sharks"));
		assertNull(list.match(null));
	}

	@Test
	public void parsesKeepAmounts()
	{
		KeepList list = KeepList.parse("Shark:200, Death rune : 5k, Coal:1.5m, Gold bar:2b");

		assertEquals(200, list.match("shark").getAmount());
		assertEquals(5_000, list.match("Death rune").getAmount());
		assertEquals(1_500_000, list.match("Coal").getAmount());
		assertEquals(2_000_000_000L, list.match("Gold bar").getAmount());
		assertFalse(list.match("shark").keepsAll());
	}

	@Test
	public void colonWithoutNumberIsPartOfTheName()
	{
		KeepList list = KeepList.parse("Weird: name");

		assertTrue(list.match("weird: name").keepsAll());
	}

	@Test
	public void wildcards()
	{
		KeepList list = KeepList.parse("* rune:1000, Super *, *(4)");

		assertEquals(1000, list.match("Death rune").getAmount());
		assertEquals(1000, list.match("BLOOD RUNE").getAmount());
		assertNull(list.match("Rune platebody"));
		assertTrue(list.match("Super restore(4)").isWildcard());
		assertTrue(list.match("Prayer potion(4)").keepsAll());
		assertNull(list.match("Prayer potion(3)"));
	}

	@Test
	public void wildcardCharactersAreLiteralOtherwise()
	{
		KeepList list = KeepList.parse("Saradomin brew(*)");

		assertTrue(list.match("Saradomin brew(3)").keepsAll());
		assertNull(list.match("Saradomin brewX3)"));
	}

	@Test
	public void plainNameBeatsWildcard()
	{
		KeepList list = KeepList.parse("* rune, Death rune:500");

		assertEquals(500, list.match("Death rune").getAmount());
		assertTrue(list.match("Blood rune").keepsAll());
		assertTrue(list.containsExactly("death rune"));
		assertFalse(list.containsExactly("Blood rune"));
	}

	@Test
	public void addAppendsInTheUsersStyle()
	{
		assertEquals("Shark", KeepList.add("", "Shark"));
		assertEquals("Shark", KeepList.add(null, "Shark"));
		assertEquals("Shark, Lobster", KeepList.add("Shark", "Lobster"));
		assertEquals("Shark, Lobster", KeepList.add("Shark, ", "Lobster"));
		assertEquals("Shark\nLobster", KeepList.add("Shark\n", "Lobster"));
		assertEquals("Shark\nKarambwan\nLobster", KeepList.add("Shark\nKarambwan", "Lobster"));
	}

	@Test
	public void addDoesNothingWhenAlreadyListedByName()
	{
		assertEquals("shark:200", KeepList.add("shark:200", "Shark"));
	}

	@Test
	public void addListsAnItemThatOnlyMatchedAWildcard()
	{
		assertEquals("* rune, Death rune", KeepList.add("* rune", "Death rune"));
	}

	@Test
	public void removeDropsOnlyExactEntries()
	{
		assertEquals("Lobster", KeepList.remove("Shark, Lobster", "shark"));
		assertEquals("Lobster", KeepList.remove("Shark:200, Lobster", "Shark"));
		assertEquals("* rune\nLobster", KeepList.remove("* rune\nDeath rune\nLobster", "Death rune"));
		assertEquals("", KeepList.remove("Shark", "Shark"));
		assertEquals("", KeepList.remove(null, "Shark"));
	}
}
