package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.BankAuditorConfig;
import io.lupix.runelite.bankauditor.BankAuditorPlugin;
import io.lupix.runelite.bankauditor.loot.LootSourceType;
import io.lupix.runelite.bankauditor.util.Formats;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * A small copy of a side panel loot box that pops up in game when loot comes in: the source, the most valuable
 * items with what they are worth, and the GE and HA totals. Popups stack, newest at the bottom, slide in and
 * fade out. Alt-drag moves them like any overlay.
 */
public class DropPopupOverlay extends Overlay
{
	private static final int WIDTH = 200;
	private static final int HEADER_HEIGHT = 24;
	private static final int ROW_HEIGHT = 32;
	private static final int MORE_HEIGHT = 16;
	private static final int FOOTER_HEIGHT = 22;
	private static final int GAP = 4;
	private static final int SOURCE_ICON_SIZE = 18;
	private static final int ITEM_ICON_WIDTH = 36;
	private static final int VALUE_ICON_SIZE = 12;
	private static final int TOTAL_ICON_SIZE = 16;
	private static final int TOTAL_GAP = 14;
	private static final Color HEADER_COLOR = new Color(58, 54, 48);
	private static final Color PULSE_COLOR = new Color(96, 88, 74);
	private static final Color ROW_ALT_COLOR = new Color(35, 35, 35);
	private static final Color OUTLINE_COLOR = new Color(16, 16, 16);

	private final Client client;
	private final BankAuditorPlugin plugin;
	private final BankAuditorConfig config;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final DropPopups popups = new DropPopups();
	private final Map<Integer, BufferedImage> sourceIcons = new HashMap<>();
	private BufferedImage geIcon;
	private BufferedImage haIcon;
	private BufferedImage geTotalIcon;
	private BufferedImage haTotalIcon;

