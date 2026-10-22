package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.util.Formats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel with two tabs: recent loot (what, when, from what and where) and the sell list.
 * The loot tab can show each drop on its own or combine them by location, NPC or day.
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
	private static final int SOURCE_ICON_SIZE = 20;
	private static final int MODE_BUTTON_SIZE = 20;
	private static final int ITEM_ICON_WIDTH = 36;
	private static final int ITEM_ICON_HEIGHT = 32;
	private static final Color ROW_ALT_COLOR = new Color(35, 35, 35);
	private static final ImageIcon BIN_ICON = new ImageIcon(ImageUtil.loadImageResource(BankAuditorPanel.class, "/io/lupix/runelite/bankauditor/bin.png"));

	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final SourceIcons icons;
	private final Map<Integer, ImageIcon> sprites = new HashMap<>();
	private ValueTiers tiers = ValueTiers.NONE;
	private final Runnable onClearLoot;
	private final Consumer<LootGroups.Mode> onModeChange;
	private LootGroups.Mode mode;
	private LootGroups.Mode hoveredMode;
	private final Map<LootGroups.Mode, JLabel> modeButtons = new EnumMap<>(LootGroups.Mode.class);
	private List<LootRecord> lootRecords = Collections.emptyList();
	private long lootNow;

	private Runnable onActivate = () ->
	{
	};
	private volatile boolean active;

	private final JLabel lootSummary = new JLabel();
	private final JPanel lootList = new JPanel();
	private final JLabel sellSummary = new JLabel();
	private final JPanel sellList = new JPanel();
	private List<String> shownSellRows = Collections.emptyList();

	/**
	 * @param mode         the loot view to start on
	 * @param onModeChange told when the user picks another loot view, so it can be remembered
	 */
	public BankAuditorPanel(ItemManager itemManager, SpriteManager spriteManager, SourceIcons icons, Runnable onClearLoot,
		LootGroups.Mode mode, Consumer<LootGroups.Mode> onModeChange)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.icons = icons;
		this.onClearLoot = onClearLoot;
		this.mode = mode;
		this.onModeChange = onModeChange;

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

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		styleSummary(lootSummary);
		top.add(lootSummary, BorderLayout.CENTER);
		top.add(buildModeButtons(), BorderLayout.EAST);
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
	 * @param records every loot record, newest first
	 * @param tiers   colors for valuable stacks, or {@link ValueTiers#NONE}
	 */
	public void showLoot(List<LootRecord> records, long now, ValueTiers tiers)
	{
		lootRecords = records;
		lootNow = now;
		this.tiers = tiers;
		renderLoot();
	}

	private void renderLoot()
	{
		lootList.removeAll();
		int totalCount = lootRecords.size();
		if (lootRecords.isEmpty())
		{
			setSummary("No loot yet", "No loot recorded yet. Kill something!");
		}
		else if (mode == LootGroups.Mode.DEFAULT)
		{
			List<LootRecord> shown = lootRecords.subList(0, Math.min(MAX_LOOT_ROWS, totalCount));
			String drops = Formats.commas(totalCount) + (totalCount == 1 ? " drop" : " drops");
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
			List<LootGroups.Group> groups = LootGroups.group(lootRecords, mode, zone);
			String noun = mode == LootGroups.Mode.LOCATION ? "location" : mode == LootGroups.Mode.SOURCE ? "source" : "day";
			String count = Formats.commas(groups.size()) + " " + noun + (groups.size() == 1 ? "" : "s");
			String drops = Formats.commas(totalCount) + (totalCount == 1 ? " drop" : " drops");
			if (groups.size() > MAX_GROUPS)
			{
				setSummary(MAX_GROUPS + " of " + count + ", " + drops, "The latest " + MAX_GROUPS + " of " + count + ", from " + drops);
			}
			else
			{
				setSummary(count + ", " + drops, count + " from " + drops);
			}
			for (LootGroups.Group group : groups.subList(0, Math.min(MAX_GROUPS, groups.size())))
			{
				String title = mode == LootGroups.Mode.DAY ? LootGroups.dayTitle(group.getTitle(), lootNow, zone) : group.getTitle();
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
			where.setToolTipText(record.getLocation().describeWithTile());
		}
		List<LootGroups.Line> lines = new ArrayList<>();
		for (LootItem item : record.getItems())
		{
			lines.add(LootGroups.Line.of(item));
		}
		lines.sort(Comparator.comparingLong(LootGroups.Line::getValue).reversed());
		return dropBox(record.describeSource(), icons.spriteFor(record.getSource(), record.getType()),
			Formats.ago(now, record.getTimestamp()), where, lines, record.getTotalValue());
	}

	private JPanel groupBox(LootGroups.Group group, String title)
	{
		JLabel detail = smallLabel(group.getDetail(), ColorScheme.LIGHT_GRAY_COLOR);
		detail.setToolTipText(group.getDetail());
		String when = mode == LootGroups.Mode.DAY ? null : Formats.ago(lootNow, group.getNewest());
		return dropBox(title, icons.spriteFor(group.getTopSource(), group.getTopSourceType()), when, detail,
			group.getLines(), group.getTotalValue());
	}

	/**
	 * A box with a header (source icon, title, time and one line under it) over a table of items
	 * with their icons, and the total value at the bottom.
	 */
	private JPanel dropBox(String title, int sprite, String when, JLabel subtitle, List<LootGroups.Line> lines, long totalValue)
	{
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		Color totalColor = tiers.colorFor(totalValue);
		box.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, totalColor != null ? totalColor : ColorScheme.DARKER_GRAY_HOVER_COLOR));

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		header.setBorder(new EmptyBorder(4, 6, 4, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(SOURCE_ICON_SIZE, SOURCE_ICON_SIZE));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		setSprite(icon, sprite);
		header.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new BorderLayout(4, 0));
		text.setOpaque(false);
		JLabel name = smallLabel(title, Color.WHITE);
		// Centre takes whatever width the time leaves, so long names get cut with "..." instead of overlapping
		name.setToolTipText(title);
		text.add(name, BorderLayout.CENTER);
		if (when != null)
		{
			JLabel time = smallLabel(when, ColorScheme.LIGHT_GRAY_COLOR);
			text.add(time, BorderLayout.EAST);
		}
		if (subtitle != null)
		{
			text.add(subtitle, BorderLayout.SOUTH);
		}
		header.add(text, BorderLayout.CENTER);
		box.add(header, BorderLayout.NORTH);

		JPanel items = new JPanel();
		items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
		items.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		List<LootGroups.Line> shown = lines.subList(0, Math.min(MAX_GROUP_LINES, lines.size()));
		for (int i = 0; i < shown.size(); i++)
		{
			items.add(itemRow(shown.get(i), i % 2 == 1));
		}
		if (lines.size() > shown.size())
		{
			int more = lines.size() - shown.size();
			JPanel moreRow = new JPanel(new BorderLayout());
			moreRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			moreRow.setBorder(new EmptyBorder(2, 6, 2, 6));
			moreRow.add(smallLabel("and " + more + (more == 1 ? " more item" : " more items"), ColorScheme.LIGHT_GRAY_COLOR));
			items.add(moreRow);
		}

		JPanel footer = new JPanel(new BorderLayout());
		footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		footer.setBorder(new EmptyBorder(3, 6, 4, 6));
		String count = lines.size() == 1 ? "1 item" : lines.size() + " items";
		footer.add(smallLabel(count, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		if (totalValue > 0)
		{
			JLabel total = smallLabel(Formats.compact(totalValue) + " gp", totalColor != null ? totalColor : ColorScheme.GRAND_EXCHANGE_PRICE);
			total.setToolTipText(Formats.commas(totalValue) + " gp");
			footer.add(total, BorderLayout.EAST);
		}
		items.add(footer);

		box.add(items, BorderLayout.CENTER);
		return box;
	}

	/**
	 * One table row: the item icon with its quantity, the name, and the stack's value, colored by tier.
	 */
	private JPanel itemRow(LootGroups.Line line, boolean odd)
	{
		Color background = odd ? ROW_ALT_COLOR : ColorScheme.DARKER_GRAY_COLOR;
		Color tier = tiers.colorFor(line.getValue());

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(background);
		row.setBorder(new EmptyBorder(1, 4, 1, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_ICON_HEIGHT + 2));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(ITEM_ICON_WIDTH, ITEM_ICON_HEIGHT));
		int quantity = (int) Math.min(Integer.MAX_VALUE, line.getQuantity());
		itemManager.getImage(line.getId(), quantity, line.getQuantity() > 1).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		text.add(smallLabel(line.getName(), tier != null ? tier : Color.WHITE));
		String detail = "x " + Formats.commas(line.getQuantity());
		if (line.getValue() > 0)
		{
			detail += ", " + Formats.compact(line.getValue()) + " gp";
		}
		text.add(smallLabel(detail, tier != null ? tier : ColorScheme.LIGHT_GRAY_COLOR));
		row.add(text, BorderLayout.CENTER);

		String tooltip = Formats.commas(line.getQuantity()) + " x " + line.getName();
		if (line.getValue() > 0)
		{
			tooltip += ", " + Formats.commas(line.getValue()) + " gp";
		}
		row.setToolTipText(tooltip);
		return row;
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
			BufferedImage scaled = ImageUtil.resizeImage(image, SOURCE_ICON_SIZE, SOURCE_ICON_SIZE, true);
			SwingUtilities.invokeLater(() ->
			{
				ImageIcon icon = sprites.computeIfAbsent(sprite, k -> new ImageIcon(scaled));
				label.setIcon(icon);
			});
		});
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
