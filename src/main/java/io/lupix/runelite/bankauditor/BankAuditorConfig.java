package io.lupix.runelite.bankauditor;

import io.lupix.runelite.bankauditor.sell.SellMode;
import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BankAuditorConfig.GROUP)
public interface BankAuditorConfig extends Config
{
	String GROUP = "bankauditor";

	@ConfigSection(
		name = "Loot tracking",
		description = "What gets recorded and for how long",
		position = 0
	)
	String lootSection = "loot";

	@ConfigSection(
		name = "Bank",
		description = "What the plugin shows while the bank is open",
		position = 1
	)
	String bankSection = "bank";

	@ConfigSection(
		name = "Selling",
		description = "Which items count as sellable and how they are highlighted",
		position = 2
	)
	String sellSection = "sell";

	@ConfigSection(
		name = "Loot colors",
		description = "Color loot in the side panel by how much a stack is worth",
		position = 3,
		closedByDefault = true
	)
	String colorSection = "colors";

	@ConfigItem(
		keyName = "trackLoot",
		name = "Track loot",
		description = "Record loot drops and work out how much of each bank stack came from loot",
		section = lootSection,
		position = 0
	)
	default boolean trackLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "useLootTracker",
		name = "Use Loot Tracker",
		description = "Read loot from RuneLite's Loot Tracker plugin instead of finding it here."
			+ " Only works while the Loot Tracker is turned on; otherwise the plugin's own tracking is used.",
		section = lootSection,
		position = 1
	)
	default boolean useLootTracker()
	{
		return false;
	}

	@ConfigItem(
		keyName = "historyDays",
		name = "Keep history for",
		description = "Loot records older than this are removed",
		section = lootSection,
		position = 2
	)
	@Range(min = 1, max = 365)
	@Units(" days")
	default int historyDays()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "maxRecords",
		name = "Max loot records",
		description = "The oldest records are removed past this many",
		section = lootSection,
		position = 3
	)
	@Range(min = 50, max = 1000)
	default int maxRecords()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "showPanel",
		name = "Side panel",
		description = "Show the Bank Auditor side panel with your loot history and sell list",
		section = lootSection,
		position = 4
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bestValueOnly",
		name = "Best value only",
		description = "In the side panel, show only the higher of the GE and high alch value for each item."
			+ " Hover it to see both.",
		section = lootSection,
		position = 5
	)
	default boolean bestValueOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTooltips",
		name = "Loot tooltips",
		description = "Hover an item in the bank to see how much of it came from loot, from what, where and when",
		section = bankSection,
		position = 0
	)
	default boolean showTooltips()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLootedAmount",
		name = "Show looted amount",
		description = "Small number in the corner of bank items saying how much of the stack came from loot",
		section = bankSection,
		position = 1
	)
	default boolean showLootedAmount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lootedAmountColor",
		name = "Looted amount color",
		description = "Color of the looted amount on bank items",
		section = bankSection,
		position = 2
	)
	default Color lootedAmountColor()
	{
		return new Color(0x5F, 0xD8, 0x5F);
	}

	@ConfigItem(
		keyName = "highlightSellableInBank",
		name = "Highlight sellable in bank",
		description = "Mark sellable items in the bank",
		section = bankSection,
		position = 3
	)
	default boolean highlightSellableInBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSellableInInventory",
		name = "Highlight sellable in inventory",
		description = "Mark sellable items in your inventory while the bank is open",
		section = bankSection,
		position = 4
	)
	default boolean highlightSellableInInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sellableTag",
		name = "Sellable bank tag",
		description = "Adds a 'sellable' bank tag you can search for with tag:sellable, showing only items you can sell."
			+ " Right-click the worn items button in the bank to open it.",
		section = bankSection,
		position = 5
	)
	default boolean sellableTag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sellMode",
		name = "Sellable items",
		description = "Only count looted items as sellable, or every tradeable item you own",
		section = sellSection,
		position = 0
	)
	default SellMode sellMode()
	{
		return SellMode.LOOT_ONLY;
	}

	@ConfigItem(
		keyName = "minimumValue",
		name = "Minimum stack value",
		description = "Ignore sellable stacks worth less than this on the Grand Exchange (0 lists everything)",
		section = sellSection,
		position = 1
	)
	@Units(" gp")
	@Range(min = 0)
	default int minimumValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "keepList",
		name = "Keep list",
		description = "Items you use and never want marked as sellable. One per line or comma separated."
			+ " Use * as a wildcard (* rune) and :amount to keep only that many (Shark:200, Death rune:5k).",
		section = sellSection,
		position = 2
	)
	default String keepList()
	{
		return "";
	}

	@ConfigItem(
		keyName = "keepList",
		name = "",
		description = ""
	)
	void setKeepList(String keepList);

	@ConfigItem(
		keyName = "lootGrouping",
		name = "",
		description = "",
		hidden = true
	)
	default String lootGrouping()
	{
		return "DEFAULT";
	}

	@ConfigItem(
		keyName = "lootGrouping",
		name = "",
		description = ""
	)
	void setLootGrouping(String mode);

	@ConfigItem(
		keyName = "lootTimeRange",
		name = "",
		description = "",
		hidden = true
	)
	default String lootTimeRange()
	{
		return "DAY";
	}

	@ConfigItem(
		keyName = "lootTimeRange",
		name = "",
		description = ""
	)
	void setLootTimeRange(String interval);

	@ConfigItem(
		keyName = "keepMenuOption",
		name = "Keep menu option",
		description = "Right-click option on bank and inventory items to add or remove them from the keep list",
		section = sellSection,
		position = 3
	)
	default boolean keepMenuOption()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightAtGrandExchange",
		name = "Highlight at Grand Exchange",
		description = "Mark sellable items in your inventory while the Grand Exchange is open",
		section = sellSection,
		position = 4
	)
	default boolean highlightAtGrandExchange()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightStyle",
		name = "Highlight style",
		description = "How sellable items are marked",
		section = sellSection,
		position = 5
	)
	default HighlightStyle highlightStyle()
	{
		return HighlightStyle.UNDERLINE;
	}

	@Alpha
	@ConfigItem(
		keyName = "sellableColor",
		name = "Sellable color",
		description = "Color used to mark sellable items",
		section = sellSection,
		position = 6
	)
	default Color sellableColor()
	{
		return new Color(255, 200, 0, 220);
	}

	@ConfigItem(
		keyName = "valueColors",
		name = "Color by value",
		description = "Color item lines and totals in the side panel when their value reaches a tier below",
		section = colorSection,
		position = 0
	)
	default boolean valueColors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowValue",
		name = "Low value",
		description = "Stacks worth at least this much get the low value color. 0 turns this tier off.",
		section = colorSection,
		position = 1
	)
	@Range(min = 0)
	@Units(" gp")
	default int lowValue()
	{
		return 20_000;
	}

	@ConfigItem(
		keyName = "lowValueColor",
		name = "Low value color",
		description = "Color for stacks that reach the low value",
		section = colorSection,
		position = 2
	)
	default Color lowValueColor()
	{
		return new Color(0x66, 0xB2, 0xFF);
	}

	@ConfigItem(
		keyName = "mediumValue",
		name = "Medium value",
		description = "Stacks worth at least this much get the medium value color. 0 turns this tier off.",
		section = colorSection,
		position = 3
	)
	@Range(min = 0)
	@Units(" gp")
	default int mediumValue()
	{
		return 100_000;
	}

	@ConfigItem(
		keyName = "mediumValueColor",
		name = "Medium value color",
		description = "Color for stacks that reach the medium value",
		section = colorSection,
		position = 4
	)
	default Color mediumValueColor()
	{
		return new Color(0x99, 0xFF, 0x99);
	}

	@ConfigItem(
		keyName = "highValue",
		name = "High value",
		description = "Stacks worth at least this much get the high value color. 0 turns this tier off.",
		section = colorSection,
		position = 5
	)
	@Range(min = 0)
	@Units(" gp")
	default int highValue()
	{
		return 1_000_000;
	}

	@ConfigItem(
		keyName = "highValueColor",
		name = "High value color",
		description = "Color for stacks that reach the high value",
		section = colorSection,
		position = 6
	)
	default Color highValueColor()
	{
		return new Color(0xFF, 0x96, 0x00);
	}

	@ConfigItem(
		keyName = "insaneValue",
		name = "Insane value",
		description = "Stacks worth at least this much get the insane value color. 0 turns this tier off.",
		section = colorSection,
		position = 7
	)
	@Range(min = 0)
	@Units(" gp")
	default int insaneValue()
	{
		return 10_000_000;
	}

	@ConfigItem(
		keyName = "insaneValueColor",
		name = "Insane value color",
		description = "Color for stacks that reach the insane value",
		section = colorSection,
		position = 8
	)
	default Color insaneValueColor()
	{
		return new Color(0xFF, 0x66, 0xB2);
	}
}
