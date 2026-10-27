package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SourceIconsTest
{
	private static final int NPC = 1;
	private static final int THIEVING = 2;
	private static final int SLAYER = 3;

	private static SourceIcons icons()
	{
		Map<String, Integer> bosses = new HashMap<>();
		bosses.put("Vorkath", 100);
		bosses.put("Barrows Chests", 101);
		bosses.put("Grotesque Guardians", 102);
		bosses.put("Alchemical Hydra", 103);
		return new SourceIcons(bosses, NPC, THIEVING, SLAYER);
	}

	@Test
	public void bossesByNameOrAlias()
	{
		assertEquals(100, icons().spriteFor("Vorkath", LootSourceType.NPC));
		assertEquals(100, icons().spriteFor("vorkath ", LootSourceType.NPC));
		assertEquals(101, icons().spriteFor("Barrows", LootSourceType.EVENT));
		assertEquals(102, icons().spriteFor("Dusk", LootSourceType.NPC));
	}

	@Test
	public void everythingElseByKind()
	{
		assertEquals(NPC, icons().spriteFor("Goblin", LootSourceType.NPC));
		assertEquals(NPC, icons().spriteFor(null, LootSourceType.OTHER));
		assertEquals(THIEVING, icons().spriteFor("Master Farmer", LootSourceType.PICKPOCKET));
		// No boss sprite for this alias's target, so it falls back
		assertEquals(NPC, icons().spriteFor("Gauntlet", LootSourceType.EVENT));
	}

	@Test
	public void tasksUseTheBossIconOrTheSlayerIcon()
	{
		assertEquals(100, icons().taskSprite("Vorkath"));
		assertEquals(103, icons().taskSprite("The Alchemical Hydra"));
		assertEquals(SLAYER, icons().taskSprite("Abyssal demons"));
		assertEquals(SLAYER, icons().taskSprite(null));
	}
}
