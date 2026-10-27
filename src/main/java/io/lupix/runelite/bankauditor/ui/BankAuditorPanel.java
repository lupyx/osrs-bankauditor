package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.loot.TripLog;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.util.Formats;
import java.awt.BorderLayout;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel with two tabs: recent loot (what, when, from what and where) and the sell list.
 * The loot tab can show each drop on its own or combine them by source, time range, trip or slayer task.
 * All methods must be called on the Swing event thread.
 */
public class BankAuditorPanel extends PluginPanel
{
	private static final int ROW_GAP = 6;
	/**
	 * Rows shown in the sell list. The summary above it still counts everything, and the bank tag shows it all.
	 */
	private static final int MAX_SELL_ROWS = 50;
	/**
	 * Drops shown one by one in the default view, newest first.
	 */
	private static final int MAX_LOOT_ROWS = 50;
	private static final int MAX_GROUPS = 50;
	/**
	 * Item lines per combined box, the most valuable ones.
	 */
	private static final int MAX_GROUP_LINES = 15;
	private static final int SOURCE_ICON_SIZE = 25;
	private static final Color HEADER_COLOR = new Color(58, 54, 48);
	private static final String HIGHLIGHT_COLOR = "#9a6400";
	private static final int VALUE_ICON_SIZE = 12;
	private static final int VALUE_COLUMN_WIDTH = 50;
	private static final ImageIcon GE_ICON = blankIcon();
	private static final ImageIcon GE_OFF_ICON = blankIcon();
	private static final ImageIcon HA_ICON = blankIcon();
	private static final ImageIcon HA_OFF_ICON = blankIcon();
	private static final int TOTAL_ICON_SIZE = 16;
	private static final int TOTAL_GAP = 14;
	private static final ImageIcon GE_TOTAL_ICON = blankIcon(TOTAL_ICON_SIZE);
	private static final ImageIcon HA_TOTAL_ICON = blankIcon(TOTAL_ICON_SIZE);
	private static final int MODE_BUTTON_SIZE = 20;
	private static final int SEARCH_DELAY_MILLIS = 250;
	private static final int ITEM_ICON_WIDTH = 36;
	private static final int ITEM_ICON_HEIGHT = 32;
	private static final Color ROW_ALT_COLOR = new Color(35, 35, 35);
	private static final ImageIcon BIN_ICON = new ImageIcon(ImageUtil.loadImageResource(BankAuditorPanel.class, "/io/lupix/runelite/bankauditor/bin.png"));

	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final SourceIcons icons;
	private final Map<Integer, ImageIcon> sprites = new HashMap<>();
	private ValueTiers tiers = ValueTiers.NONE;
	private Map<Integer, ItemValue> itemValues = Collections.emptyMap();
	private boolean bestValueOnly;
	/**
	 * Keys of the boxes whose full item list is showing.
	 */
	private final Set<String> expanded = new HashSet<>();
	private final Runnable onClearLoot;
	private final Consumer<LootGroups.Mode> onModeChange;
	private final Consumer<LootGroups.Interval> onIntervalChange;
	private LootGroups.Mode mode;
	private LootGroups.Interval interval;
	private JPanel intervalRow;
	private final Runnable onEndTrip;
	private JPanel tripRow;
	private JLabel endTripButton;
	private boolean endTripHovered;
	private TripLog trips = new TripLog();
	private final Map<LootGroups.Interval, JLabel> intervalChips = new EnumMap<>(LootGroups.Interval.class);
	private LootGroups.Mode hoveredMode;
	private final Map<LootGroups.Mode, JLabel> modeButtons = new EnumMap<>(LootGroups.Mode.class);
	private List<LootRecord> lootRecords = Collections.emptyList();
	private long lootNow;

	private Runnable onActivate = () ->
	{
	};
	private volatile boolean active;

	private final JLabel lootSummary = new JLabel();
	private final IconTextField searchBar = new IconTextField();
	private final Timer searchTimer = new Timer(SEARCH_DELAY_MILLIS, e -> renderLoot());
	private boolean matchSource = true;
	private boolean matchLocation = true;
	private boolean matchItem = true;
	/**
	 * The search being shown, so the boxes can highlight what matched.
	 */
	private String query = "";
	private final JPanel lootList = new JPanel();
	private final JLabel sellSummary = new JLabel();
	private final JPanel sellList = new JPanel();
	private List<String> shownSellRows = Collections.emptyList();

