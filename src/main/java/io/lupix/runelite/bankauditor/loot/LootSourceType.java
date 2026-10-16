package io.lupix.runelite.bankauditor.loot;

/**
 * Where a loot record came from. Mirrors the loot tracker's record types, with
 * {@link #OTHER} standing in for anything we don't recognise.
 */
public enum LootSourceType
{
	NPC,
	PLAYER,
	EVENT,
	PICKPOCKET,
	OTHER;

	/**
	 * Maps a loot tracker type name onto ours. The loot tracker enum lives in a separate
	 * artifact and may grow new constants, so this goes by name rather than by switch.
	 */
	public static LootSourceType fromName(String name)
	{
		if (name == null)
		{
			return OTHER;
		}

		for (LootSourceType type : values())
		{
			if (type.name().equals(name))
			{
				return type;
			}
		}
		return OTHER;
	}
}
