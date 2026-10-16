package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootItem;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.util.Formats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Side panel with two tabs: recent loot (what, when, from what and where) and the sell list.
 * All methods must be called on the Swing event thread.
 */
public class BankAuditorPanel extends PluginPanel
{
	private static final int ROW_GAP = 6;

	private final ItemManager itemManager;
	private final Runnable onClearLoot;

	private Runnable onActivate = () ->
	{
	};
	private volatile boolean active;

	private final JLabel lootSummary = new JLabel();
	private final JPanel lootList = new JPanel();
	private final JLabel sellSummary = new JLabel();
	private final JPanel sellList = new JPanel();

	public BankAuditorPanel(ItemManager itemManager, Runnable onClearLoot)
	{
		this.itemManager = itemManager;
		this.onClearLoot = onClearLoot;

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

		styleSummary(lootSummary);
		view.add(lootSummary, BorderLayout.NORTH);

		lootList.setLayout(new BoxLayout(lootList, BoxLayout.Y_AXIS));
		lootList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		view.add(lootList, BorderLayout.CENTER);

		JButton clear = new JButton("Clear loot data");
		clear.setToolTipText("Forget all loot history and stop counting current stacks as loot");
		clear.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this,
				"This forgets your loot history and stops counting any current stacks as loot. Continue?",
				"Clear loot data", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.YES_OPTION)
			{
				onClearLoot.run();
			}
		});
		view.add(clear, BorderLayout.SOUTH);
		return view;
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

	private static void styleSummary(JLabel label)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
	}

	/**
	 * @param records     newest first, already trimmed to what should be shown
	 * @param totalCount  how many records exist in total
	 */
	public void showLoot(List<LootRecord> records, int totalCount, long now)
	{
		lootList.removeAll();
		if (records.isEmpty())
		{
			lootSummary.setText(html("No loot recorded yet. Kill something!"));
		}
		else if (records.size() < totalCount)
		{
			lootSummary.setText(html("Latest " + records.size() + " of " + Formats.commas(totalCount) + " loot records"));
		}
		else
		{
			lootSummary.setText(html(Formats.commas(totalCount) + (totalCount == 1 ? " loot record" : " loot records")));
		}

		for (LootRecord record : records)
		{
			lootList.add(lootBox(record, now));
			lootList.add(gap());
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
		sellList.removeAll();
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

		for (ItemSummary item : items)
		{
			sellList.add(sellRow(item));
			sellList.add(gap());
		}
		sellList.revalidate();
		sellList.repaint();
	}

	private JPanel lootBox(LootRecord record, long now)
	{
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		header.setBorder(new EmptyBorder(4, 6, 4, 6));

		JLabel source = new JLabel(record.describeSource());
		source.setFont(FontManager.getRunescapeSmallFont());
		source.setForeground(Color.WHITE);
		header.add(source, BorderLayout.WEST);

		JLabel when = new JLabel(Formats.ago(now, record.getTimestamp()));
		when.setFont(FontManager.getRunescapeSmallFont());
		when.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(when, BorderLayout.EAST);

		String location = record.describeLocation();
		if (location != null)
		{
			JLabel where = new JLabel(location);
			where.setFont(FontManager.getRunescapeSmallFont());
			where.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			where.setToolTipText(record.getLocation().describeWithTile());
			header.add(where, BorderLayout.SOUTH);
		}
		box.add(header, BorderLayout.NORTH);

		JPanel items = new JPanel();
		items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
		items.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		items.setBorder(new EmptyBorder(4, 6, 4, 6));
		for (LootItem item : record.getItems())
		{
			items.add(lineRow(Formats.commas(item.getQuantity()) + " x " + item.getName(),
				item.getValue() > 0 ? Formats.compact(item.getValue()) : ""));
		}
		if (record.getTotalValue() > 0)
		{
			JLabel total = new JLabel("Total " + Formats.compact(record.getTotalValue()) + " gp");
			total.setFont(FontManager.getRunescapeSmallFont());
			total.setForeground(ColorScheme.GRAND_EXCHANGE_PRICE);
			total.setHorizontalAlignment(SwingConstants.RIGHT);
			JPanel totalRow = new JPanel(new BorderLayout());
			totalRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			totalRow.add(total, BorderLayout.EAST);
			items.add(totalRow);
		}
		box.add(items, BorderLayout.CENTER);
		return box;
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

	private static JPanel lineRow(String left, String right)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel name = new JLabel(left);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.TEXT_COLOR);
		row.add(name, BorderLayout.CENTER);

		JLabel value = new JLabel(right);
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(value, BorderLayout.EAST);
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
