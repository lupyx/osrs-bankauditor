/*
 * Copyright (c) 2026, lupyx
 * Based on RuneLite's Loot Tracker plugin:
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MessageNode;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.LootManager;
import net.runelite.client.plugins.loottracker.PluginLootReceived;
import net.runelite.client.util.Text;

/**
 * Finds loot that doesn't come from a kill: reward chests, raids, clue caskets, opened caskets and sacks, keyed
 * chests, skilling rewards and so on. This follows RuneLite's Loot Tracker plugin closely, so the plugin works
 * the same with the Loot Tracker turned off. Kills and pickpockets are handled by the plugin itself.
 *
 * <p>Many of these only know loot is coming (a chat message, a click on Open). For those it remembers the
 * inventory and reports what was added when it next changes, plus what landed on the floor under the player
 * when the inventory was full.
 */
public class NativeLootSources
{
	/**
	 * Gets the loot found here.
	 */
	public interface Sink
	{
		void loot(String source, LootSourceType type, Collection<ItemStack> items);
	}

	@FunctionalInterface
	private interface InvChange
	{
		void accept(List<ItemStack> added, Collection<ItemStack> ground, Map<Integer, Integer> removed);
	}

	/**
	 * Server ticks to wait for the inventory to change after loot was announced.
	 */
	private static final int INVCHANGE_TIMEOUT = 10;

	static final String LUNAR_CHEST = "Lunar Chest";
	private static final String CHAMBERS_OF_XERIC = "Chambers of Xeric";
	private static final String THEATRE_OF_BLOOD = "Theatre of Blood";
	private static final String TOMBS_OF_AMASCUT = "Tombs of Amascut";
	private static final int THEATRE_OF_BLOOD_REGION = 12867;
	private static final int THEATRE_OF_BLOOD_LOBBY = 14642;

	private static final List<Integer> PVP_LOOT_KEY_CONTAINERS = List.of(
		InventoryID.DEADMAN_LOOT_INV0,
		InventoryID.DEADMAN_LOOT_INV1,
		InventoryID.DEADMAN_LOOT_INV2,
		InventoryID.DEADMAN_LOOT_INV3,
		InventoryID.DEADMAN_LOOT_INV4);
	private static final List<Integer> PVP_LOOT_KEYS = List.of(
		ItemID.WILDY_LOOT_KEY0,
		ItemID.WILDY_LOOT_KEY1,
		ItemID.WILDY_LOOT_KEY2,
		ItemID.WILDY_LOOT_KEY3,
		ItemID.WILDY_LOOT_KEY4);

	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed [0-9]+ ([a-z]+) Treasure Trails?\\.");
	private static final String COURIER_TASK_COMPLETE_MESSAGE = "and complete your courier task!";
	private static final String HERBIBOAR_LOOTED_MESSAGE = "You harvest herbs from the herbiboar, whereupon it escapes.";
	private static final Pattern HERBIBOAR_HERB_SACK_PATTERN = Pattern.compile(".+(Grimy .+?) herb.+");
	private static final Pattern ZOMBIE_PIRATE_LOCKER_PATTERN = Pattern.compile("You loot the locker and receive <col=[\\da-f]{6}>(?<qty>[\\d,]+) x (?<item>.+)</col>\\.");
	private static final Pattern SALVAGE_PATTERN = Pattern.compile("You sort through the\\s+(?<tier>\\S+)\\s+salvage.*");
	private static final Pattern GOLEM_CRAFTING_PATTERN = Pattern.compile(
		"As you complete the golem it leaves a gift (?:on the ground|in your gem (?:bag|sack)) for you: 1 x "
			+ "(?<item>Uncut diamond|Uncut emerald|Uncut ruby|Uncut sapphire|Jeweller's chisel)\\.?$");
	private static final Map<String, Integer> GOLEM_CRAFTING_REWARDS = Map.of(
		"Uncut diamond", ItemID.UNCUT_DIAMOND,
		"Uncut emerald", ItemID.UNCUT_EMERALD,
		"Uncut ruby", ItemID.UNCUT_RUBY,
		"Uncut sapphire", ItemID.UNCUT_SAPPHIRE,
		"Jeweller's chisel", ItemID.JEWELLERS_CHISEL);