	@Inject
	DropPopupOverlay(Client client, BankAuditorPlugin plugin, BankAuditorConfig config, ItemManager itemManager, SpriteManager spriteManager)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Dismiss", "Drop popups", e -> popups.clear());
	}

	/**
	 * Shows a drop, or adds it to the popup for the same source if that one is still up. Client thread only.
	 */
	public void show(String title, LootSourceType type, int sprite, Collection<DropPopups.Item> items)
	{
		popups.add(title, type, sprite, items, System.currentTimeMillis(), config.popupSeconds() * 1000L);
	}

	public void clear()
	{
		popups.clear();
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.dropPopups())
		{
			popups.clear();
			return null;
		}
		if (popups.isEmpty())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		List<DropPopups.Popup> showing = popups.showing(now);
		if (showing.isEmpty())
		{
			return null;
		}
		loadValueIcons();
		boolean bestValueOnly = config.bestValueOnly();
		int maxItems = Math.max(1, config.popupItems());
		ValueTiers tiers = plugin.valueTiers();

		// The overlay is placed using the size it had last frame, and the popups change size every frame while
		// they slide. So everything is laid out from the edge that stays put: the bottom when the overlay sits
		// above the chat box or in a bottom corner, the top anywhere else. Newest is always at the bottom.
		Rectangle bounds = getBounds();
		boolean growsUp = growsUp();
		// Right after being empty the overlay was placed for a size of zero, so it would be drawn in the wrong
		// spot for a frame. The popup is still see-through at that point, so skipping the frame isn't noticed.
		boolean draw = bounds.width > 0;
		int edge = growsUp ? bounds.height : 0;

		// Where the mouse is, relative to this overlay, to keep a hovered popup up
		Point mouse = client.getMouseCanvasPosition();
		int mouseX = mouse == null ? -1 : mouse.getX() - bounds.x;
		int mouseY = mouse == null ? -1 : mouse.getY() - bounds.y;

		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		Composite composite = g.getComposite();
		Shape clip = g.getClip();
		int used = 0;
		for (int n = 0; n < showing.size(); n++)
		{
			DropPopups.Popup popup = showing.get(growsUp ? showing.size() - 1 - n : n);
			List<DropPopups.Item> items = popup.items(bestValueOnly);
			int shown = Math.min(maxItems, items.size());
			int more = items.size() - shown;
			int full = HEADER_HEIGHT + shown * ROW_HEIGHT + (more > 0 ? MORE_HEIGHT : 0) + FOOTER_HEIGHT;
			float height = popup.height(now);
			int slot = Math.round(full * height);
			if (slot <= 0)
			{
				continue;
			}
			if (used > 0)
			{
				used += Math.round(GAP * height);
			}
			int slotTop = growsUp ? edge - used - slot : used;
			used += slot;
			if (mouseX >= 0 && mouseX < WIDTH && mouseY >= slotTop && mouseY < slotTop + slot)
			{
				popups.hover(popup, now);
			}
			if (!draw)
			{
				continue;
			}

			// A popup comes out of the edge it grows from: it rises out of the chat box, or drops down below the
			// popup above it
			int top = growsUp ? slotTop : slotTop + slot - full;
			g.setClip(clip);
			g.clipRect(0, slotTop, WIDTH, slot);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, popup.alpha(now)))));
			drawPopup(g, popup, items.subList(0, shown), more, top, full, now, bestValueOnly, tiers);
		}
		g.setClip(clip);
		g.setComposite(composite);
		return used > 0 ? new Dimension(WIDTH, used) : null;
	}

	/**
	 * Whether the overlay keeps its bottom edge in place and grows upwards, which the bottom snap corners do.
	 * Once dragged somewhere else it keeps its top left corner in place.
	 */
	private boolean growsUp()
	{
		if (getPreferredLocation() != null)
		{
			return false;
		}
		OverlayPosition position = getPreferredPosition() != null ? getPreferredPosition() : getPosition();
		return position == OverlayPosition.ABOVE_CHATBOX_RIGHT || position == OverlayPosition.BOTTOM_LEFT
			|| position == OverlayPosition.BOTTOM_RIGHT;
	}

	private void drawPopup(Graphics2D g, DropPopups.Popup popup, List<DropPopups.Item> items, int more, int top, int full, long now,
		boolean bestValueOnly, ValueTiers tiers)
	{
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(0, top, WIDTH, full);

		// Header: source icon and name, and how many drops went in when there is more than one
		g.setColor(blend(HEADER_COLOR, PULSE_COLOR, popup.pulse(now)));
		g.fillRect(0, top, WIDTH, HEADER_HEIGHT);
		g.setColor(ColorScheme.BORDER_COLOR);
		g.fillRect(0, top + HEADER_HEIGHT - 1, WIDTH, 1);
		BufferedImage icon = sourceIcon(popup.getSprite());
		if (icon != null)
		{
			g.drawImage(icon, 5 + (SOURCE_ICON_SIZE - icon.getWidth()) / 2, top + (HEADER_HEIGHT - icon.getHeight()) / 2, null);
		}
		int titleX = 8 + SOURCE_ICON_SIZE;
		int titleRight = WIDTH - 6;
		if (popup.getDrops() > 1)
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			String drops = "x" + popup.getDrops();
			int width = g.getFontMetrics().stringWidth(drops);
			text(g, drops, WIDTH - 6 - width, middle(g, top, HEADER_HEIGHT), ColorScheme.LIGHT_GRAY_COLOR);
			titleRight -= width + 6;
		}
		g.setFont(FontManager.getRunescapeBoldFont());
		text(g, fit(g, popup.getTitle(), titleRight - titleX), titleX, middle(g, top, HEADER_HEIGHT), Color.WHITE);

		// Items, with the value color down their left edge like the side panel
		int rowsTop = top + HEADER_HEIGHT;
		g.setFont(FontManager.getRunescapeSmallFont());
		for (int i = 0; i < items.size(); i++)
		{
			drawItem(g, items.get(i), rowsTop + i * ROW_HEIGHT, i % 2 == 1, bestValueOnly, tiers);
		}
		int rowsBottom = rowsTop + items.size() * ROW_HEIGHT;
		if (more > 0)
		{
			String label = "+ " + more + (more == 1 ? " more item" : " more items");
			int width = g.getFontMetrics().stringWidth(label);
			text(g, label, (WIDTH - width) / 2, middle(g, rowsBottom, MORE_HEIGHT), ColorScheme.LIGHT_GRAY_COLOR);
			rowsBottom += MORE_HEIGHT;
		}
		Color worth = tiers.colorFor(popup.worth());
		if (worth != null)
		{
			g.setColor(worth);
			g.fillRect(0, rowsTop, 2, rowsBottom - rowsTop);
		}

		// Footer: GE and HA totals side by side in the middle
		g.setColor(HEADER_COLOR);
		g.fillRect(0, rowsBottom, WIDTH, FOOTER_HEIGHT);
		g.setColor(ColorScheme.BORDER_COLOR);
		g.fillRect(0, rowsBottom, WIDTH, 1);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics metrics = g.getFontMetrics();
		String ge = Formats.compact(popup.geTotal());
		String ha = Formats.compact(popup.haTotal());
		int geWidth = TOTAL_ICON_SIZE + 4 + metrics.stringWidth(ge);
		int haWidth = TOTAL_ICON_SIZE + 4 + metrics.stringWidth(ha);
		int x = (WIDTH - geWidth - TOTAL_GAP - haWidth) / 2;
		int textY = middle(g, rowsBottom + 1, FOOTER_HEIGHT - 1);
		int iconY = rowsBottom + 1 + (FOOTER_HEIGHT - 1 - TOTAL_ICON_SIZE) / 2;
		drawIcon(g, geTotalIcon, x, iconY);
		text(g, ge, x + TOTAL_ICON_SIZE + 4, textY, ColorScheme.GRAND_EXCHANGE_PRICE);
		x += geWidth + TOTAL_GAP;
		drawIcon(g, haTotalIcon, x, iconY);
		text(g, ha, x + TOTAL_ICON_SIZE + 4, textY, ColorScheme.GRAND_EXCHANGE_ALCH);

		g.setColor(OUTLINE_COLOR);
		g.drawRect(0, top, WIDTH - 1, full - 1);
	}

	private void drawItem(Graphics2D g, DropPopups.Item item, int rowTop, boolean odd, boolean bestValueOnly, ValueTiers tiers)
	{
		if (odd)
		{
			g.setColor(ROW_ALT_COLOR);
			g.fillRect(0, rowTop, WIDTH, ROW_HEIGHT);
		}
		int quantity = (int) Math.min(Integer.MAX_VALUE, item.getQuantity());
		g.drawImage(itemManager.getImage(item.getId(), quantity, item.getQuantity() > 1), 4, rowTop, null);

		boolean showHa = item.showsHa(bestValueOnly);
		long value = item.shownValue(bestValueOnly);
		Color tier = tiers.colorFor(value);
		int nameX = 6 + ITEM_ICON_WIDTH;
		int right = WIDTH - 6;
		int textY = middle(g, rowTop, ROW_HEIGHT);
		// Coins are already gold and untradeable items have no price, so neither shows a value
		if (item.getId() != ItemValue.COINS && item.isTradeable())
		{
			String text = Formats.compact(value);
			int width = g.getFontMetrics().stringWidth(text);
			int valueX = right - width;
			text(g, text, valueX, textY, showHa ? ColorScheme.GRAND_EXCHANGE_ALCH : tier != null ? tier : ColorScheme.GRAND_EXCHANGE_PRICE);
			int iconX = valueX - 3 - VALUE_ICON_SIZE;
			drawIcon(g, showHa ? haIcon : geIcon, iconX, rowTop + (ROW_HEIGHT - VALUE_ICON_SIZE) / 2);
			right = iconX - 4;
		}
		text(g, fit(g, item.getName(), right - nameX), nameX, textY, tier != null ? tier : Color.WHITE);
	}

	/**
	 * Same icons as the side panel's values, loaded on first use.
	 */
	private void loadValueIcons()
	{
		if (geIcon == null)
		{
			BufferedImage coins = itemManager.getImage(ItemID.COINS_10000);
			// Blank until it loads, so only keep it once something is drawn in it
			if (coins != null && !isBlank(coins))
			{
				geIcon = ImageUtil.resizeImage(coins, VALUE_ICON_SIZE, VALUE_ICON_SIZE, true);
				geTotalIcon = ImageUtil.resizeImage(coins, TOTAL_ICON_SIZE, TOTAL_ICON_SIZE, true);
			}
		}
		if (haIcon == null)
		{
			BufferedImage alch = spriteManager.getSprite(SpriteID.Magicon.HIGH_LEVEL_ALCHEMY, 0);
			if (alch != null)
			{
				haIcon = ImageUtil.resizeImage(alch, VALUE_ICON_SIZE, VALUE_ICON_SIZE, true);
				haTotalIcon = ImageUtil.resizeImage(alch, TOTAL_ICON_SIZE, TOTAL_ICON_SIZE, true);
			}
		}
	}

	private BufferedImage sourceIcon(int sprite)
	{
		BufferedImage cached = sourceIcons.get(sprite);
		if (cached != null)
		{
			return cached;
		}
		BufferedImage image = spriteManager.getSprite(sprite, 0);
		if (image == null)
		{
			return null;
		}
		BufferedImage scaled = image.getWidth() <= SOURCE_ICON_SIZE && image.getHeight() <= SOURCE_ICON_SIZE
			? image : ImageUtil.resizeImage(image, SOURCE_ICON_SIZE, SOURCE_ICON_SIZE, true);
		sourceIcons.put(sprite, scaled);
		return scaled;
	}

	private static boolean isBlank(BufferedImage image)
	{
		for (int x = 0; x < image.getWidth(); x += 2)
		{
			for (int y = 0; y < image.getHeight(); y += 2)
			{
				if ((image.getRGB(x, y) >>> 24) != 0)
				{
					return false;
				}
			}
		}
		return true;
	}

	private static void drawIcon(Graphics2D g, BufferedImage icon, int x, int y)
	{
		if (icon != null)
		{
			g.drawImage(icon, x, y, null);
		}
	}

	/**
	 * Text with the one pixel shadow the game uses.
	 */
	private static void text(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/**
	 * Baseline that centers the current font in a strip.
	 */
	private static int middle(Graphics2D g, int top, int height)
	{
		FontMetrics metrics = g.getFontMetrics();
		return top + (height - metrics.getAscent() - metrics.getDescent()) / 2 + metrics.getAscent();
	}

	/**
	 * Cuts text that is too wide with "...".
	 */
	private static String fit(Graphics2D g, String text, int width)
	{
		FontMetrics metrics = g.getFontMetrics();
		if (metrics.stringWidth(text) <= width)
		{
			return text;
		}
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end) + "...") > width)
		{
			end--;
		}
		return text.substring(0, end).trim() + "...";
	}

	private static Color blend(Color from, Color to, float amount)
	{
		if (amount <= 0f)
		{
			return from;
		}
		float keep = 1f - amount;
		return new Color(
			Math.round(from.getRed() * keep + to.getRed() * amount),
			Math.round(from.getGreen() * keep + to.getGreen() * amount),
			Math.round(from.getBlue() * keep + to.getBlue() * amount));
	}
}
