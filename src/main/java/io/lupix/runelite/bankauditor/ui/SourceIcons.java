package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the sprite shown next to a loot source: the boss icon from the hiscores when there is one,
 * otherwise a skill icon for the kind of loot.
 */
public final class SourceIcons
{
	/**
	 * Loot names that differ from the boss's hiscore name.
	 */
	private static final String[][] ALIASES = {
		{"Barrows", "Barrows Chests"},
		{"Lunar Chest", "Lunar Chests"},
		{"Dusk", "Grotesque Guardians"},
		{"Dawn", "Grotesque Guardians"},
		{"Corrupted Gauntlet", "The Corrupted Gauntlet"},
		{"Gauntlet", "The Gauntlet"},
		{"Chambers of Xeric Challenge Mode", "Chambers of Xeric: Challenge Mode"},
		{"Theatre of Blood Hard Mode", "Theatre of Blood: Hard Mode"},
		{"Tombs of Amascut Expert Mode", "Tombs of Amascut: Expert Mode"},
		{"The Nightmare", "Nightmare"},
		{"Hueycoatl", "The Hueycoatl"},
		{"Leviathan", "The Leviathan"},
		{"Whisperer", "The Whisperer"},
		{"Royal Titans", "The Royal Titans"},
		{"Branda the Fire Queen", "The Royal Titans"},
		{"Eldric the Ice King", "The Royal Titans"},
		{"Fortis Colosseum", "Sol Heredit"},
		{"Reward pool (Tempoross)", "Tempoross"},
		{"Supply crate (Wintertodt)", "Wintertodt"},
	};

	private final Map<String, Integer> byName = new HashMap<>();
	private final int npcSprite;
	private final int pickpocketSprite;

	/**
	 * @param bossSprites      boss name to sprite id, from the hiscores
	 * @param npcSprite        for any other NPC, player or event
	 * @param pickpocketSprite for pickpocketing
	 */
	public SourceIcons(Map<String, Integer> bossSprites, int npcSprite, int pickpocketSprite)
	{
		for (Map.Entry<String, Integer> boss : bossSprites.entrySet())
		{
			byName.put(key(boss.getKey()), boss.getValue());
		}
		for (String[] alias : ALIASES)
		{
			Integer sprite = byName.get(key(alias[1]));
			if (sprite != null)
			{
				byName.putIfAbsent(key(alias[0]), sprite);
			}
		}
		this.npcSprite = npcSprite;
		this.pickpocketSprite = pickpocketSprite;
	}

	public int spriteFor(String source, LootSourceType type)
	{
		Integer boss = source == null ? null : byName.get(key(source));
		if (boss != null)
		{
			return boss;
		}
		return type == LootSourceType.PICKPOCKET ? pickpocketSprite : npcSprite;
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ENGLISH);
	}
}