	private static final String CHEST_LOOTED_MESSAGE = "You find some treasure in the chest!";
	private static final Pattern ROGUES_CHEST_PATTERN = Pattern.compile("You find (a|some)([a-z\\s]*) inside.");
	private static final Pattern LARRAN_LOOTED_PATTERN = Pattern.compile("You have opened Larran's (big|small) chest .*");
	private static final String ALCHEMIST_SIGNET_CHEST_MESSAGE = "You take some loot from inside.";
	private static final String OTHER_CHEST_LOOTED_MESSAGE = "You steal some loot from the chest.";
	private static final String DORGESH_KAAN_CHEST_LOOTED_MESSAGE = "You find treasure inside!";
	private static final Pattern GRUBBY_CHEST_LOOTED_MESSAGE = Pattern.compile("You find treasure(?: and supplies|, supplies, and a weirdly coloured egg sac) within the chest.");
	private static final String ANCIENT_CHEST_LOOTED_MESSAGE = "You open the chest and find";
	private static final Pattern HAM_CHEST_LOOTED_PATTERN = Pattern.compile("Your (?<key>[a-z]+) key breaks in the lock.*");
	private static final int HAM_STOREROOM_REGION = 10321;
	/**
	 * Chests opened with a key or a lockpick, by the region they're in.
	 */
	static final Map<Integer, String> CHEST_EVENT_TYPES = Map.ofEntries(
		Map.entry(5179, "Brimstone Chest"),
		Map.entry(11573, "Crystal Chest"),
		Map.entry(12093, "Larran's big chest"),
		Map.entry(13113, "Larran's small chest"),
		Map.entry(13151, "Elven Crystal Chest"),
		Map.entry(5277, "Stone chest"),
		Map.entry(10835, "Dorgesh-Kaan Chest"),
		Map.entry(10834, "Dorgesh-Kaan Chest"),
		Map.entry(7323, "Grubby Chest"),
		Map.entry(8593, "Isle of Souls Chest"),
		Map.entry(7827, "Dark Chest"),
		Map.entry(13117, "Rogues' Chest"),
		Map.entry(13156, "Chest (Ancient Vault)"),
		Map.entry(12348, "Muddy Chest"),
		Map.entry(5422, "Chest (Aldarin Villas)"),
		Map.entry(6550, "Chest (Moon key)"),
		Map.entry(5521, "Chest (Alchemist's signet)"),
		Map.entry(12073, "Rusty chest"),
		Map.entry(7470, "Rusty chest"),
		Map.entry(6187, "Tarnished chest"),
		Map.entry(6953, "Tarnished chest"),
		Map.entry(7743, "Reinforced chest"),
		Map.entry(8758, "Reinforced chest"));

