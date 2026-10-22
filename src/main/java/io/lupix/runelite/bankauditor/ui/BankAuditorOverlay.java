package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.BankAuditorConfig;
import io.lupix.runelite.bankauditor.BankAuditorPlugin;
import io.lupix.runelite.bankauditor.HighlightStyle;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.util.Formats;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Draws on top of bank items, the inventory next to the bank, and the inventory next to the Grand Exchange:
 * the looted amount, the sellable highlight and the hover tooltip.
 */
public class BankAuditorOverlay extends WidgetItemOverlay
{
	private enum Place
	{
		BANK,
		BANK_INVENTORY,
		GE_INVENTORY,
		OTHER
	}

	private static final int OUTLINE_CACHE_SIZE = 512;
	private static final int CORNER_SIZE = 7;

	private final Client client;
	private final BankAuditorPlugin plugin;
	private final BankAuditorConfig config;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;

	// ItemManager keeps only 128 outlines, which a big bank goes through every frame
	private final Map<Long, BufferedImage> outlines = new LinkedHashMap<Long, BufferedImage>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, BufferedImage> eldest)
		{
			return size() > OUTLINE_CACHE_SIZE;
		}
	};
	private Color outlineColor;
	private volatile boolean clearRequested;

	@Inject
	BankAuditorOverlay(Client client, BankAuditorPlugin plugin, BankAuditorConfig config, ItemManager itemManager, TooltipManager tooltipManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		showOnBank();
		showOnInterfaces(InterfaceID.BANKSIDE, InterfaceID.GE_OFFERS_SIDE);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Place place = placeOf(widgetItem.getWidget());
		if (place == Place.OTHER || widgetItem.getQuantity() <= 0)
		{
			// Placeholders have a quantity of 0
			return;
		}

		ItemSummary item = plugin.getSummary(itemId);
		if (item == null)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		boolean highlight = item.isSellable()
			&& (place == Place.GE_INVENTORY ? config.highlightAtGrandExchange()
			: place == Place.BANK_INVENTORY ? config.highlightSellableInInventory()
			: config.highlightSellableInBank());
		if (highlight)
		{
			drawHighlight(graphics, widgetItem, bounds);
		}

		// The looted figure covers the bank and the inventory, but this number is about the stack under it
		long lootedHere = Math.min(item.getLooted(), widgetItem.getQuantity());
		if (place == Place.BANK && config.showLootedAmount() && lootedHere > 0)
		{
			drawLootedAmount(graphics, bounds, lootedHere);
		}

		if (config.showTooltips() && isHovered(widgetItem, bounds))
		{
			String text = plugin.getTooltip(item);
			if (text != null)
			{
				tooltipManager.add(new Tooltip(text));
			}
		}
	}

	private static Place placeOf(Widget widget)
	{
		if (widget == null)
		{
			return Place.OTHER;
		}

		int id = widget.getId();
		switch (WidgetUtil.componentToInterface(id))
		{
			case InterfaceID.BANKMAIN:
				// Only real bank slots, not the potion store or the equipment tab
				return isIn(widget, InterfaceID.Bankmain.ITEMS) ? Place.BANK : Place.OTHER;
			case InterfaceID.BANKSIDE:
				// Only the inventory, not the rune pouch, looting bag or other views that share the side panel
				return isIn(widget, InterfaceID.Bankside.ITEMS) ? Place.BANK_INVENTORY : Place.OTHER;
			case InterfaceID.GE_OFFERS_SIDE:
				return isIn(widget, InterfaceID.GeOffersSide.ITEMS) ? Place.GE_INVENTORY : Place.OTHER;
			default:
				return Place.OTHER;
		}
	}

	private static boolean isIn(Widget widget, int componentId)
	{
		return widget.getId() == componentId || widget.getParentId() == componentId;
	}

	private void drawHighlight(Graphics2D graphics, WidgetItem widgetItem, Rectangle bounds)
	{
		Color color = config.sellableColor();
		HighlightStyle style = config.highlightStyle();
		if (style == HighlightStyle.OUTLINE)
		{
			BufferedImage outline = outline(widgetItem.getId(), widgetItem.getQuantity(), color);
			if (outline != null)
			{
				graphics.drawImage(outline, bounds.x, bounds.y, null);
			}
		}
		else if (style == HighlightStyle.CORNER)
		{
			int x = bounds.x + bounds.width;
			int y = bounds.y;
			graphics.setColor(color);
			graphics.fillPolygon(new int[]{x - CORNER_SIZE, x, x}, new int[]{y, y, y + CORNER_SIZE}, 3);
		}
		else
		{
			int y = bounds.y + bounds.height + 1;
			graphics.setColor(color);
			graphics.drawLine(bounds.x, y, bounds.x + bounds.width, y);
		}
	}

	private BufferedImage outline(int itemId, int quantity, Color color)
	{
		if (clearRequested || !color.equals(outlineColor))
		{
			clearRequested = false;
			outlines.clear();
			outlineColor = color;
		}
		long key = ((long) itemId << 32) | (quantity & 0xffffffffL);
		BufferedImage image = outlines.get(key);
		if (image == null)
		{
			image = itemManager.getItemOutline(itemId, quantity, color);
			outlines.put(key, image);
		}
		return image;
	}

	private void drawLootedAmount(Graphics2D graphics, Rectangle bounds, long looted)
	{
		String text = Formats.compact(looted);
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + bounds.width - metrics.stringWidth(text);
		int y = bounds.y + bounds.height;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(config.lootedAmountColor());
		graphics.drawString(text, x, y);
	}

	private boolean isHovered(WidgetItem widgetItem, Rectangle bounds)
	{
		if (client.isMenuOpen())
		{
			return false;
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return false;
		}
		// Bank slots scrolled half out of view still have bounds under the mouse
		Widget parent = widgetItem.getWidget().getParent();
		return parent == null || parent.getBounds().contains(mouse.getX(), mouse.getY());
	}

	/**
	 * Safe to call from any thread. The cache is cleared on the next render.
	 */
	public void clearCaches()
	{
		clearRequested = true;
	}
}
