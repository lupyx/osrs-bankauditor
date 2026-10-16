package io.lupix.runelite.bankauditor;

import com.google.gson.Gson;
import com.google.inject.Provides;
import io.lupix.runelite.bankauditor.loot.LootAttribution;
import io.lupix.runelite.bankauditor.loot.LootHistory;
import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootLedger;
import io.lupix.runelite.bankauditor.loot.LootLocation;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import io.lupix.runelite.bankauditor.loot.LootStateCodec;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.sell.KeepList;
import io.lupix.runelite.bankauditor.sell.KeepRule;
import io.lupix.runelite.bankauditor.sell.SellableCalculator;
import io.lupix.runelite.bankauditor.ui.BankAuditorOverlay;
import io.lupix.runelite.bankauditor.ui.BankAuditorPanel;
import io.lupix.runelite.bankauditor.ui.TooltipText;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Bank Auditor",
	description = "Shows which bank stacks came from loot, from what, when and where, and marks what you can sell",
	tags = {"loot", "bank", "sell", "grand exchange", "ge", "tracker", "money", "clean up"}
)
@PluginDependency(BankTagsPlugin.class)
public class BankAuditorPlugin extends Plugin
{
	static final String SELLABLE_TAG = "sellable";

	// RuneScape profile keys holding the plugin's own state, next to the normal config items
	static final String KEY_LEDGER = "ledger";
	static final String KEY_HISTORY = "history";
	static final String KEY_BANK = "bankSnapshot";
	private static final Set<String> STATE_KEYS = Set.of(KEY_LEDGER, KEY_HISTORY, KEY_BANK);

	/**
	 * Containers must have been still for this many ticks before the ledger trusts them.
	 * Moving items between the bank and the inventory updates two containers, not always in the same tick.
	 */
	private static final int QUIET_TICKS = 2;
	/**
	 * After the bank closes, how long to wait for late container updates before the visit is wrapped up.
	 */
	private static final int VISIT_END_MAX_TICKS = 5;
	private static final int SAVE_INTERVAL_TICKS = 50;
	private static final int PANEL_REFRESH_TICKS = 3;
	private static final int PANEL_CLOCK_TICKS = 100;
	private static final int PANEL_LOOT_RECORDS = 50;
	private static final int TOOLTIP_SOURCES = 3;
	private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
	private static final long SHUTDOWN_SAVE_TIMEOUT_MILLIS = 2000;
	private static final int ALL_ITEMS = -1;
	// Bank widgets that hold items the client can't count: the looting bag, the leagues bag and the rune pouch views
	private static final Set<Integer> HIDDEN_CONTAINER_WIDGETS = Set.of(
		InterfaceID.Bankside.LOOTINGBAG_BANKALL, InterfaceID.Bankside.LOOTINGBAG_ITEMS,
		InterfaceID.Bankside.LEAGUE_SECONDINV_BANKALL, InterfaceID.Bankside.LEAGUE_SECONDINV_ITEMS,
		InterfaceID.Bankside.RUNEPOUCH_BANKALL, InterfaceID.Bankside.RUNEPOUCH_CONTENTS,
		InterfaceID.Bankmain.DEPOSITCONTAINERS);

	// Same message the Loot Tracker uses to spot pickpocketing
	private static final Pattern PICKPOCKET = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	// Player kills in these minigames drop minigame-only items. The Loot Tracker skips them too.
	private static final Set<Integer> MINIGAME_PVP_REGIONS = Set.of(
		// Last Man Standing
		13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432,
		// Soul Wars
		8493, 8749, 9005);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private BankAuditorConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankAuditorOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private TagManager tagManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private Gson gson;

	// Everything below is only touched on the client thread unless marked volatile

	private LootStateCodec codec;
	private LootLedger ledger = new LootLedger();
	private LootHistory history = new LootHistory();
	/**
	 * Canonical item id to quantity as of the last time the bank was open. Null until the bank has been seen.
	 */
	private Map<Integer, Long> bankCounts;
	/**
	 * True once the bank has been seen this login and nothing since could have changed it unseen.
	 * A snapshot from an earlier session can miss deposits made on another device.
	 */
	private boolean bankTrusted;
	private Map<Integer, Long> inventoryCounts = Collections.emptyMap();
	private Map<Integer, Long> wornCounts = Collections.emptyMap();
	/**
	 * Inventory and worn items as they were before their first change in the current client tick.
	 * Some loot lands in the inventory in the same tick it is announced, and the baseline must not include it.
	 */
	private Map<Integer, Long> inventoryBeforeTick = Collections.emptyMap();
	private Map<Integer, Long> wornBeforeTick = Collections.emptyMap();
	private int inventoryTick = -1;
	private int wornTick = -1;
	/**
	 * Items sitting in Grand Exchange sell offers, or waiting in the collection box after an aborted sale.
	 * They are still owned, and they are the first thing that stops counting as sellable.
	 */
	private Map<Integer, Long> offerCounts = Collections.emptyMap();
	private volatile KeepList keepList = KeepList.EMPTY;