	/**
	 * @param mode         the loot view to start on
	 * @param onModeChange told when the user picks another loot view, so it can be remembered
	 * @param onEndTrip    ends the loot trip that is going
	 */
	public BankAuditorPanel(ItemManager itemManager, SpriteManager spriteManager, SourceIcons icons, Runnable onClearLoot,
		LootGroups.Mode mode, Consumer<LootGroups.Mode> onModeChange,
		LootGroups.Interval interval, Consumer<LootGroups.Interval> onIntervalChange, Runnable onEndTrip)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.icons = icons;
		this.onClearLoot = onClearLoot;
		this.mode = mode;
		this.onModeChange = onModeChange;
		this.interval = interval;
		this.onIntervalChange = onIntervalChange;
		this.onEndTrip = onEndTrip;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 2, 5, 0));
		tabs.setBorder(new EmptyBorder(0, 0, 10, 0));

		MaterialTab lootTab = new MaterialTab("Loot", tabs, buildLootView());
		MaterialTab sellTab = new MaterialTab("Sell", tabs, buildSellView());
		tabs.addTab(lootTab);
		tabs.addTab(sellTab);
		tabs.select(lootTab);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
		loadValueIcons();
	}

	/**
	 * Called whenever the panel is opened, so the plugin can fill it with fresh data.
	 */
	public void setOnActivate(Runnable onActivate)
	{
		this.onActivate = onActivate;
	}

	@Override
	public void onActivate()
	{
		active = true;
		onActivate.run();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	/**
	 * Whether the panel is currently open. Safe to call from any thread.
	 */
	public boolean isActive()
	{
		return active;
	}

	private JPanel buildLootView()
	{
		JPanel view = new JPanel(new BorderLayout(0, ROW_GAP));
		view.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel summaryRow = new JPanel(new BorderLayout(6, 0));
		summaryRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		styleSummary(lootSummary);
		summaryRow.add(lootSummary, BorderLayout.CENTER);
		summaryRow.add(buildModeButtons(), BorderLayout.EAST);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(buildSearchBar());
		top.add(Box.createVerticalStrut(4));
		top.add(buildFilterRow());
		top.add(Box.createVerticalStrut(ROW_GAP));
		top.add(summaryRow);
		intervalRow = buildIntervalRow();
		top.add(intervalRow);
		tripRow = buildTripRow();
		top.add(tripRow);
		view.add(top, BorderLayout.NORTH);

		lootList.setLayout(new BoxLayout(lootList, BoxLayout.Y_AXIS));
		lootList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		view.add(lootList, BorderLayout.CENTER);

		JButton clear = new JButton("Clear loot data", BIN_ICON);
		clear.setIconTextGap(6);
		clear.setToolTipText("Forget all loot history and stop counting current stacks as loot");
		clear.addActionListener(e ->
		{
			// Cancel is the default, so pressing Enter or Space by accident keeps the data
			Object[] options = {"Clear", "Cancel"};
			int answer = JOptionPane.showOptionDialog(this,
				"This forgets your loot history and stops counting any current stacks as loot. It can't be undone.",
				"Clear loot data", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
			if (answer == 0)
			{
				onClearLoot.run();
			}
		});
		view.add(clear, BorderLayout.SOUTH);
		return view;
	}

	private IconTextField buildSearchBar()
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
		searchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setToolTipText("Search loot by source, location or item name");
		// Wait for a short pause in typing, since every render rebuilds the whole list
		searchTimer.setRepeats(false);
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchTimer.restart();
			}
		});
		searchBar.addClearListener(this::renderLoot);
		return searchBar;
	}

	/**
	 * Checkboxes under the search bar for what the search looks at. At least one stays on.
	 */
	private JPanel buildFilterRow()
	{
		// Spread over the full width, lined up with the edges of the search bar
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.add(filterBox("Source", "source names", () -> matchSource, on -> matchSource = on));
		row.add(Box.createHorizontalGlue());
		row.add(filterBox("Location", "location names", () -> matchLocation, on -> matchLocation = on));
		row.add(Box.createHorizontalGlue());
		row.add(filterBox("Item", "item names", () -> matchItem, on -> matchItem = on));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JCheckBox filterBox(String name, String what, BooleanSupplier get, Consumer<Boolean> set)
	{
		JCheckBox box = new JCheckBox(name, get.getAsBoolean());
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(Color.WHITE);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setFocusable(false);
		box.setIconTextGap(3);
		box.setBorder(new EmptyBorder(0, 0, 0, 0));
		box.setToolTipText("Search " + what);
		box.addActionListener(e ->
		{
			boolean on = box.isSelected();
			// Unticking the last one would hide everything, so it stays ticked
			if (!on && (matchSource ? 1 : 0) + (matchLocation ? 1 : 0) + (matchItem ? 1 : 0) == 1)
			{
				box.setSelected(true);
				return;
			}
			set.accept(on);
			if (!searchBar.getText().trim().isEmpty())
			{
				renderLoot();
			}
		});
		return box;
	}

	private String filterNames()
	{
		List<String> names = new ArrayList<>();
		if (matchSource)
		{
			names.add("source");
		}
		if (matchLocation)
		{
			names.add("location");
		}
		if (matchItem)
		{
			names.add("item");
		}
		return String.join(", ", names) + (names.size() == 1 ? " name" : " names");
	}

	/**
	 * A row of small icon buttons that switch the loot list between its views. They look nothing like
	 * the Loot and Sell tabs on purpose, since they only change how the same list is shown.
	 */
	private JPanel buildModeButtons()
	{
		JPanel row = new JPanel(new GridLayout(1, LootGroups.Mode.values().length, 2, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (LootGroups.Mode m : LootGroups.Mode.values())
		{
			JLabel button = new JLabel(new ImageIcon(ImageUtil.loadImageResource(BankAuditorPanel.class, m.getIcon())));
			button.setOpaque(true);
			button.setPreferredSize(new Dimension(MODE_BUTTON_SIZE, MODE_BUTTON_SIZE));
			button.setToolTipText(m.getLabel() + ": " + m.getDescription());
			button.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (SwingUtilities.isLeftMouseButton(e) && mode != m)
					{
						mode = m;
						styleModeButtons();
						onModeChange.accept(m);
						intervalRow.setVisible(m == LootGroups.Mode.TIME);
						tripRow.setVisible(m == LootGroups.Mode.TRIP);
						renderLoot();
					}
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hoveredMode = m;
					styleModeButtons();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					if (hoveredMode == m)
					{
						hoveredMode = null;
						styleModeButtons();
					}
				}
			});
			modeButtons.put(m, button);
			row.add(button);
		}
		styleModeButtons();
		return row;
	}

	/**
	 * Chips for how much time each box covers, only shown in the time range view.
	 */
	private JPanel buildIntervalRow()
	{
		JPanel row = new JPanel(new GridLayout(1, LootGroups.Interval.values().length, 3, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(ROW_GAP, 0, 0, 0));
		for (LootGroups.Interval i : LootGroups.Interval.values())
		{
			JLabel chip = smallLabel(i.getLabel(), Color.WHITE);
			chip.setHorizontalAlignment(SwingConstants.CENTER);
			chip.setOpaque(true);
			chip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			chip.setToolTipText("One box per " + i.getLabel().toLowerCase(Locale.ENGLISH));
			chip.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (SwingUtilities.isLeftMouseButton(e) && interval != i)
					{
						interval = i;
						styleIntervalChips();
						onIntervalChange.accept(i);
						renderLoot();
					}
				}
			});
			intervalChips.put(i, chip);
			row.add(chip);
		}
		styleIntervalChips();
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setVisible(mode == LootGroups.Mode.TIME);
		return row;
	}

	/**
	 * The End trip button, only shown in the trip view.
	 */
	private JPanel buildTripRow()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(ROW_GAP, 0, 0, 0));
		endTripButton = smallLabel("End trip", Color.WHITE);
		endTripButton.setHorizontalAlignment(SwingConstants.CENTER);
		endTripButton.setOpaque(true);
		endTripButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && tripGoing())
				{
					onEndTrip.run();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				endTripHovered = true;
				styleEndTripButton();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				endTripHovered = false;
				styleEndTripButton();
			}
		});
		row.add(endTripButton, BorderLayout.CENTER);
		styleEndTripButton();
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setVisible(mode == LootGroups.Mode.TRIP);
		return row;
	}

	/**
	 * Whether there are drops since the last trip ended.
	 */
	private boolean tripGoing()
	{
		return trips.hasCurrent(lootRecords.isEmpty() ? null : lootRecords.get(0).getTimestamp());
	}

	private void styleEndTripButton()
	{
		boolean going = tripGoing();
		endTripButton.setForeground(going ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);
		endTripButton.setBackground(going && endTripHovered ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		endTripButton.setCursor(going ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
		endTripButton.setToolTipText(going ? "End this trip now so the next drop starts a new one. Banking does this too."
			: "No drops since the last trip ended");
		endTripButton.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(going ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_HOVER_COLOR),
			new EmptyBorder(2, 5, 2, 5)));
	}

	private void styleIntervalChips()
	{
		for (Map.Entry<LootGroups.Interval, JLabel> entry : intervalChips.entrySet())
		{
			boolean on = entry.getKey() == interval;
			JLabel chip = entry.getValue();
			chip.setForeground(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			chip.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_HOVER_COLOR),
				new EmptyBorder(1, 5, 1, 5)));
		}
	}

	private void styleModeButtons()
	{
		for (Map.Entry<LootGroups.Mode, JLabel> entry : modeButtons.entrySet())
		{
			JLabel button = entry.getValue();
			boolean selected = entry.getKey() == mode;
			button.setBackground(entry.getKey() == hoveredMode ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			button.setBorder(BorderFactory.createLineBorder(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR));
		}
	}

	private JPanel buildSellView()
	{
		JPanel view = new JPanel(new BorderLayout(0, ROW_GAP));
		view.setBackground(ColorScheme.DARK_GRAY_COLOR);

		styleSummary(sellSummary);
		view.add(sellSummary, BorderLayout.NORTH);

		sellList.setLayout(new BoxLayout(sellList, BoxLayout.Y_AXIS));
		sellList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		view.add(sellList, BorderLayout.CENTER);
		return view;
	}

	/**
	 * The summary shares its line with the view buttons, so it stays short and the tooltip says it in full.
	 */
	private void setSummary(String text, String tooltip)
	{
		lootSummary.setText(text);
		lootSummary.setToolTipText(tooltip);
	}

	private static void styleSummary(JLabel label)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
	}

	/**
	 * @param records    every loot record, newest first
	 * @param tiers      colors for valuable stacks, or {@link ValueTiers#NONE}
	 * @param itemValues    alch price and tradeability of every item in the records
	 * @param bestValueOnly show only the higher of the GE and HA value on each item line
	 * @param trips         where loot trips ended
	 */
	public void showLoot(List<LootRecord> records, long now, ValueTiers tiers, Map<Integer, ItemValue> itemValues,
		boolean bestValueOnly, TripLog trips)
	{
		lootRecords = records;
		lootNow = now;
		this.tiers = tiers;
		this.itemValues = itemValues;
		this.bestValueOnly = bestValueOnly;
		this.trips = trips;
		styleEndTripButton();
		renderLoot();
	}

	private void renderLoot()
	{
		lootList.removeAll();
		query = searchBar.getText().trim();
		List<LootRecord> records = LootSearch.filter(lootRecords, query, matchSource, matchLocation, matchItem);
		int totalCount = records.size();
		// While searching, the counts say "matching" so an empty list doesn't look like lost data
		String matching = query.isEmpty() ? "" : " matching";
		if (lootRecords.isEmpty())
		{
			setSummary("No loot yet", "No loot recorded yet. Kill something!");
		}
		else if (records.isEmpty())
		{
			setSummary("No matches", "Nothing matches \"" + query + "\" in the " + filterNames());
		}
		else if (mode == LootGroups.Mode.DEFAULT)
		{
			List<LootRecord> shown = records.subList(0, Math.min(MAX_LOOT_ROWS, totalCount));
			String drops = Formats.commas(totalCount) + matching + (totalCount == 1 ? " drop" : " drops");
			if (shown.size() < totalCount)
			{
				setSummary("Latest " + shown.size() + " of " + drops, "The latest " + shown.size() + " of " + drops + ", newest first");
			}
			else
			{
				setSummary(drops, drops + ", newest first");
			}
			for (LootRecord record : shown)
			{
				lootList.add(lootBox(record, lootNow));
				lootList.add(gap());
			}
		}
		else
		{
			ZoneId zone = ZoneId.systemDefault();
			List<LootGroups.Group> groups = LootGroups.group(records, mode, interval, trips, zone);
			if (mode == LootGroups.Mode.TASK)
			{
				// The task view leaves out drops that weren't for a task, so only count the ones it shows
				totalCount = 0;
				for (LootGroups.Group group : groups)
				{
					totalCount += group.getDrops();
				}
			}
			String noun = mode == LootGroups.Mode.SOURCE ? "source" : mode == LootGroups.Mode.TRIP ? "trip"
				: mode == LootGroups.Mode.TASK ? "task" : interval.getLabel().toLowerCase(Locale.ENGLISH);
			String count = Formats.commas(groups.size()) + " " + noun + (groups.size() == 1 ? "" : "s");
			String drops = Formats.commas(totalCount) + matching + (totalCount == 1 ? " drop" : " drops");
			if (groups.isEmpty())
			{
				setSummary("No slayer task drops" + (query.isEmpty() ? " yet" : matching),
					"Drops from kills that count for your slayer task show up here, one box per task");
			}
			else if (groups.size() > MAX_GROUPS)
			{
				setSummary(MAX_GROUPS + " of " + count + ", " + drops, "The latest " + MAX_GROUPS + " of " + count + ", from " + drops);
			}
			else
			{
				setSummary(count + ", " + drops, count + " from " + drops);
			}
			for (LootGroups.Group group : groups.subList(0, Math.min(MAX_GROUPS, groups.size())))
			{
				String title = mode == LootGroups.Mode.TIME ? LootGroups.periodTitle(group.getTitle(), interval, lootNow, zone)
					: mode == LootGroups.Mode.TRIP ? LootGroups.tripTitle(group, lootNow, zone)
					: mode == LootGroups.Mode.TASK ? group.getTaskName() : group.getTitle();
				lootList.add(groupBox(group, title));
				lootList.add(gap());
			}
		}
		lootList.revalidate();
		lootList.repaint();
	}

	/**
	 * @param items     sellable items, most valuable first
	 * @param bankSeen  false until the bank has been opened at least once on this account
	 */
	public void showSellList(List<ItemSummary> items, boolean bankSeen, boolean tagEnabled)
	{
		long total = 0;
		for (ItemSummary item : items)
		{
			total += item.getSellableValue();
		}

		if (!bankSeen)
		{
			sellSummary.setText(html("Open your bank once so Bank Auditor can see what you have."));
		}
		else if (items.isEmpty())
		{
			sellSummary.setText(html("Nothing to sell right now."));
		}
		else
		{
			String text = items.size() + (items.size() == 1 ? " item" : " items") + ", about " + Formats.compact(total) + " gp.";
			if (tagEnabled)
			{
				text += " Search tag:sellable in the bank to see just these.";
			}
			sellSummary.setText(html(text));
		}

		List<ItemSummary> shown = items.subList(0, Math.min(MAX_SELL_ROWS, items.size()));
		List<String> rows = new ArrayList<>(shown.size() + 1);
		for (ItemSummary item : shown)
		{
			rows.add(item.getId() + ":" + item.getSellable() + ":" + item.getOwned() + ":" + item.getPrice());
		}
		rows.add(String.valueOf(items.size()));
		if (rows.equals(shownSellRows))
		{
			// Same rows as last time, so skip rebuilding them and reloading every item image
			return;
		}
		shownSellRows = rows;

		sellList.removeAll();
		for (ItemSummary item : shown)
		{
			sellList.add(sellRow(item));
			sellList.add(gap());
		}
		if (items.size() > shown.size())
		{
			JLabel more = new JLabel("and " + (items.size() - shown.size()) + " more");
			styleSummary(more);
			sellList.add(more);
		}
		sellList.revalidate();
		sellList.repaint();
	}

	private JPanel lootBox(LootRecord record, long now)
	{
		String location = record.describeLocation();
		JLabel where = null;
		if (location != null)
		{
			where = smallLabel(location, ColorScheme.LIGHT_GRAY_COLOR);
			highlight(where, location, matchLocation);
			where.setToolTipText(record.getLocation().describeWithTile());
		}
		List<LootGroups.Line> lines = new ArrayList<>();
		for (LootItem item : record.getItems())
		{
			lines.addAll(LootGroups.Line.of(item));
		}
		lines.sort(LootGroups.Line.ORDER);
		// The title is also where an area named after the boss shows, like Vorkath at Vorkath
		String key = "drop:" + record.getTimestamp() + ":" + record.getSource();
		return dropBox(key, record.describeSource(), matchSource || matchLocation, icons.spriteFor(record.getSource(), record.getType()),
			Formats.ago(now, record.getTimestamp()), where, lines);
	}

	private JPanel groupBox(LootGroups.Group group, String title)
	{
		JLabel detail = smallLabel(group.getDetail(), ColorScheme.LIGHT_GRAY_COLOR);
		highlight(detail, group.getDetail(), matchSource || matchLocation);
		detail.setToolTipText(group.getDetail());
		// A trip shows how long it took, from its first drop to its last
		String when = mode == LootGroups.Mode.TIME ? null
			: mode == LootGroups.Mode.TRIP ? Formats.duration(group.getNewest() - group.getOldest())
			: Formats.ago(lootNow, group.getNewest());
		boolean titleSearched = mode == LootGroups.Mode.SOURCE && matchSource;
		int sprite = mode == LootGroups.Mode.TASK ? icons.taskSprite(group.getTaskName())
			: icons.spriteFor(group.getTopSource(), group.getTopSourceType());
		return dropBox(mode + ":" + group.getTitle(), title, titleSearched, sprite, when, detail, group.getLines());
	}

	/**
	 * A box with a header (source icon, title, time, the line under it and the item count) over a table of
	 * items with their icons and values, and a footer with the GE and HA totals.
	 *
	 * @param key stays the same across refreshes, to remember whether the box was expanded
	 */
	private JPanel dropBox(String key, String title, boolean titleSearched, int sprite, String when, JLabel subtitle,
		List<LootGroups.Line> lines)
	{
		long geTotal = 0;
		long haTotal = 0;
		// What the box is worth picks its value color: the better of GE and HA for each item
		long worth = 0;
		for (LootGroups.Line line : lines)
		{
			if (line.isLeft())
			{
				continue;
			}
			if (line.isAlched())
			{
				geTotal += line.getValue();
				haTotal += line.getValue();
				worth += line.getValue();
				continue;
			}
			long ha = ItemValue.haValue(line.getId(), line.getQuantity(), itemValues.get(line.getId()));
			geTotal += line.getValue();
			haTotal += ha;
			worth += Math.max(line.getValue(), ha);
		}
		Color worthColor = tiers.colorFor(worth);

		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// The header is taller, brighter and bold, with a line under it, so it never reads as another item row
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(HEADER_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BORDER_COLOR),
			new EmptyBorder(5, 5, 5, 6)));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(SOURCE_ICON_SIZE, SOURCE_ICON_SIZE));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		setSprite(icon, sprite);
		header.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
		text.setOpaque(false);

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		JLabel name = new JLabel(title);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		// Long names get cut with "..." instead of pushing the time out
		name.setToolTipText(title);
		highlight(name, title, titleSearched);
		top.add(name, BorderLayout.CENTER);
		if (when != null)
		{
			top.add(smallLabel(when, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		}
		text.add(top);

		JPanel bottom = new JPanel(new BorderLayout(4, 0));
		bottom.setOpaque(false);
		bottom.add(subtitle != null ? subtitle : new JLabel(), BorderLayout.CENTER);
		bottom.add(smallLabel(lines.size() == 1 ? "1 item" : lines.size() + " items", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		text.add(bottom);
		header.add(text, BorderLayout.CENTER);
		box.add(header, BorderLayout.NORTH);

		JPanel items = new JPanel();
		items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
		items.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// The value color runs down the items only, so the header stays clean
		items.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, worthColor != null ? worthColor : ColorScheme.DARKER_GRAY_COLOR));
		if (matchItem && !query.isEmpty())
		{
			// Matching items go first so a long combined box never hides them under "and N more"
			lines = new ArrayList<>(lines);
			lines.sort(Comparator.comparing((LootGroups.Line l) -> !LootSearch.matches(l.getName(), query)));
		}
		boolean open = expanded.contains(key);
		List<LootGroups.Line> shown = open ? lines : lines.subList(0, Math.min(MAX_GROUP_LINES, lines.size()));
		for (int i = 0; i < shown.size(); i++)
		{
			items.add(itemRow(shown.get(i), i % 2 == 1));
		}
		if (lines.size() > MAX_GROUP_LINES)
		{
			int more = lines.size() - MAX_GROUP_LINES;
			items.add(expandRow(key, open ? "Show fewer items"
				: "Show " + more + (more == 1 ? " more item" : " more items")));
		}
		box.add(items, BorderLayout.CENTER);
		box.add(totalsRow(geTotal, haTotal), BorderLayout.SOUTH);
		return box;
	}

	/**
	 * The footer: the GE and HA totals side by side in the middle, in a bigger font than the item values.
	 * It shares the header's color and sits outside the value edge, so the box reads as framed.
	 */
	private JPanel totalsRow(long geTotal, long haTotal)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(HEADER_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.BORDER_COLOR),
			new EmptyBorder(6, 6, 6, 6)));
		JPanel values = new JPanel(new FlowLayout(FlowLayout.CENTER, TOTAL_GAP, 0));
		values.setOpaque(false);
		values.add(totalLabel(GE_TOTAL_ICON, geTotal, ColorScheme.GRAND_EXCHANGE_PRICE, "GE total"));
		values.add(totalLabel(HA_TOTAL_ICON, haTotal, ColorScheme.GRAND_EXCHANGE_ALCH, "HA total"));
		row.add(values, BorderLayout.CENTER);
		return row;
	}

	/**
	 * The clickable line under a long box that shows or hides the rest of its items.
	 */
	private JPanel expandRow(String key, String label)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(3, 6, 3, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		JLabel text = smallLabel(label, ColorScheme.BRAND_ORANGE);
		text.setHorizontalAlignment(SwingConstants.CENTER);
		row.add(text, BorderLayout.CENTER);
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					if (!expanded.remove(key))
					{
						expanded.add(key);
					}
					renderLoot();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
		return row;
	}

	/**
	 * One table row: the item icon with its quantity, the name, and a column with the stack's Grand Exchange
	 * and high alchemy values, or only the higher one with best value only on. The name is colored by value
	 * tier. Alched items show the gold they gave, and items left on the floor are grayed out.
	 */
	private JPanel itemRow(LootGroups.Line line, boolean odd)
	{
		Color background = odd ? ROW_ALT_COLOR : ColorScheme.DARKER_GRAY_COLOR;
		boolean left = line.isLeft();
		boolean alched = line.isAlched();
		ItemValue value = itemValues.get(line.getId());
		long haTotal = ItemValue.haValue(line.getId(), line.getQuantity(), value);
		boolean showHaOnly = bestValueOnly && ItemValue.alchIsBetter(line.getValue(), haTotal, value);
		Color tier = left || alched ? null : tiers.colorFor(showHaOnly ? haTotal : line.getValue());
		String note = left ? "Not picked up"
			: alched ? "High alched for " + Formats.commas(line.getAlchPrice()) + " gp" + (line.getQuantity() > 1 ? " each" : "")
			: null;

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(background);
		row.setBorder(new EmptyBorder(1, 4, 1, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_ICON_HEIGHT + 2));

		JLabel icon = left ? new FadedLabel() : new JLabel();
		icon.setPreferredSize(new Dimension(ITEM_ICON_WIDTH, ITEM_ICON_HEIGHT));
		int quantity = (int) Math.min(Integer.MAX_VALUE, line.getQuantity());
		itemManager.getImage(line.getId(), quantity, line.getQuantity() > 1).addTo(icon);
		icon.setToolTipText(note);
		row.add(icon, BorderLayout.WEST);

		Color nameColor = left ? ColorScheme.MEDIUM_GRAY_COLOR : alched ? ColorScheme.GRAND_EXCHANGE_ALCH
			: tier != null ? tier : Color.WHITE;
		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = smallLabel(line.getName(), nameColor);
		highlight(name, line.getName(), matchItem);
		name.setToolTipText(note != null ? note : line.getName());
		text.add(name);
		// Left items only say so on hover; the gray already sets them apart
		String amount = "x " + Formats.commas(line.getQuantity()) + (alched ? " high alched" : "");
		JLabel amountLabel = smallLabel(amount, left ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		amountLabel.setToolTipText(note);
		text.add(amountLabel);
		row.add(text, BorderLayout.CENTER);

		// Coins are already gold, so their amount says it all
		if (line.getId() != ItemValue.COINS)
		{
			JPanel values = new JPanel(new GridLayout(2, 1));
			values.setOpaque(false);
			values.setPreferredSize(new Dimension(VALUE_COLUMN_WIDTH, ITEM_ICON_HEIGHT));
			// Untradeable items have neither value, so they keep both crossed out labels
			boolean single = bestValueOnly && !alched && (value == null || value.isTradeable());
			if (single)
			{
				values.setLayout(new BorderLayout());
				JLabel best = bestValue(line, value, haTotal, showHaOnly, tier);
				if (left)
				{
					best.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
					best.setToolTipText(note);
				}
				values.add(best, BorderLayout.CENTER);
			}
			else if (alched)
			{
				JLabel gold = valueLabel(HA_ICON, Formats.compact(line.getValue()), ColorScheme.GRAND_EXCHANGE_ALCH);
				gold.setToolTipText(priceTip("HA", line.getValue()));
				values.add(gold);
				values.add(new JLabel());
			}
			else
			{
				JLabel ge = geValue(line, value, tier);
				JLabel ha = haValue(line, value);
				if (left)
				{
					ge.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
					ha.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
					ge.setToolTipText(note);
					ha.setToolTipText(note);
				}
				values.add(ge);
				values.add(ha);
			}
			row.add(values, BorderLayout.EAST);
		}
		row.setToolTipText(note);
		return row;
	}

	private JLabel geValue(LootGroups.Line line, ItemValue value, Color tier)
	{
		if (value != null && !value.isTradeable())
		{
			JLabel label = valueLabel(GE_OFF_ICON, "<html><s>GE</s></html>", ColorScheme.MEDIUM_GRAY_COLOR);
			label.setToolTipText("Not tradeable");
			return label;
		}
		JLabel label = valueLabel(GE_ICON, Formats.compact(line.getValue()), tier != null ? tier : ColorScheme.GRAND_EXCHANGE_PRICE);
		label.setToolTipText(priceTip("GE", line.getValue()));
		return label;
	}

	/**
	 * The one value shown with best value only on. The hover has both, the shown one first.
	 */
	private JLabel bestValue(LootGroups.Line line, ItemValue value, long haTotal, boolean showHa, Color tier)
	{
		long geTotal = line.getValue();
		JLabel label = showHa
			? valueLabel(HA_ICON, Formats.compact(haTotal), ColorScheme.GRAND_EXCHANGE_ALCH)
			: valueLabel(GE_ICON, Formats.compact(geTotal), tier != null ? tier : ColorScheme.GRAND_EXCHANGE_PRICE);
		String ge = priceTip("GE", geTotal);
		String ha = value != null && value.isAlchable() ? priceTip("HA", haTotal) : "HA: not alchable";
		label.setToolTipText("<html>" + (showHa ? ha + "<br>" + ge : ge + "<br>" + ha) + "</html>");
		return label;
	}

	private JLabel haValue(LootGroups.Line line, ItemValue value)
	{
		if (value == null || !value.isAlchable())
		{
			JLabel label = valueLabel(HA_OFF_ICON, "<html><s>HA</s></html>", ColorScheme.MEDIUM_GRAY_COLOR);
			label.setToolTipText("Not alchable");
			return label;
		}
		long total = ItemValue.haValue(line.getId(), line.getQuantity(), value);
		JLabel label = valueLabel(HA_ICON, Formats.compact(total), ColorScheme.GRAND_EXCHANGE_ALCH);
		label.setToolTipText(priceTip("HA", total));
		return label;
	}

	/**
	 * The exact value for a hover, like "GE: 39,512 gp".
	 */
	private static String priceTip(String kind, long total)
	{
		return kind + ": " + Formats.commas(total) + " gp";
	}

	private static JLabel valueLabel(ImageIcon icon, String text, Color color)
	{
		JLabel label = smallLabel(text, color);
		label.setIcon(icon);
		label.setIconTextGap(3);
		label.setHorizontalAlignment(SwingConstants.LEFT);
		return label;
	}

	private static JLabel totalLabel(ImageIcon icon, long total, Color color, String kind)
	{
		JLabel label = new JLabel(Formats.compact(total));
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(color);
		label.setIcon(icon);
		label.setIconTextGap(4);
		label.setHorizontalAlignment(SwingConstants.LEFT);
		label.setToolTipText(priceTip(kind, total));
		return label;
	}

	/**
	 * The coin and high alchemy icons are shared by every value label, so they are drawn into once they
	 * load and every label picks them up on its next paint.
	 */
	private void loadValueIcons()
	{
		AsyncBufferedImage coins = itemManager.getImage(ItemID.COINS_10000);
		coins.onLoaded(() -> SwingUtilities.invokeLater(() -> fillValueIcons(coins, GE_ICON, GE_OFF_ICON, GE_TOTAL_ICON)));
		spriteManager.getSpriteAsync(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY, 0,
			image -> SwingUtilities.invokeLater(() -> fillValueIcons(image, HA_ICON, HA_OFF_ICON, HA_TOTAL_ICON)));
	}

	private void fillValueIcons(BufferedImage source, ImageIcon icon, ImageIcon off, ImageIcon total)
	{
		if (source == null)
		{
			return;
		}
		BufferedImage scaled = ImageUtil.resizeImage(source, VALUE_ICON_SIZE, VALUE_ICON_SIZE, true);
		draw(scaled, icon);
		draw(ImageUtil.alphaOffset(ImageUtil.grayscaleImage(scaled), 0.5f), off);
		draw(ImageUtil.resizeImage(source, TOTAL_ICON_SIZE, TOTAL_ICON_SIZE, true), total);
		repaint();
	}

	private static void draw(BufferedImage image, ImageIcon into)
	{
		BufferedImage target = (BufferedImage) into.getImage();
		Graphics2D g = (Graphics2D) target.getGraphics();
		g.setComposite(AlphaComposite.Src);
		int x = (target.getWidth() - image.getWidth()) / 2;
		int y = (target.getHeight() - image.getHeight()) / 2;
		g.drawImage(image, x, y, null);
		g.dispose();
	}

	private static ImageIcon blankIcon()
	{
		return blankIcon(VALUE_ICON_SIZE);
	}

	private static ImageIcon blankIcon(int size)
	{
		return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
	}

	/**
	 * Sprites load on the client thread, so the icon is filled in once it arrives. Scaled copies are kept
	 * because every refresh rebuilds the list.
	 */
	private void setSprite(JLabel label, int sprite)
	{
		ImageIcon cached = sprites.get(sprite);
		if (cached != null)
		{
			label.setIcon(cached);
			return;
		}
		spriteManager.getSpriteAsync(sprite, 0, image ->
		{
			if (image == null)
			{
				return;
			}
			BufferedImage scaled = image.getWidth() <= SOURCE_ICON_SIZE && image.getHeight() <= SOURCE_ICON_SIZE
				? image : ImageUtil.resizeImage(image, SOURCE_ICON_SIZE, SOURCE_ICON_SIZE, true);
			SwingUtilities.invokeLater(() ->
			{
				ImageIcon icon = sprites.computeIfAbsent(sprite, k -> new ImageIcon(scaled));
				label.setIcon(icon);
			});
		});
	}

	/**
	 * Marks the parts of the label that match the search, when that part of the search is on.
	 */
	private void highlight(JLabel label, String text, boolean searched)
	{
		String html = searched ? LootSearch.highlight(text, query, HIGHLIGHT_COLOR) : null;
		if (html != null)
		{
			label.setText(html);
		}
	}

	/**
	 * Paints its icon see-through, for items that were left on the floor.
	 */
	private static final class FadedLabel extends JLabel
	{
		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
			super.paintComponent(g2);
			g2.dispose();
		}
	}

	private static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private JPanel sellRow(ItemSummary item)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		itemManager.getImage(item.getId(), (int) Math.min(Integer.MAX_VALUE, item.getSellable()), item.getSellable() > 1).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel name = new JLabel(item.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		text.add(name);

		String detail = Formats.commas(item.getSellable());
		if (item.getSellable() < item.getOwned())
		{
			detail += " of " + Formats.commas(item.getOwned());
		}
		if (item.getSellableValue() > 0)
		{
			detail += ", " + Formats.compact(item.getSellableValue()) + " gp";
		}
		JLabel value = new JLabel(detail);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
		text.add(value);

		row.add(text, BorderLayout.CENTER);
		return row;
	}

	private static JPanel gap()
	{
		JPanel gap = new JPanel();
		gap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gap.setBorder(BorderFactory.createEmptyBorder(ROW_GAP / 2, 0, ROW_GAP / 2, 0));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_GAP));
		return gap;
	}

	private static String html(String text)
	{
		return "<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</html>";
	}
}
