package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The drop popups showing in game: what each one holds and how far along its animation it is. A popup slides
 * in, stays up for a while, fades out, then closes the gap it leaves. More drops from the same source while it
 * is up are added to it instead of opening another one. Times are milliseconds from any clock.
 */
public final class DropPopups
{
	/**
	 * Popups up at once. Past this the oldest one starts fading right away.
	 */
	static final int MAX_SHOWN = 3;
	static final long ENTER_MILLIS = 250;
	static final long FADE_MILLIS = 600;
	static final long COLLAPSE_MILLIS = 200;
	static final long PULSE_MILLIS = 400;
	/**
	 * How long a popup stays up after the mouse leaves it.
	 */
	static final long HOVER_GRACE_MILLIS = 1000;

	public static final class Item
	{
		/**
		 * Most valuable first, by the value the popup shows.
		 */
		static Comparator<Item> order(boolean bestValueOnly)
		{
			return Comparator.comparingLong((Item i) -> i.shownValue(bestValueOnly)).reversed()
				.thenComparing(Comparator.comparingLong(Item::getQuantity).reversed());
		}

		private final int id;
		private final String name;
		private final ItemValue value;
		private long quantity;
		private long geValue;

		/**
		 * @param geValue GE value of the whole stack
		 * @param value   alch price and tradeability, or null when not known
		 */
		public Item(int id, String name, long quantity, long geValue, ItemValue value)
		{
			this.id = id;
			this.name = name;
			this.quantity = quantity;
			this.geValue = geValue;
			this.value = value;
		}

		public int getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public long getQuantity()
		{
			return quantity;
		}

		public long getGeValue()
		{
			return geValue;
		}

		public long getHaValue()
		{
			return ItemValue.haValue(id, quantity, value);
		}

		public boolean isTradeable()
		{
			return value == null || value.isTradeable();
		}

		/**
		 * Whether the popup shows the alch value instead of the GE value: only with best value only on, when
		 * alching pays more.
		 */
		public boolean showsHa(boolean bestValueOnly)
		{
			return bestValueOnly && ItemValue.alchIsBetter(geValue, getHaValue(), value);
		}

		public long shownValue(boolean bestValueOnly)
		{
			return showsHa(bestValueOnly) ? getHaValue() : geValue;
		}

		/**
		 * The better of the GE and alch value, which is what picks the popup's value color.
		 */
		long worth()
		{
			return Math.max(geValue, getHaValue());
		}

		private void add(Item more)
		{
			quantity += more.quantity;
			geValue += more.geValue;
		}
	}

	public static final class Popup
	{
		private final String title;
		private final LootSourceType type;
		private final int sprite;
		private final long createdAt;
		private final Map<Integer, Item> items = new LinkedHashMap<>();
		private long holdUntil;
		private long mergedAt = Long.MIN_VALUE;
		private int drops = 1;
		private int version;

		private Popup(String title, LootSourceType type, int sprite, long createdAt, long holdUntil)
		{
			this.title = title;
			this.type = type;
			this.sprite = sprite;
			this.createdAt = createdAt;
			this.holdUntil = holdUntil;
		}

		public String getTitle()
		{
			return title;
		}

		public int getSprite()
		{
			return sprite;
		}

		/**
		 * How many drops went into this popup.
		 */
		public int getDrops()
		{
			return drops;
		}

		/**
		 * Goes up every time items are added, so a renderer knows to redo what it worked out from them.
		 */
		public int getVersion()
		{
			return version;
		}

		public List<Item> items(boolean bestValueOnly)
		{
			List<Item> sorted = new ArrayList<>(items.values());
			sorted.sort(Item.order(bestValueOnly));
			return sorted;
		}

		public long geTotal()
		{
			long total = 0;
			for (Item item : items.values())
			{
				total += item.getGeValue();
			}
			return total;
		}

		public long haTotal()
		{
			long total = 0;
			for (Item item : items.values())
			{
				total += item.getHaValue();
			}
			return total;
		}

		/**
		 * What the popup is worth for its value color: the better of GE and HA for each item, like the side panel.
		 */
		public long worth()
		{
			long total = 0;
			for (Item item : items.values())
			{
				total += item.worth();
			}
			return total;
		}

