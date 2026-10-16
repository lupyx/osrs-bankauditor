package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootAttribution;
import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.sell.KeepList;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class TooltipTextTest
{
	private static final long HOUR = 3_600_000L;
	private static final long NOW = 1000 * HOUR;
	private static final int RUNE = 560;

	// Region ids: 9007 is Zulrah's shrine, 9023 is Vorkath's island, 1 has no name
	private static LootRecord record(long hoursAgo, String source, int level, int region, long quantity)
	{
		return new LootRecord(NOW - hoursAgo * HOUR, source, LootSourceType.NPC, level, new LootLocation(1, 2, 0, region, 302),
			Collections.singletonList(new LootItem(RUNE, "Death rune", quantity, 200)));
	}

	@Test
	public void nothingToSay()
	{
		ItemSummary plain = new ItemSummary(RUNE, "Death rune", true, 5000, 0, 0, 200, null);

		assertNull(TooltipText.build(plain, null, NOW, 3));
	}

	@Test
	public void lootedStackWithSources()
	{
		ItemSummary item = new ItemSummary(RUNE, "Death rune", true, 6200, 1200, 1200, 200, null);
		LootAttribution attribution = LootAttribution.of(RUNE, 1200, Arrays.asList(
			record(2, "Snakeling", 90, 9007, 300),
			record(30, "Vorkath", 732, 9023, 500),
			record(50, "Kraken", 291, 1, 100),
			record(60, "Goblin", 2, 1, 100)));

		String text = TooltipText.build(item, attribution, NOW, 3);

		assertEquals("<col=ff9040>Death rune</col>"
			+ "</br>From loot: 1,200 of 6,200"
			+ "</br><col=a0a0a0>300 from Snakeling (lvl 90), 2 h ago, Zulrah, world 302</col>"
			+ "</br><col=a0a0a0>500 from Vorkath (lvl 732), 1 day ago, world 302</col>"
			+ "</br><col=a0a0a0>100 from Kraken (lvl 291), 2 days ago, region 1, world 302</col>"
			+ "</br><col=a0a0a0>+300 from older loot</col>"
			+ "</br><col=ffc800>Sellable: 1,200 (240K gp)</col>", text);
	}

	@Test
	public void wholeStackLootedSkipsTheTotal()
	{
		ItemSummary item = new ItemSummary(4151, "Abyssal whip", true, 1, 1, 1, 0, null);

		assertEquals("<col=ff9040>Abyssal whip</col></br>From loot: 1"
				+ "</br><col=a0a0a0>+1 from older loot</col>"
				+ "</br><col=ffc800>Sellable: 1</col>",
			TooltipText.build(item, LootAttribution.of(4151, 1, Collections.emptyList()), NOW, 3));
	}

	@Test
	public void keptItems()
	{
		ItemSummary keepAll = new ItemSummary(385, "Shark", true, 100, 20, 0, 900, KeepList.parse("Shark").match("Shark"));
		ItemSummary keepSome = new ItemSummary(385, "Shark", true, 300, 0, 0, 900, KeepList.parse("Shark:200").match("Shark"));

		assertEquals("<col=ff9040>Shark</col></br>From loot: 20 of 100</br><col=a0a0a0>On your keep list</col>",
			TooltipText.build(keepAll, null, NOW, 3));
		assertEquals("<col=ff9040>Shark</col></br><col=a0a0a0>Keeping 200</col>",
			TooltipText.build(keepSome, null, NOW, 3));
	}
}