	private static final Pattern SHADE_CHEST_NO_KEY_PATTERN = Pattern.compile("You need a [a-z]+ key with a [a-z]+ trim to open this chest .*");
	private static final Map<Integer, String> SHADE_CHEST_OBJECTS = Map.ofEntries(
		Map.entry(ObjectID.SHADECHEST_BRONZE_BLOODRED, "Bronze key red"),
		Map.entry(ObjectID.SHADECHEST_BRONZE_BROWN, "Bronze key brown"),
		Map.entry(ObjectID.SHADECHEST_BRONZE_CRIMSON, "Bronze key crimson"),
		Map.entry(ObjectID.SHADECHEST_BRONZE_BLACK, "Bronze key black"),
		Map.entry(ObjectID.SHADECHEST_BRONZE_PURPLE, "Bronze key purple"),
		Map.entry(ObjectID.SHADECHEST_STEEL_BLOODRED, "Steel key red"),
		Map.entry(ObjectID.SHADECHEST_STEEL_BROWN, "Steel key brown"),
		Map.entry(ObjectID.SHADECHEST_STEEL_CRIMSON, "Steel key crimson"),
		Map.entry(ObjectID.SHADECHEST_STEEL_BLACK, "Steel key black"),
		Map.entry(ObjectID.SHADECHEST_STEEL_PURPLE, "Steel key purple"),
		Map.entry(ObjectID.SHADECHEST_BLACK_BLOODRED, "Black key red"),
		Map.entry(ObjectID.SHADECHEST_BLACK_BROWN, "Black key brown"),
		Map.entry(ObjectID.SHADECHEST_BLACK_CRIMSON, "Black key crimson"),
		Map.entry(ObjectID.SHADECHEST_BLACK_BLACK, "Black key black"),
		Map.entry(ObjectID.SHADECHEST_BLACK_PURPLE, "Black key purple"),
		Map.entry(ObjectID.SHADECHEST_SILVER_BLOODRED, "Silver key red"),
		Map.entry(ObjectID.SHADECHEST_SILVER_BROWN, "Silver key brown"),
		Map.entry(ObjectID.SHADECHEST_SILVER_CRIMSON, "Silver key crimson"),
		Map.entry(ObjectID.SHADECHEST_SILVER_BLACK, "Silver key black"),
		Map.entry(ObjectID.SHADECHEST_SILVER_PURPLE, "Silver key purple"),
		Map.entry(ObjectID.SHADECHEST_GOLD_BLOODRED, "Gold key red"),
		Map.entry(ObjectID.SHADECHEST_GOLD_BROWN, "Gold key brown"),
		Map.entry(ObjectID.SHADECHEST_GOLD_CRIMSON, "Gold key crimson"),
		Map.entry(ObjectID.SHADECHEST_GOLD_BLACK, "Gold key black"),
		Map.entry(ObjectID.SHADECHEST_GOLD_PURPLE, "Gold key purple"));

	private static final Set<Integer> BIRDNEST_IDS = Set.of(ItemID.BIRD_NEST_EGG_RED, ItemID.BIRD_NEST_EGG_GREEN,
		ItemID.BIRD_NEST_EGG_BLUE, ItemID.BIRD_NEST_SEEDS, ItemID.BIRD_NEST_RING, ItemID.BIRD_NEST_CHEAPSEEDS,
		ItemID.BIRD_NEST_DECENTSEEDS, ItemID.BIRD_NEST_SEEDS_JAN2019, ItemID.BIRD_NEST_DECENTSEEDS_JAN2019);
	private static final Pattern BIRDHOUSE_PATTERN = Pattern.compile("You dismantle and discard the trap, retrieving (?:(?:a|\\d{1,2}) nests?, )?10 dead birds, \\d{1,3} feathers and (\\d,?\\d{1,3}) Hunter XP\\.");
	private static final Map<Integer, String> BIRDHOUSE_XP_TO_TYPE = Map.of(
		112, "Regular Bird House",
		168, "Oak Bird House",
		224, "Willow Bird House",
		280, "Teak Bird House",
		369, "Maple Bird House",
		480, "Mahogany Bird House",
		612, "Yew Bird House",
		969, "Magic Bird House",
		1200, "Redwood Bird House");
	private static final Pattern TARNISHED_PATTERN = Pattern.compile("You rub the tarnished (?<type>.+) on your clothes.*");
	private static final Map<String, Integer> TARNISHED_ITEMS = Map.of(
		"2h sword", ItemID.TARNISHED_2H_SWORD,
		"amulet", ItemID.TARNISHED_AMULET,
		"battleaxe", ItemID.TARNISHED_BATTLEAXE,
		"halberd", ItemID.TARNISHED_HALBERD,
		"longsword", ItemID.TARNISHED_LONGSWORD,
		"necklace", ItemID.TARNISHED_NECKLACE,
		"ring", ItemID.TARNISHED_RING,
		"spear", ItemID.TARNISHED_SPEAR,
		"bracelet", ItemID.TARNISHED_BRACELET);

	private static final String LOOT_STRING = "You found some loot: ";
	private static final int TEMPOROSS_REGION = 12588;
	private static final int GUARDIANS_OF_THE_RIFT_REGION = 14484;
	private static final int WINTERTODT_REGION = 6461;
	private static final int FONT_OF_CONSUMPTION_REGION = 12106;
	private static final String FONT_OF_CONSUMPTION_USE_MESSAGE = "You place the Unsired into the Font of Consumption...";
	private static final int BA_LOBBY_REGION = 10039;