		/**
		 * How opaque the popup is: fading in as it enters, fully shown while it is up, then fading out.
		 */
		public float alpha(long now)
		{
			float fade = 1f - progress(now - holdUntil, FADE_MILLIS);
			return Math.min(enter(now), fade);
		}

		/**
		 * How much of its full height the popup takes up, from 0 to 1. It grows as it enters and shrinks once
		 * it has faded out, so the popups around it slide instead of jumping.
		 */
		public float height(long now)
		{
			float collapse = progress(now - holdUntil - FADE_MILLIS, COLLAPSE_MILLIS);
			return enter(now) * (1f - easeOut(collapse));
		}

		/**
		 * 1 right after more items were added to it, down to 0, for a short flash.
		 */
		public float pulse(long now)
		{
			if (mergedAt == Long.MIN_VALUE)
			{
				return 0f;
			}
			return 1f - progress(now - mergedAt, PULSE_MILLIS);
		}

		boolean isFading(long now)
		{
			return now >= holdUntil;
		}

		boolean isGone(long now)
		{
			return now >= holdUntil + FADE_MILLIS + COLLAPSE_MILLIS;
		}

		private float enter(long now)
		{
			return easeOut(progress(now - createdAt, ENTER_MILLIS));
		}

		private void add(Collection<Item> more)
		{
			for (Item item : more)
			{
				Item existing = items.get(item.getId());
				if (existing == null)
				{
					items.put(item.getId(), new Item(item.id, item.name, item.quantity, item.geValue, item.value));
				}
				else
				{
					existing.add(item);
				}
			}
			version++;
		}
	}

	/**
	 * Oldest first.
	 */
	private final List<Popup> popups = new ArrayList<>();

	/**
	 * Shows a drop. If a popup for the same source is still up, the items go into that one.
	 *
	 * @param holdMillis how long the popup stays up before it fades
	 */
	public void add(String title, LootSourceType type, int sprite, Collection<Item> items, long now, long holdMillis)
	{
		if (items.isEmpty())
		{
			return;
		}
		prune(now);
		for (Popup popup : popups)
		{
			if (!popup.isFading(now) && popup.title.equals(title) && popup.type == type)
			{
				popup.add(items);
				popup.drops++;
				popup.mergedAt = now;
				popup.holdUntil = Math.max(popup.holdUntil, now + holdMillis);
				return;
			}
		}

		int up = 0;
		for (Popup popup : popups)
		{
			if (!popup.isFading(now))
			{
				up++;
			}
		}
		for (Iterator<Popup> it = popups.iterator(); up >= MAX_SHOWN && it.hasNext(); )
		{
			Popup oldest = it.next();
			if (!oldest.isFading(now))
			{
				oldest.holdUntil = now;
				up--;
			}
		}

		Popup popup = new Popup(title, type, sprite, now, now + holdMillis);
		popup.add(items);
		popups.add(popup);
	}

	/**
	 * Keeps a popup up while the mouse is on it. One that already started fading comes back.
	 */
	public void hover(Popup popup, long now)
	{
		if (!popup.isGone(now) && now < popup.holdUntil + FADE_MILLIS)
		{
			popup.holdUntil = Math.max(popup.holdUntil, now + HOVER_GRACE_MILLIS);
		}
	}

	/**
	 * The popups to draw, oldest first.
	 */
	public List<Popup> showing(long now)
	{
		prune(now);
		return Collections.unmodifiableList(new ArrayList<>(popups));
	}

	public boolean isEmpty()
	{
		return popups.isEmpty();
	}

	public void clear()
	{
		popups.clear();
	}

	private void prune(long now)
	{
		popups.removeIf(p -> p.isGone(now));
	}

	private static float progress(long elapsed, long duration)
	{
		if (elapsed <= 0)
		{
			return 0f;
		}
		return elapsed >= duration ? 1f : (float) elapsed / duration;
	}

	private static float easeOut(float t)
	{
		float rest = 1f - t;
		return 1f - rest * rest * rest;
	}
}