	/**
	 * The RuneScape profile the state above belongs to.
	 */
	private String profileKey;

	private int tickCounter;
	private boolean bankOpen;
	private int bankOpenedTick;
	/**
	 * Tick of the last bank container update. The container only exists while the bank is open,
	 * so an update at or after {@link #bankOpenedTick} means the bank is fully known for this visit.
	 */
	private int lastBankUpdate = -1;
	private boolean reconcileNeeded;
	private int lastContainerChange;
	private boolean visitEnding;
	private int visitEndTick;
	/*
	 * Bank clicks that the server may not have answered yet, for when the bank is closed in the same moment.
	 * Ticks here are client ticks (Client#getTickCount), which stay the same from one GameTick until after the next.
	 * See checkBankCloseRace and finishBankVisit.
	 */
	private int bankUpdateTick = -2;
	private int bankClosedTick = -2;
	/**
	 * Withdraws and deposits between the bank and the inventory or worn items: canonical item id (or
	 * {@link #ALL_ITEMS} for the deposit buttons) to the client tick of the click.
	 */
	private final Map<Integer, Integer> pendingBankClicks = new HashMap<>();
	/**
	 * Item of a Withdraw-X or Deposit-X whose amount prompt is still open, or null.
	 */
	private Integer amountPromptItem;
	/**
	 * Emptying something the client can't see into, like the looting bag or a herb sack, straight into the bank.
	 */
	private boolean hiddenDepositPending;
	private int hiddenDepositTick = -2;
	/**
	 * Set when such a deposit was lost to a close race. Holds the bank as it was then, so the next bank visit can
	 * tell what the deposit added.
	 */
	private Map<Integer, Long> bankBeforeHiddenDeposit;

	private boolean ledgerDirty;
	private boolean historyDirty;
	private boolean bankDirty;
	private int lastSave;

	private boolean panelDirty;
	private int lastPanelRefresh;

	private Plugin lootTracker;
	private volatile boolean lootTrackerActive;
	private int pickpocketTick = -1;

	// Per tick caches for the overlay, which asks for every visible item every frame
	private final Map<Integer, ItemSummary> summaries = new HashMap<>();
	private final Map<Integer, String> tooltips = new HashMap<>();

	// Swing side, only touched on the event dispatch thread
	private volatile BankAuditorPanel panel;
	private NavigationButton navButton;

	private boolean tagRegistered;

