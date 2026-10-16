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
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
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
	private static final int SAVE_INTERVAL_TICKS = 50;
	private static final int PANEL_REFRESH_TICKS = 3;
	private static final int PANEL_CLOCK_TICKS = 100;
	private static final int PANEL_LOOT_RECORDS = 50;
	private static final int TOOLTIP_SOURCES = 3;
	private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

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
	private Map<Integer, Long> inventoryCounts = Collections.emptyMap();
	private Map<Integer, Long> wornCounts = Collections.emptyMap();
	private volatile KeepList keepList = KeepList.EMPTY;

	/**
	 * The RuneScape profile the state above belongs to.
	 */
	private String profileKey;

	private int tickCounter;
	private boolean bankOpen;
	private boolean bankSeenThisVisit;
	private boolean reconcileNeeded;
	private int lastContainerChange;

	private boolean ledgerDirty;
	private boolean historyDirty;
	private boolean bankDirty;
	private int lastSave;

	private boolean panelDirty;
	private int lastPanelRefresh;

	private Plugin lootTracker;
	private volatile boolean lootTrackerActive;

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
					onBankContainer(bank);
				}
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
			inventoryCounts = Collections.emptyMap();
			wornCounts = Collections.emptyMap();
			profileKey = null;
			bankOpen = false;
			bankSeenThisVisit = false;
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
			NPCComposition npc = event.getComposition();
			addLoot(npc == null ? null : Text.removeTags(npc.getName()), npc == null ? -1 : npc.getCombatLevel(),
				LootSourceType.NPC, event.getItems());
		}
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (!lootTrackerActive)
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

		List<LootItem> items = new ArrayList<>(quantities.size());
		for (Map.Entry<Integer, Long> e : quantities.entrySet())
		{
			int id = e.getKey();
			items.add(new LootItem(id, itemManager.getItemComposition(id).getMembersName(), e.getValue(), itemManager.getItemPrice(id)));
			ledger.addLoot(id, e.getValue());
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

	private LootLocation currentLocation()
	{
		Player player = client.getLocalPlayer();
		LocalPoint local = player == null ? null : player.getLocalLocation();
		if (local == null)
		{
			return null;
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
			inventoryCounts = count(event.getItemContainer());
			onContentsChanged();
		}
		else if (id == InventoryID.WORN)
		{
			wornCounts = count(event.getItemContainer());
			onContentsChanged();
		}
	}

	private void onBankContainer(ItemContainer bank)
	{
		bankCounts = count(bank);
		bankDirty = true;
		// The bank container only exists while the bank is open, so getting it means the bank is fully known
		bankSeenThisVisit = true;
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
			bankOpen = true;
			reconcileNeeded = true;
			lastContainerChange = tickCounter;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			endBankVisit();
			saveState(false);
		}
	}

	private void endBankVisit()
	{
		if (bankOpen || bankSeenThisVisit)
		{
			// No reconcile here: the last bank update can arrive after the interface closed.
			// Whatever changed is picked up on the next visit instead.
			ledger.endBankVisit();
			ledgerDirty = true;
		}
		bankOpen = false;
		bankSeenThisVisit = false;
		reconcileNeeded = false;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCounter++;
		// Tooltips say "5 min ago", and prices change, so rebuild them now and then
		clearCaches();

		if (bankOpen && bankSeenThisVisit && reconcileNeeded && tickCounter - lastContainerChange >= QUIET_TICKS)
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
	 * What the player owns right now: bank, inventory and worn items.
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
		return totals;
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
		long total = owned + wornCounts.getOrDefault(id, 0L);
		// Worn items count for the ledger but are in use, so they are never shown as loot to sell
		long looted = Math.min(ledger.estimateLooted(id, total), owned);
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
			endBankVisit();
			saveState(false);
			inventoryCounts = Collections.emptyMap();
			wornCounts = Collections.emptyMap();
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
		CompletableFuture<Void> saved = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				saveState(false);
			}
			finally
			{
				saved.complete(null);
			}
		});
		event.waitFor(saved);
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
		bankOpen = false;
		bankSeenThisVisit = false;
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