	private final Client client;
	private final ItemManager itemManager;
	private final LootManager lootManager;

	private Sink sink = (source, type, items) ->
	{
	};
	private BooleanSupplier enabled = () -> false;

	private boolean chestLooted;
	private boolean pvpKeysLooted;
	private boolean lastLoadingIntoInstance;

	private int watchedInventory = -1;
	private Map<Integer, Integer> inventorySnapshot;
	private InvChange inventoryCallback;
	private int inventoryTimeout;

	@Inject
	NativeLootSources(Client client, ItemManager itemManager, LootManager lootManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.lootManager = lootManager;
	}

	/**
	 * @param sink    gets the loot
	 * @param enabled whether to look for loot right now
	 */
	public void start(Sink sink, BooleanSupplier enabled)
	{
		this.sink = sink;
		this.enabled = enabled;
		reset();
	}

	public void reset()
	{
		chestLooted = false;
		pvpKeysLooted = false;
		resetEvent();
	}

	private void loot(String source, Collection<ItemStack> items)
	{
		if (!items.isEmpty())
		{
			sink.loot(source, LootSourceType.EVENT, items);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		boolean inInstance = client.getTopLevelWorldView().isInstance();
		if (event.getGameState() == GameState.LOADING && inInstance != lastLoadingIntoInstance)
		{
			lastLoadingIntoInstance = inInstance;
			chestLooted = false;
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		if (inventoryTimeout > 0 && --inventoryTimeout == 0)
		{
			resetEvent();
		}
	}

	@Subscribe
	public void onPluginLootReceived(PluginLootReceived event)
	{
		// Other plugins (Motherlode Mine, for one) announce loot this way, for the Loot Tracker to pick up
		if (enabled.getAsBoolean() && event.getItems() != null)
		{
			sink.loot(event.getName(), event.getType() == null ? LootSourceType.OTHER : LootSourceType.fromName(event.getType().name()),
				new ArrayList<>(event.getItems()));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (!enabled.getAsBoolean())
		{
			return;
		}
		String source;
		int container;
		switch (event.getGroupId())
		{
			case InterfaceID.BARROWS_REWARD:
				source = "Barrows";
				container = InventoryID.TRAIL_REWARDINV;
				break;
			case InterfaceID.RAIDS_REWARDS:
				if (chestLooted)
				{
					return;
				}
				source = CHAMBERS_OF_XERIC;
				container = InventoryID.RAIDS_REWARDS;
				chestLooted = true;
				break;
			case InterfaceID.TOB_CHESTS:
				if (chestLooted || !inTobChestRegion())
				{
					return;
				}
				source = THEATRE_OF_BLOOD;
				container = InventoryID.TOB_CHESTS;
				chestLooted = true;
				break;
			case InterfaceID.TOA_CHESTS:
				if (chestLooted)
				{
					return;
				}
				source = TOMBS_OF_AMASCUT;
				container = InventoryID.TOA_CHESTS;
				chestLooted = true;
				break;
			case InterfaceID.MISC_COLLECTION:
				source = "Kingdom of Miscellania";
				container = InventoryID.MISC_RESOURCES_COLLECTED;
				break;
			case InterfaceID.TRAWLER_REWARD:
				source = "Fishing Trawler";
				container = InventoryID.TRAWLER_REWARDINV;
				break;
			case InterfaceID.FOSSIL_DRIFTNET:
				source = "Drift Net";
				container = InventoryID.MACRO_CERTER;
				break;
			case InterfaceID.WILDY_LOOT_CHEST:
				if (!pvpKeysLooted)
				{
					pvpKeysLooted = addPvpChestLoot();
				}
				return;
			case InterfaceID.PMOON_REWARD:
				source = LUNAR_CHEST;
				container = InventoryID.PMOON_REWARDINV;
				break;
			case InterfaceID.COLOSSEUM_REWARD_CHEST_2:
				if (chestLooted)
				{
					return;
				}
				source = "Fortis Colosseum";
				container = InventoryID.COLOSSEUM_REWARDS;
				chestLooted = true;
				break;
			default:
				return;
		}
		loot(source, stacks(client.getItemContainer(container)));
	}

	/**
	 * PvP loot keys each have a tab in the loot chest, and each counts as its own loot.
	 */
	private boolean addPvpChestLoot()
	{
		boolean found = false;
		for (int containerId : PVP_LOOT_KEY_CONTAINERS)
		{
			List<ItemStack> items = stacks(client.getItemContainer(containerId));
			if (!items.isEmpty())
			{
				loot("Loot Chest", items);
				found = true;
			}
		}
		return found;
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.DOM_LOOT_CLAIM && enabled.getAsBoolean())
		{
			// Runs once the Doom of Mokhaiotl loot has been claimed
			loot("Doom of Mokhaiotl", stacks(client.getItemContainer(InventoryID.DOM_LOOTPILE)));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType chatType = event.getType();
		if (!enabled.getAsBoolean()
			|| chatType != ChatMessageType.GAMEMESSAGE && chatType != ChatMessageType.SPAM && chatType != ChatMessageType.MESBOX)
		{
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		String message = event.getMessage();
		int regionId = player.getWorldLocation().getRegionID();

		if (isChestMessage(message))
		{
			String chest = CHEST_EVENT_TYPES.get(regionId);
			if (chest != null)
			{
				onInvChange(invAndGround(chest));
			}
			return;
		}

		Matcher locker = ZOMBIE_PIRATE_LOCKER_PATTERN.matcher(message);
		if (locker.matches())
		{
			int quantity = Integer.parseInt(locker.group("qty").replace(",", ""));
			String name = locker.group("item");
			int itemId = "Coins".equals(name) ? ItemID.COINS : searchItem(name);
			if (itemId > 0)
			{
				loot("Zombie Pirate's Locker", List.of(new ItemStack(itemId, quantity)));
			}
		}

		Matcher salvage = SALVAGE_PATTERN.matcher(message);
		if (salvage.matches())
		{
			onInvChange(invOnly(capitalize(salvage.group("tier")) + " salvage"));
			return;
		}

		Matcher golem = GOLEM_CRAFTING_PATTERN.matcher(Text.removeTags(message));
		if (golem.find())
		{
			Integer itemId = GOLEM_CRAFTING_REWARDS.get(golem.group("item"));
			if (itemId != null)
			{
				loot("Golem Crafting", List.of(new ItemStack(itemId, 1)));
			}
			return;
		}

		if (message.endsWith(COURIER_TASK_COMPLETE_MESSAGE))
		{
			onInvChange(invOnly("Courier tasks"));
		}

		if (message.equals(HERBIBOAR_LOOTED_MESSAGE))
		{
			if (!herbSackLoot(event.getTimestamp()))
			{
				onInvChange(invAndGround("Herbiboar"));
			}
			return;
		}

		Matcher ham = HAM_CHEST_LOOTED_PATTERN.matcher(message);
		if (ham.matches() && regionId == HAM_STOREROOM_REGION)
		{
			onInvChange(invAndGround("H.A.M. chest (" + ham.group("key") + ")"));
			return;
		}

		Matcher clue = CLUE_SCROLL_PATTERN.matcher(Text.removeTags(message));
		if (clue.find())
		{
			String tier = clueTier(clue.group(1));
			if (tier != null)
			{
				// Clue caskets show their reward in the same container as Barrows
				onInvChange(InventoryID.TRAIL_REWARDINV, invOnly("Clue Scroll (" + tier + ")"));
			}
			return;
		}

		if (SHADE_CHEST_NO_KEY_PATTERN.matcher(message).matches())
		{
			// No key, so no loot is coming
			resetEvent();
			return;
		}

		Matcher birdhouse = BIRDHOUSE_PATTERN.matcher(message);
		if (birdhouse.matches())
		{
			String type = BIRDHOUSE_XP_TO_TYPE.get(Integer.parseInt(birdhouse.group(1).replace(",", "")));
			if (type != null)
			{
				onInvChange(invAndGround(type));
			}
			return;
		}

		Matcher tarnished = TARNISHED_PATTERN.matcher(message);
		if (tarnished.matches())
		{
			Integer itemId = TARNISHED_ITEMS.get(tarnished.group("type").toLowerCase(Locale.ENGLISH));
			if (itemId != null)
			{
				onInvChange(invOnly(itemManager.getItemComposition(itemId).getMembersName()));
			}
			return;
		}

		if (regionId == TEMPOROSS_REGION && message.startsWith(LOOT_STRING))
		{
			onInvChange(invOnly("Reward pool (Tempoross)"));
			return;
		}
		if (regionId == GUARDIANS_OF_THE_RIFT_REGION && message.startsWith(LOOT_STRING))
		{
			onInvChange(invOnly("Guardians of the Rift"));
			return;
		}
		if (regionId == WINTERTODT_REGION && message.contains(LOOT_STRING))
		{
			onInvChange(invOnly("Reward cart (Wintertodt)"));
			return;
		}
		if (regionId == FONT_OF_CONSUMPTION_REGION && message.equals(FONT_OF_CONSUMPTION_USE_MESSAGE))
		{
			onInvChange(invOnly("Unsired"));
		}
		if (regionId == BA_LOBBY_REGION && chatType == ChatMessageType.MESBOX && message.contains("High level gamble count:"))
		{
			onInvChange(invAndGround("Barbarian Assault high gamble"));
		}

		if (message.startsWith("You rummage through the offerings"))
		{
			countChangedItems(ItemID.ENT_TOTEMS_LOOT, null);
		}
		else if (message.equals("You clean a batch of arrowtips."))
		{
			countChangedItems(ItemID.DIRTY_ARROWTIPS, null);
		}
		else if (message.equals("You crack open the rubium geode and obtain some rubium splinters."))
		{
			countChangedItems(ItemID.RUBIUM_GEODE, null);
		}
	}

	static boolean isChestMessage(String message)
	{
		return message.equals(CHEST_LOOTED_MESSAGE) || message.equals(OTHER_CHEST_LOOTED_MESSAGE)
			|| message.equals(DORGESH_KAAN_CHEST_LOOTED_MESSAGE) || message.equals(ALCHEMIST_SIGNET_CHEST_MESSAGE)
			|| GRUBBY_CHEST_LOOTED_MESSAGE.matcher(message).matches() || message.startsWith(ANCIENT_CHEST_LOOTED_MESSAGE)
			|| LARRAN_LOOTED_PATTERN.matcher(message).matches() || ROGUES_CHEST_PATTERN.matcher(message).matches();
	}

	/**
	 * "Clue Scroll (Hard)" and so on use these names.
	 */
	static String clueTier(String type)
	{
		switch (type.toLowerCase(Locale.ENGLISH))
		{
			case "beginner":
			case "easy":
			case "medium":
			case "hard":
			case "elite":
			case "master":
				return capitalize(type);
			default:
				return null;
		}
	}

	static String capitalize(String word)
	{
		String lower = word.toLowerCase(Locale.ENGLISH);
		return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/**
	 * The herbiboar puts its herbs straight into a herb sack, saying so in one message per herb.
	 */
	private boolean herbSackLoot(int timestamp)
	{
		List<ItemStack> herbs = new ArrayList<>();
		for (MessageNode node : client.getMessages())
		{
			if (node.getTimestamp() != timestamp || node.getType() != ChatMessageType.SPAM)
			{
				continue;
			}
			Matcher matcher = HERBIBOAR_HERB_SACK_PATTERN.matcher(node.getValue());
			if (matcher.matches())
			{
				int itemId = searchItem(matcher.group(1));
				if (itemId > 0)
				{
					herbs.add(new ItemStack(itemId, 1));
				}
			}
		}
		if (herbs.isEmpty())
		{
			return false;
		}
		loot("Herbiboar", herbs);
		return true;
	}

	private int searchItem(String name)
	{
		var found = itemManager.search(name);
		return found.isEmpty() ? -1 : found.get(0).getId();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!enabled.getAsBoolean())
		{
			return;
		}
		String option = event.getMenuOption();
		if (isObjectOp(event.getMenuAction()) && "Open".equals(option) && SHADE_CHEST_OBJECTS.containsKey(event.getId()))
		{
			onInvChange(invAndGround(SHADE_CHEST_OBJECTS.get(event.getId())));
			return;
		}
		if (!event.isItemOp())
		{
			return;
		}
		int itemId = event.getItemId();
		if (itemId == ItemID.SEEDBOX && ("Take".equals(option) || "Take-all".equals(option)))
		{
			onInvChange(invOnly("Seed pack"));
		}
		else if ("Search".equals(option) && BIRDNEST_IDS.contains(itemId))
		{
			onInvChange(invOnly("Bird nest"));
		}
		else if ("Open".equals(option))
		{
			openedItem(itemId);
		}
	}

	private void openedItem(int itemId)
	{
		switch (itemId)
		{
			case ItemID.CASKET:
				onInvChange(invOnly("Casket"));
				break;
			case ItemID.REWARD_GEM_BAG:
				onInvChange(invAndGround("Bag full of gems (Percy)"));
				break;
			case ItemID.REWARD_GEM_BAG_GUILD:
				onInvChange(invAndGround("Bag full of gems (Belona)"));
				break;
			case ItemID.STAR_REWARD_GEM_BAG:
				onInvChange(invAndGround("Bag full of gems (Dusuri)"));
				break;
			case ItemID.WINT_REWARD_BOX:
			case ItemID.WINT_REROLL_BOX:
				onInvChange(invAndGround("Supply crate (Wintertodt)"));
				break;
			case ItemID.SOUL_WARS_SPOILS:
				onInvChange(invOnly("Spoils of war"));
				break;
			case ItemID.TEMPOROSS_REWARD_CASKET:
				onInvChange(invAndGround("Casket (Tempoross)"));
				break;
			case ItemID.CAMDOZAAL_VAULT_CHEST_LOW:
			case ItemID.CAMDOZAAL_VAULT_CHEST_MID:
			case ItemID.CAMDOZAAL_VAULT_CHEST_HIGH:
			case ItemID.TOA_RUNE_CACHE:
			case ItemID.GOTR_INTRICATE_POUCH:
			case ItemID.FROZEN_CACHE:
			case ItemID.BH_EP_CRATE_2:
			case ItemID.BH_EP_CRATE_3:
			case ItemID.BH_EP_CRATE_4:
			case ItemID.BH_EP_CRATE_5:
			case ItemID.BH_EP_CRATE_6:
			case ItemID.BH_EP_CRATE_7:
			case ItemID.BH_EP_CRATE_8:
			case ItemID.BH_EP_CRATE_9:
			case ItemID.BH_EP_CRATE_10:
			case ItemID.MM_POTION_PACK_LOW:
			case ItemID.MM_POTION_PACK_MED:
			case ItemID.MM_POTION_PACK_HIGH:
			case ItemID.CASTLEWARS_CRATE:
			case ItemID.FORGOTTEN_LOCKBOX:
			case ItemID.YAMA_DOSSIER:
				onInvChange(invAndGround(itemManager.getItemComposition(itemId).getName()));
				break;
			case ItemID.CONSTRUCTION_SUPPLY_CRATE:
				onInvChange(invOnly("Supply crate (Mahogany Homes)"));
				break;
			case ItemID.HALLOWED_SACK:
				onInvChange(invAndGround("Hallowed Sack"));
				break;
			case ItemID.WBR_VM_OREPACK:
				onInvChange(invOnly("Ore Pack (Volcanic Mine)"));
				break;
			case ItemID.HG_LOOTSACK_DROPTRACKING:
			case ItemID.HG_LOOTSACK_T0:
			case ItemID.HG_LOOTSACK_T1:
			case ItemID.HG_LOOTSACK_T2:
			case ItemID.HG_LOOTSACK_T3:
				// Hunter sacks stack, and several can be opened in one tick
				onInvChange((added, ground, removed) ->
				{
					if (removed.getOrDefault(itemId, 0) > 0)
					{
						loot(itemManager.getItemComposition(itemId).getMembersName(), added);
					}
				});
				break;
			default:
				ItemComposition item = client.getItemDefinition(itemId);
				if (item.getIntValue(ParamID.COURIER_BAG_TIER) >= 0)
				{
					// A reward bag named after a place, or a coin bag
					String name = item.getMembersName();
					int place = name.indexOf(" (");
					countChangedItems(itemId, place > 0 ? name.substring(0, place) : name);
				}
		}
	}

	private static boolean isObjectOp(MenuAction action)
	{
		int id = action.getId();
		return id >= MenuAction.GAME_OBJECT_FIRST_OPTION.getId() && id <= MenuAction.GAME_OBJECT_FOURTH_OPTION.getId()
			|| id == MenuAction.GAME_OBJECT_FIFTH_OPTION.getId();
	}

	private boolean inTobChestRegion()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return false;
		}
		WorldPoint point = WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		int region = point == null ? -1 : point.getRegionID();
		return region == THEATRE_OF_BLOOD_REGION || region == THEATRE_OF_BLOOD_LOBBY;
	}

	// ------------------------------------------------------------------
	// Waiting for the inventory to change
	// ------------------------------------------------------------------

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (pvpKeysLooted && event.getContainerId() == InventoryID.INV)
		{
			ItemContainer inventory = event.getItemContainer();
			pvpKeysLooted = PVP_LOOT_KEYS.stream().anyMatch(inventory::contains);
		}

		if (watchedInventory == -1 || event.getContainerId() != watchedInventory)
		{
			return;
		}
		Map<Integer, Integer> now = counts(event.getItemContainer());
		Player player = client.getLocalPlayer();
		Collection<ItemStack> ground = player == null ? Collections.emptyList() : lootManager.getItemSpawns(player.getWorldLocation());
		InvChange callback = inventoryCallback;
		Map<Integer, Integer> before = inventorySnapshot;
		resetEvent();
		callback.accept(stacks(difference(now, before)), ground, difference(before, now));
	}

	private void onInvChange(InvChange callback)
	{
		onInvChange(InventoryID.INV, callback);
	}

	private void onInvChange(int inventory, InvChange callback)
	{
		watchedInventory = inventory;
		inventorySnapshot = counts(client.getItemContainer(inventory));
		inventoryCallback = callback;
		inventoryTimeout = INVCHANGE_TIMEOUT * Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH;
	}

	private void resetEvent()
	{
		watchedInventory = -1;
		inventorySnapshot = null;
		inventoryCallback = null;
		inventoryTimeout = 0;
	}

	private InvChange invOnly(String source)
	{
		return (added, ground, removed) -> loot(source, added);
	}

	private InvChange invAndGround(String source)
	{
		return (added, ground, removed) ->
		{
			List<ItemStack> all = new ArrayList<>(added);
			all.addAll(ground);
			loot(source, all);
		};
	}

	/**
	 * Loot from using up an item, counted only when one of them really went.
	 *
	 * @param name the source's name, or null for the item's own name
	 */
	private void countChangedItems(int itemId, String name)
	{
		onInvChange((added, ground, removed) ->
		{
			if (removed.getOrDefault(itemId, 0) > 0)
			{
				List<ItemStack> all = new ArrayList<>(added);
				all.addAll(ground);
				loot(name != null ? name : itemManager.getItemComposition(itemId).getMembersName(), all);
			}
		});
	}

	static Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() >= 0 && item.getQuantity() > 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	/**
	 * What {@code a} has more of than {@code b}.
	 */
	static Map<Integer, Integer> difference(Map<Integer, Integer> a, Map<Integer, Integer> b)
	{
		Map<Integer, Integer> more = new HashMap<>();
		a.forEach((id, quantity) ->
		{
			int extra = quantity - b.getOrDefault(id, 0);
			if (extra > 0)
			{
				more.put(id, extra);
			}
		});
		return more;
	}

	private static List<ItemStack> stacks(Map<Integer, Integer> counts)
	{
		List<ItemStack> stacks = new ArrayList<>();
		counts.forEach((id, quantity) -> stacks.add(new ItemStack(id, quantity)));
		return stacks;
	}

	private static List<ItemStack> stacks(ItemContainer container)
	{
		return stacks(counts(container));
	}
}