	@Provides
	BankAuditorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankAuditorConfig.class);
	}

	@Override
	protected void startUp()
	{
		codec = new LootStateCodec(gson);
		keepList = KeepList.parse(config.keepList());
		overlayManager.add(overlay);
		if (config.showPanel())
		{
			addPanel();
		}
		if (config.sellableTag())
		{
			registerTag();
		}

		clientThread.invokeLater(() ->
		{
			refreshLootTracker();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				switchProfile(configManager.getRSProfileKey());
				inventoryCounts = count(client.getItemContainer(InventoryID.INV));
				wornCounts = count(client.getItemContainer(InventoryID.WORN));
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				if (bank != null)
				{
					// Turned on with the bank already open
					bankOpen = true;
					bankOpenedTick = tickCounter;
					reconcileNeeded = true;
					onBankContainer(bank);
				}
				offerCounts = countSellOffers();
				onContentsChanged();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		removePanel();
		unregisterTag();

		clientThread.invokeLater(() ->
		{
			saveState(true);
			ledger = new LootLedger();
			history = new LootHistory();
			bankCounts = null;
			bankTrusted = false;
			bankBeforeHiddenDeposit = null;
			clearCarriedCounts();
			profileKey = null;
			bankOpen = false;
			lastBankUpdate = -1;
			visitEnding = false;
			reconcileNeeded = false;
			lootTracker = null;
			clearCaches();
		});
	}

	// ------------------------------------------------------------------
	// Loot
	// ------------------------------------------------------------------

	/*
	 * The Loot Tracker plugin turns NPC and player kills into LootReceived events and adds chests, clues, raids,
	 * pickpocketing and so on. While it runs, LootReceived is the only source used, otherwise the same kill would
	 * be counted twice. Without it, NPC and player kills still come in through the raw events.
	 */

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (lootTrackerActive)
		{
			addLoot(event.getName(), event.getCombatLevel(),
				event.getType() == null ? LootSourceType.OTHER : LootSourceType.fromName(event.getType().name()),
				event.getItems());
		}
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!lootTrackerActive)
		{
			// The server sends this for pickpocketing too, and then the items are already in the inventory
			LootSourceType type = pickpocketTick == client.getTickCount() ? LootSourceType.PICKPOCKET : LootSourceType.NPC;
			NPCComposition npc = event.getComposition();
			addLoot(npc == null ? null : Text.removeTags(npc.getName()), npc == null ? -1 : npc.getCombatLevel(),
				type, event.getItems());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if ((event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
			&& PICKPOCKET.matcher(event.getMessage()).matches())
		{
			pickpocketTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (!lootTrackerActive && !inMinigamePvp())
		{
			Player player = event.getPlayer();
			addLoot(player == null ? null : player.getName(), player == null ? -1 : player.getCombatLevel(),
				LootSourceType.PLAYER, event.getItems());
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() instanceof LootTrackerPlugin)
		{
			clientThread.invokeLater(this::refreshLootTracker);
		}
	}

	private void refreshLootTracker()
	{
		if (lootTracker == null)
		{
			for (Plugin plugin : pluginManager.getPlugins())
			{
				if (plugin instanceof LootTrackerPlugin)
				{
					lootTracker = plugin;
					break;
				}
			}
		}
		lootTrackerActive = lootTracker != null && pluginManager.isPluginActive(lootTracker);
	}

	private void addLoot(String source, int combatLevel, LootSourceType type, Collection<ItemStack> stacks)
	{
		if (!config.trackLoot() || stacks == null || stacks.isEmpty())
		{
			return;
		}

		// Some of these collections are reused and cleared once the event is done, so read them right away
		Map<Integer, Long> quantities = new LinkedHashMap<>();
		for (ItemStack stack : new ArrayList<>(stacks))
		{
			if (stack != null && stack.getId() > 0 && stack.getQuantity() > 0)
			{
				quantities.merge(itemManager.canonicalize(stack.getId()), (long) stack.getQuantity(), Long::sum);
			}
		}
		if (quantities.isEmpty())
		{
			return;
		}

		// Kills drop their loot on the floor, so what the player owned at the start of this tick is what they had
		// before the drop. Chests, clues and pickpocketing can hand out items over several ticks, so there is
		// no such moment for them.
		boolean groundDrop = type == LootSourceType.NPC || type == LootSourceType.PLAYER;
		List<LootItem> items = new ArrayList<>(quantities.size());
		for (Map.Entry<Integer, Long> e : quantities.entrySet())
		{
			int id = e.getKey();
			items.add(new LootItem(id, itemManager.getItemComposition(id).getMembersName(), e.getValue(), itemManager.getItemPrice(id)));
			ledger.addLoot(id, e.getValue(), groundDrop && bankTrusted ? ownedBeforeThisTick(id) : null);
		}

		long now = System.currentTimeMillis();
		history.add(new LootRecord(now, source, type, combatLevel, currentLocation(), items));
		pruneHistory(now);

		ledgerDirty = true;
		historyDirty = true;
		panelDirty = true;
		if (bankOpen)
		{
			reconcileNeeded = true;
		}
		clearCaches();
	}

	private boolean inMinigamePvp()
	{
		for (int region : client.getTopLevelWorldView().getMapRegions())
		{
			if (MINIGAME_PVP_REGIONS.contains(region))
			{
				return true;
			}
		}
		return false;
	}

	private LootLocation currentLocation()
	{
		Player player = client.getLocalPlayer();
		LocalPoint local = player == null ? null : player.getLocalLocation();
		if (local == null)
		{
			return null;
		}
		WorldView view = player.getWorldView();
		if (view != null && !view.isTopLevel())
		{
			// On a boat the player's position is relative to the boat, so move it onto the main map first
			WorldEntity boat = client.getTopLevelWorldView().worldEntities().byIndex(view.getId());
			local = boat == null ? null : boat.transformToMainWorld(local);
			if (local == null)
			{
				return null;
			}
		}
		// Inside instances this gives the real map location instead of the instance copy
		WorldPoint point = WorldPoint.fromLocalInstance(client, local);
		if (point == null)
		{
			return null;
		}
		return new LootLocation(point.getX(), point.getY(), point.getPlane(), point.getRegionID(), client.getWorld());
	}

	private boolean pruneHistory(long now)
	{
		return history.prune(now, config.historyDays() * DAY_MILLIS, config.maxRecords());
	}

	// ------------------------------------------------------------------
	// Bank and inventory
	// ------------------------------------------------------------------

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			onBankContainer(event.getItemContainer());
		}
		else if (id == InventoryID.INV)
		{
			int tick = client.getTickCount();
			if (tick != inventoryTick)
			{
				inventoryBeforeTick = inventoryCounts;
				inventoryTick = tick;
			}
			inventoryCounts = count(event.getItemContainer());
			onContentsChanged();
		}
		else if (id == InventoryID.WORN)
		{
			int tick = client.getTickCount();
			if (tick != wornTick)
			{
				wornBeforeTick = wornCounts;
				wornTick = tick;
			}
			wornCounts = count(event.getItemContainer());
			onContentsChanged();
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		offerCounts = countSellOffers();
		onContentsChanged();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!bankOpen || event.getMenuOption() == null)
		{
			return;
		}
		String option = Text.removeTags(event.getMenuOption());
		int widgetId = event.getParam1();
		int group = WidgetUtil.componentToInterface(widgetId);
		if (group != InterfaceID.BANKMAIN && group != InterfaceID.BANKSIDE)
		{
			return;
		}
		boolean movesItems = option.startsWith("Withdraw") || option.startsWith("Deposit");
		if (HIDDEN_CONTAINER_WIDGETS.contains(widgetId) ? movesItems || option.startsWith("Empty") : option.startsWith("Empty"))
		{
			// Moves items the client can't see, from a bag, sack or pouch into the bank
			hiddenDepositPending = true;
			hiddenDepositTick = client.getTickCount();
		}
		else if (movesItems && widgetId != InterfaceID.Bankmain.POTIONSTORE_ITEMS)
		{
			// Potion storage is skipped because its contents are not part of the bank container
			boolean oneItem = (widgetId == InterfaceID.Bankmain.ITEMS || widgetId == InterfaceID.Bankside.ITEMS
				|| widgetId == InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS) && event.getItemId() > 0;
			int item = oneItem ? itemManager.canonicalize(event.getItemId()) : ALL_ITEMS;
			if (option.endsWith("-X"))
			{
				// Nothing moves until the amount is entered
				amountPromptItem = item;
			}
			else
			{
				pendingBankClicks.put(item, client.getTickCount());
			}
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (amountPromptItem != null && event.getIndex() == VarClientID.MESLAYERMODE
			&& client.getVarcIntValue(VarClientID.MESLAYERMODE) == 0)
		{
			// The amount prompt closed, with an amount or cancelled. Closing the bank cancels it too.
			if (bankOpen)
			{
				pendingBankClicks.put(amountPromptItem, client.getTickCount());
			}
			amountPromptItem = null;
		}
	}

	private void resetBankActions()
	{
		pendingBankClicks.clear();
		amountPromptItem = null;
		hiddenDepositPending = false;
	}

	private void onBankContainer(ItemContainer bank)
	{
		Map<Integer, Long> fresh = count(bank);
		if (bankBeforeHiddenDeposit != null)
		{
			// First look at the bank since a looting bag or sack was emptied into it as it closed
			Map<Integer, Long> gained = new HashMap<>();
			fresh.forEach((id, qty) ->
			{
				long gain = qty - bankBeforeHiddenDeposit.getOrDefault(id, 0L);
				if (gain > 0)
				{
					gained.put(id, gain);
				}
			});
			ledger.restoreMissingFromBank(gained);
			ledger.endBankVisit();
			ledgerDirty = true;
			bankBeforeHiddenDeposit = null;
		}

		bankCounts = fresh;
		bankTrusted = true;
		bankDirty = true;
		lastBankUpdate = tickCounter;
		int tick = client.getTickCount();
		bankUpdateTick = tick;
		// Clicks from earlier ticks have been answered now. A click from this tick may have come too late for it.
		pendingBankClicks.values().removeIf(clickTick -> clickTick < tick);
		if (hiddenDepositTick < tick)
		{
			hiddenDepositPending = false;
		}
		onContentsChanged();
	}

	/**
	 * Runs at the end of every client tick, once that tick's container updates are in. A withdraw or deposit
	 * changes the bank and the inventory in the same tick, but when the bank is closed in the same moment, the
	 * server only sends the inventory. So when the inventory or worn items change right after a bank click that
	 * got no bank update, with the bank closed, the bank snapshot gets the opposite change for the clicked items.
	 */
	private void checkBankCloseRace()
	{
		int tick = client.getTickCount();
		// The server answers a click in the same tick or the next one
		pendingBankClicks.values().removeIf(clickTick -> clickTick < tick - 1);
		boolean carriedChanged = inventoryTick == tick || wornTick == tick;
		if (!carriedChanged || pendingBankClicks.isEmpty() || bankOpen || bankCounts == null || bankUpdateTick == tick)
		{
			return;
		}
		// One correction per click. Later changes are eating, teleports and so on.
		Set<Integer> clicked = new HashSet<>(pendingBankClicks.keySet());
		pendingBankClicks.clear();
		boolean allItems = clicked.contains(ALL_ITEMS);

		Map<Integer, Long> before = new HashMap<>(inventoryTick == tick ? inventoryBeforeTick : inventoryCounts);
		(wornTick == tick ? wornBeforeTick : wornCounts).forEach((id, qty) -> before.merge(id, qty, Long::sum));
		Map<Integer, Long> after = new HashMap<>(inventoryCounts);
		wornCounts.forEach((id, qty) -> after.merge(id, qty, Long::sum));

		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		if (!allItems)
		{
			ids.retainAll(clicked);
		}
		Map<Integer, Long> bank = new HashMap<>(bankCounts);
		for (int id : ids)
		{
			long moved = after.getOrDefault(id, 0L) - before.getOrDefault(id, 0L);
			if (moved != 0)
			{
				long left = bank.getOrDefault(id, 0L) - moved;
				if (left > 0)
				{
					bank.put(id, left);
				}
				else
				{
					bank.remove(id);
				}
			}
		}
		bankCounts = bank;
		bankDirty = true;
		onContentsChanged();
	}

	private void onContentsChanged()
	{
		lastContainerChange = tickCounter;
		if (bankOpen)
		{
			reconcileNeeded = true;
		}
		panelDirty = true;
		clearCaches();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			if (visitEnding)
			{
				finishBankVisit();
			}
			pendingBankClicks.clear();
			bankOpen = true;
			bankOpenedTick = tickCounter;
			reconcileNeeded = true;
			lastContainerChange = tickCounter;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN && bankOpen)
		{
			// Don't settle the visit yet. The last deposit (a looting bag, say) can still be on its way.
			bankOpen = false;
			bankClosedTick = client.getTickCount();
			amountPromptItem = null;
			reconcileNeeded = false;
			visitEnding = true;
			visitEndTick = tickCounter;
		}
	}

	/**
	 * Wraps up a bank visit. There is no full reconcile here because the last bank update may never have
	 * arrived, so only loot that went missing and came back is booked. Losses wait for the next visit.
	 */
	private void finishBankVisit()
	{
		visitEnding = false;
		amountPromptItem = null;
		boolean hiddenDepositLost = hiddenDepositPending && hiddenDepositTick >= bankClosedTick - 1 && bankCounts != null;
		hiddenDepositPending = false;
		if (hiddenDepositLost)
		{
			// A looting bag or sack was emptied into the bank right as it closed, and the bank update never came.
			// The loot that went missing this visit may be in there. Keep it open, and let the next look at the bank
			// give back what the bank gained.
			bankBeforeHiddenDeposit = new HashMap<>(bankCounts);
		}
		else
		{
			if (ledger.restoreMissing(ownedTotals()))
			{
				panelDirty = true;
				clearCaches();
			}
			ledger.endBankVisit();
		}
		ledgerDirty = true;
		saveState(false);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkBankCloseRace();
		tickCounter++;
		// Tooltips say "5 min ago", and prices change, so rebuild them now and then
		clearCaches();

		if (visitEnding && (tickCounter - visitEndTick >= VISIT_END_MAX_TICKS
			|| (tickCounter - visitEndTick >= QUIET_TICKS && tickCounter - lastContainerChange >= QUIET_TICKS)))
		{
			finishBankVisit();
		}

		if (bankOpen && lastBankUpdate >= bankOpenedTick && reconcileNeeded && tickCounter - lastContainerChange >= QUIET_TICKS)
		{
			reconcileNeeded = false;
			if (ledger.reconcile(ownedTotals()))
			{
				ledgerDirty = true;
				panelDirty = true;
			}
		}

		if (tickCounter - lastSave >= SAVE_INTERVAL_TICKS)
		{
			saveState(false);
		}

		if (tickCounter - lastPanelRefresh >= PANEL_CLOCK_TICKS)
		{
			// Keeps the "ago" times in the panel current
			panelDirty = true;
		}
		BankAuditorPanel p = panel;
		if (panelDirty && p != null && p.isActive() && tickCounter - lastPanelRefresh >= PANEL_REFRESH_TICKS)
		{
			refreshPanel();
		}
	}

	/**
	 * What the player owns right now: bank, inventory, worn items and items in Grand Exchange sell offers.
	 */
	private Map<Integer, Long> ownedTotals()
	{
		Map<Integer, Long> totals = new HashMap<>();
		if (bankCounts != null)
		{
			totals.putAll(bankCounts);
		}
		inventoryCounts.forEach((id, qty) -> totals.merge(id, qty, Long::sum));
		wornCounts.forEach((id, qty) -> totals.merge(id, qty, Long::sum));
		offerCounts.forEach((id, qty) -> totals.merge(id, qty, Long::sum));
		return totals;
	}

	private long ownedTotal(int id)
	{
		long bank = bankCounts == null ? 0 : bankCounts.getOrDefault(id, 0L);
		return bank + inventoryCounts.getOrDefault(id, 0L) + wornCounts.getOrDefault(id, 0L) + offerCounts.getOrDefault(id, 0L);
	}

	/**
	 * What the player owned of an item before anything changed in the current tick.
	 */
	private long ownedBeforeThisTick(int id)
	{
		int tick = client.getTickCount();
		Map<Integer, Long> inventory = inventoryTick == tick ? inventoryBeforeTick : inventoryCounts;
		Map<Integer, Long> worn = wornTick == tick ? wornBeforeTick : wornCounts;
		long bank = bankCounts == null ? 0 : bankCounts.getOrDefault(id, 0L);
		return bank + inventory.getOrDefault(id, 0L) + worn.getOrDefault(id, 0L) + offerCounts.getOrDefault(id, 0L);
	}

	private void clearCarriedCounts()
	{
		inventoryCounts = Collections.emptyMap();
		wornCounts = Collections.emptyMap();
		inventoryBeforeTick = Collections.emptyMap();
		wornBeforeTick = Collections.emptyMap();
		inventoryTick = -1;
		wornTick = -1;
		offerCounts = Collections.emptyMap();
		// The first containers after logging in are not a bank transfer
		resetBankActions();
	}

	/**
	 * Unsold items in sell offers, including aborted offers until their slot is collected. The client can't see
	 * the collection box, so items taken out of an aborted offer whose coins are still waiting count twice.
	 */
	private Map<Integer, Long> countSellOffers()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return Collections.emptyMap();
		}
		Map<Integer, Long> counts = new HashMap<>();
		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null || offer.getItemId() <= 0)
			{
				continue;
			}
			GrandExchangeOfferState state = offer.getState();
			if (state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.CANCELLED_SELL)
			{
				long unsold = offer.getTotalQuantity() - offer.getQuantitySold();
				if (unsold > 0)
				{
					counts.merge(itemManager.canonicalize(offer.getItemId()), unsold, Long::sum);
				}
			}
		}
		return counts;
	}

	private Map<Integer, Long> count(ItemContainer container)
	{
		if (container == null)
		{
			return Collections.emptyMap();
		}
		Map<Integer, Long> counts = new HashMap<>();
		for (Item item : container.getItems())
		{
			// Placeholders have a quantity of 0
			if (item.getId() <= 0 || item.getQuantity() <= 0 || item.getId() == ItemID.BANK_FILLER)
			{
				continue;
			}
			counts.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
		}
		return counts;
	}

	// ------------------------------------------------------------------
	// What the overlay, tooltip, bank tag and panel ask for
	// ------------------------------------------------------------------

	/**
	 * Loot and sell information for an item, or null for items that aren't worth talking about.
	 * Must be called on the client thread.
	 *
	 * @param rawItemId any item id, noted and placeholder ids included
	 */
	public ItemSummary getSummary(int rawItemId)
	{
		if (rawItemId <= 0)
		{
			return null;
		}
		if (summaries.containsKey(rawItemId))
		{
			return summaries.get(rawItemId);
		}
		ItemSummary summary = buildSummary(rawItemId);
		summaries.put(rawItemId, summary);
		return summary;
	}

	private ItemSummary buildSummary(int rawItemId)
	{
		int id = itemManager.canonicalize(rawItemId);
		if (id == ItemID.BANK_FILLER)
		{
			return null;
		}
		ItemComposition composition = itemManager.getItemComposition(id);
		String name = composition.getMembersName();
		if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
		{
			return null;
		}

		boolean tradeable = composition.isGeTradeable() && id != ItemID.COINS && id != ItemID.PLATINUM;
		long bank = bankCounts == null ? 0 : bankCounts.getOrDefault(id, 0L);
		long owned = bank + inventoryCounts.getOrDefault(id, 0L);
		long offered = offerCounts.getOrDefault(id, 0L);
		// Worn items and items up for sale count for the ledger, but they are in use or already being sold,
		// so they are never shown as loot to sell. Items in sell offers are taken out of the loot first.
		long looted = Math.min(Math.max(0, ledger.estimateLooted(id, ownedTotal(id)) - offered), owned);
		long price = itemManager.getItemPrice(id);
		KeepRule rule = keepList.match(name);

		long sellable = SellableCalculator.sellableQuantity(config.sellMode(), tradeable, owned, looted, rule);
		if (!SellableCalculator.isWorthSelling(sellable, price, config.minimumValue()))
		{
			sellable = 0;
		}
		return new ItemSummary(id, name, tradeable, owned, looted, sellable, price, rule);
	}

	/**
	 * Tooltip text for an item, or null when there is nothing to say. Must be called on the client thread.
	 */
	public String getTooltip(ItemSummary item)
	{
		int id = item.getId();
		if (tooltips.containsKey(id))
		{
			return tooltips.get(id);
		}
		LootAttribution attribution = item.getLooted() > 0
			? LootAttribution.of(id, item.getLooted(), history.containing(id))
			: null;
		String text = TooltipText.build(item, attribution, System.currentTimeMillis(), TOOLTIP_SOURCES);
		tooltips.put(id, text);
		return text;
	}

	private void clearCaches()
	{
		summaries.clear();
		tooltips.clear();
	}

	private List<ItemSummary> sellList()
	{
		Set<Integer> ids = new HashSet<>(inventoryCounts.keySet());
		if (bankCounts != null)
		{
			ids.addAll(bankCounts.keySet());
		}
		List<ItemSummary> items = new ArrayList<>();
		for (int id : ids)
		{
			ItemSummary item = getSummary(id);
			if (item != null && item.isSellable())
			{
				items.add(item);
			}
		}
		items.sort(Comparator.comparingLong(ItemSummary::getSellableValue).reversed()
			.thenComparing(ItemSummary::getName));
		return items;
	}

	// ------------------------------------------------------------------
	// Keep list menu option
	// ------------------------------------------------------------------

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.keepMenuOption() || !"Examine".equals(event.getOption()))
		{
			return;
		}
		int widgetId = event.getActionParam1();
		if (widgetId != InterfaceID.Bankmain.ITEMS && widgetId != InterfaceID.Bankside.ITEMS
			&& widgetId != InterfaceID.Inventory.ITEMS && widgetId != InterfaceID.GeOffersSide.ITEMS)
		{
			return;
		}

		MenuEntry examine = event.getMenuEntry();
		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			Widget widget = examine.getWidget();
			itemId = widget == null ? -1 : widget.getItemId();
		}
		ItemSummary item = getSummary(itemId);
		if (item == null || !item.isTradeable())
		{
			// Untradeable items are never sellable, so there is nothing to keep
			return;
		}

		String name = item.getName();
		boolean kept = keepList.containsExactly(name);
		client.getMenu().createMenuEntry(-1)
			.setOption(kept ? "Unkeep" : "Keep")
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.setParam0(examine.getParam0())
			.setParam1(widgetId)
			.setIdentifier(examine.getIdentifier())
			.setItemId(itemId)
			.onClick(e -> config.setKeepList(kept ? KeepList.remove(config.keepList(), name) : KeepList.add(config.keepList(), name)));
	}

	// ------------------------------------------------------------------
	// Sellable bank tag
	// ------------------------------------------------------------------

	private void registerTag()
	{
		if (!tagRegistered)
		{
			tagManager.registerTag(SELLABLE_TAG, this::isSellableForTag);
			tagRegistered = true;
		}
	}

	private void unregisterTag()
	{
		if (tagRegistered)
		{
			tagManager.unregisterTag(SELLABLE_TAG);
			tagRegistered = false;
			clientThread.invokeLater(() ->
			{
				if (SELLABLE_TAG.equals(bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
			});
		}
	}

	private boolean isSellableForTag(int itemId)
	{
		ItemSummary item = getSummary(itemId);
		return item != null && item.isSellable();
	}

	// ------------------------------------------------------------------
	// Side panel
	// ------------------------------------------------------------------

	private void addPanel()
	{
		if (navButton != null)
		{
			return;
		}
		BankAuditorPanel newPanel = new BankAuditorPanel(itemManager, () -> clientThread.invokeLater(this::clearLootData));
		newPanel.setOnActivate(() -> clientThread.invokeLater(this::refreshPanel));
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Bank Auditor")
			.icon(icon)
			.priority(7)
			.panel(newPanel)
			.build();
		panel = newPanel;
		clientToolbar.addNavigation(navButton);
	}

	private void removePanel()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
			panel = null;
		}
	}

	private void refreshPanel()
	{
		BankAuditorPanel target = panel;
		if (target == null)
		{
			return;
		}
		panelDirty = false;
		lastPanelRefresh = tickCounter;

		List<LootRecord> newest = history.newestFirst();
		List<LootRecord> shown = new ArrayList<>(newest.subList(0, Math.min(PANEL_LOOT_RECORDS, newest.size())));
		int total = newest.size();
		List<ItemSummary> sell = sellList();
		boolean bankSeen = bankCounts != null;
		boolean tagEnabled = config.sellableTag();
		long now = System.currentTimeMillis();

		SwingUtilities.invokeLater(() ->
		{
			target.showLoot(shown, total, now);
			target.showSellList(sell, bankSeen, tagEnabled);
		});
	}

	private void clearLootData()
	{
		ledger.clear();
		history.clear();
		ledgerDirty = true;
		historyDirty = true;
		clearCaches();
		saveState(true);
		refreshPanel();
	}

	// ------------------------------------------------------------------
	// Config and accounts
	// ------------------------------------------------------------------

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BankAuditorConfig.GROUP.equals(event.getGroup()) || STATE_KEYS.contains(event.getKey()))
		{
			return;
		}

		switch (event.getKey())
		{
			case "keepList":
				keepList = KeepList.parse(config.keepList());
				break;
			case "showPanel":
				SwingUtilities.invokeLater(() ->
				{
					if (config.showPanel())
					{
						addPanel();
					}
					else
					{
						removePanel();
					}
				});
				break;
			case "sellableTag":
				if (config.sellableTag())
				{
					registerTag();
				}
				else
				{
					unregisterTag();
				}
				break;
			case "highlightStyle":
			case "sellableColor":
				overlay.clearCaches();
				break;
			default:
				break;
		}

		clientThread.invokeLater(() ->
		{
			if (pruneHistory(System.currentTimeMillis()))
			{
				historyDirty = true;
			}
			clearCaches();
			panelDirty = true;
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			switchProfile(configManager.getRSProfileKey());
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (bankOpen || visitEnding)
			{
				bankOpen = false;
				reconcileNeeded = false;
				finishBankVisit();
			}
			saveState(false);
			bankTrusted = false;
			clearCarriedCounts();
			clearCaches();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		switchProfile(configManager.getRSProfileKey());
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// ConfigManager writes the config to disk in its own ClientShutdown handler, which runs after this one.
		// The save has to be done by then, so wait for the client thread here instead of handing it a future.
		CompletableFuture<Void> saved = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				if (bankOpen || visitEnding)
				{
					bankOpen = false;
					reconcileNeeded = false;
					finishBankVisit();
				}
				saveState(false);
			}
			finally
			{
				saved.complete(null);
			}
		});
		try
		{
			saved.get(SHUTDOWN_SAVE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (ExecutionException | TimeoutException e)
		{
			log.debug("Bank Auditor state was not saved before shutdown", e);
		}
	}

	private void switchProfile(String newProfileKey)
	{
		if (Objects.equals(newProfileKey, profileKey))
		{
			return;
		}
		saveState(false);

		profileKey = newProfileKey;
		if (newProfileKey == null)
		{
			ledger = new LootLedger();
			history = new LootHistory();
			bankCounts = null;
		}
		else
		{
			ledger = codec.readLedger(configManager.getConfiguration(BankAuditorConfig.GROUP, newProfileKey, KEY_LEDGER));
			history = codec.readHistory(configManager.getConfiguration(BankAuditorConfig.GROUP, newProfileKey, KEY_HISTORY));
			bankCounts = codec.readCounts(configManager.getConfiguration(BankAuditorConfig.GROUP, newProfileKey, KEY_BANK));
		}
		ledgerDirty = false;
		historyDirty = pruneHistory(System.currentTimeMillis());
		bankDirty = false;
		bankTrusted = false;
		resetBankActions();
		bankBeforeHiddenDeposit = null;
		bankOpen = false;
		lastBankUpdate = -1;
		visitEnding = false;
		reconcileNeeded = false;
		clearCaches();
		panelDirty = true;
	}

	/**
	 * Writes changed state to the RuneScape profile the state belongs to.
	 *
	 * @param everything write all of it, changed or not
	 */
	private void saveState(boolean everything)
	{
		lastSave = tickCounter;
		if (profileKey == null || codec == null)
		{
			return;
		}
		if (everything || ledgerDirty)
		{
			configManager.setConfiguration(BankAuditorConfig.GROUP, profileKey, KEY_LEDGER, codec.writeLedger(ledger));
			ledgerDirty = false;
		}
		if (everything || historyDirty)
		{
			configManager.setConfiguration(BankAuditorConfig.GROUP, profileKey, KEY_HISTORY, codec.writeHistory(history));
			historyDirty = false;
		}
		if ((everything || bankDirty) && bankCounts != null)
		{
			configManager.setConfiguration(BankAuditorConfig.GROUP, profileKey, KEY_BANK, codec.writeCounts(bankCounts));
			bankDirty = false;
		}
	}
}
