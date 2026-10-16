package io.lupix.runelite.bankauditor.loot;

import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class LootLocationTest
{
	private static LootRecord at(String source, LootLocation location)
	{
		return new LootRecord(0, source, LootSourceType.NPC, 2, location, Collections.emptyList());
	}

	@Test
	public void namedAreas()
	{
		assertEquals("Lumbridge", RegionNames.of(12850));
		assertEquals("Lumbridge, world 301", new LootLocation(3222, 3218, 0, 12850, 301).describe());
		assertNull(RegionNames.of(1));
		assertEquals("region 1", new LootLocation(64, 64, 0, 1, 0).describe());
	}

	@Test
	public void tileForm()
	{
		assertEquals("Tile 3222, 3218, 0 in region 12850", new LootLocation(3222, 3218, 0, 12850, 301).describeWithTile());
	}

	@Test
	public void areaNamedAfterTheSourceIsNotRepeated()
	{
		LootLocation vorkath = new LootLocation(2272, 4065, 0, 9023, 302);

		assertEquals("world 302", at("Vorkath", vorkath).describeLocation());
		assertEquals("Vorkath, world 302", at("Zombified spawn", vorkath).describeLocation());
		assertNull(at("Goblin", null).describeLocation());
	}
}
