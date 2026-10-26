package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootSourceType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DropPopupsTest
{
	private static final long HOLD = 6_000;
	private static final int SPRITE = 1;
	private static final ItemValue ALCHABLE = new ItemValue(100, true);

	private static DropPopups.Item item(int id, long quantity, long geValue)
	{
		return new DropPopups.Item(id, "Item " + id, quantity, geValue, ALCHABLE);
	}

	private static void drop(DropPopups popups, String source, long now, DropPopups.Item... items)
	{
		popups.add(source, LootSourceType.NPC, SPRITE, Arrays.asList(items), now, HOLD);
	}

	@Test
	public void dropsFromTheSameSourceGoIntoOnePopup()
	{
		DropPopups popups = new DropPopups();
		drop(popups, "Goblin", 0, item(1, 2, 20));
		drop(popups, "Goblin", 2_000, item(1, 3, 30), item(2, 1, 5));

		List<DropPopups.Popup> showing = popups.showing(2_000);
		assertEquals(1, showing.size());
		DropPopups.Popup popup = showing.get(0);
		assertEquals(2, popup.getDrops());
		assertEquals(55, popup.geTotal());
		List<DropPopups.Item> items = popup.items(false);
		assertEquals(5, items.get(0).getQuantity());
		assertEquals(2, items.size());
		// The merge restarts the time it stays up
		assertEquals(1f, popup.alpha(2_000 + HOLD - 1), 0f);
		assertTrue(popup.pulse(2_000) > 0.99f);
		assertEquals(0f, popup.pulse(2_000 + DropPopups.PULSE_MILLIS), 0f);
	}

	@Test
	public void popupSlidesInStaysThenFadesAndCloses()
	{
		DropPopups popups = new DropPopups();
		drop(popups, "Goblin", 1_000, item(1, 1, 10));
		DropPopups.Popup popup = popups.showing(1_000).get(0);

		assertEquals(0f, popup.alpha(1_000), 0f);
		assertEquals(0f, popup.height(1_000), 0f);
		assertEquals(1f, popup.alpha(1_000 + DropPopups.ENTER_MILLIS), 0f);
		assertEquals(1f, popup.height(1_000 + DropPopups.ENTER_MILLIS), 0f);

		long fadeStart = 1_000 + HOLD;
		assertEquals(0.5f, popup.alpha(fadeStart + DropPopups.FADE_MILLIS / 2), 0.01f);
		assertEquals(1f, popup.height(fadeStart + DropPopups.FADE_MILLIS / 2), 0f);
		long fadeEnd = fadeStart + DropPopups.FADE_MILLIS;
		assertEquals(0f, popup.alpha(fadeEnd), 0f);
		assertTrue(popup.height(fadeEnd + DropPopups.COLLAPSE_MILLIS / 2) < 1f);

		assertEquals(1, popups.showing(fadeEnd + DropPopups.COLLAPSE_MILLIS - 1).size());
		assertTrue(popups.showing(fadeEnd + DropPopups.COLLAPSE_MILLIS).isEmpty());
		assertTrue(popups.isEmpty());
	}

	@Test
	public void aFadingPopupGetsANewOneInsteadOfMoreItems()
	{
		DropPopups popups = new DropPopups();
		drop(popups, "Goblin", 0, item(1, 1, 10));
		drop(popups, "Goblin", HOLD + 100, item(1, 1, 10));

		List<DropPopups.Popup> showing = popups.showing(HOLD + 100);
		assertEquals(2, showing.size());
		assertEquals(1, showing.get(1).getDrops());
	}

	@Test
	public void onlyThreeStayUpAndTheOldestMakesWay()
	{
		DropPopups popups = new DropPopups();
		drop(popups, "A", 0, item(1, 1, 10));
		drop(popups, "B", 100, item(1, 1, 10));
		drop(popups, "C", 200, item(1, 1, 10));
		drop(popups, "D", 300, item(1, 1, 10));

		List<DropPopups.Popup> showing = popups.showing(300);
		assertEquals(4, showing.size());
		assertEquals("A", showing.get(0).getTitle());
		assertTrue(showing.get(0).alpha(300 + DropPopups.FADE_MILLIS / 2) < 1f);
		assertEquals(1f, showing.get(1).alpha(300 + DropPopups.FADE_MILLIS / 2), 0f);
		assertEquals("D", showing.get(3).getTitle());

		List<DropPopups.Popup> later = popups.showing(300 + DropPopups.FADE_MILLIS + DropPopups.COLLAPSE_MILLIS);
		assertEquals(3, later.size());
		assertEquals("B", later.get(0).getTitle());
	}

	@Test
	public void hoverKeepsAPopupUpAndBringsBackAFadingOne()
	{
		DropPopups popups = new DropPopups();
		drop(popups, "Goblin", 0, item(1, 1, 10));
		DropPopups.Popup popup = popups.showing(0).get(0);

		popups.hover(popup, HOLD + 200);
		assertEquals(1f, popup.alpha(HOLD + 200), 0f);
		assertEquals(1f, popup.alpha(HOLD + 200 + DropPopups.HOVER_GRACE_MILLIS - 1), 0f);
		assertTrue(popup.alpha(HOLD + 200 + DropPopups.HOVER_GRACE_MILLIS + 100) < 1f);

		// Once it has faded out completely it is on its way out
		long closing = HOLD + 200 + DropPopups.HOVER_GRACE_MILLIS + DropPopups.FADE_MILLIS + 10;
		popups.hover(popup, closing);
		assertEquals(0f, popup.alpha(closing), 0f);
	}

	@Test
	public void bestValueOnlyListsItemsByTheValueShown()
	{
		DropPopups popups = new DropPopups();
		// 5 x 100 gp alch beats 300 gp on the GE, but not 450 gp
		DropPopups.Item cheapOnGe = item(1, 5, 300);
		DropPopups.Item middle = item(2, 1, 450);
		drop(popups, "Goblin", 0, cheapOnGe, middle);
		DropPopups.Popup popup = popups.showing(0).get(0);

		assertEquals(2, popup.items(false).get(0).getId());
		List<DropPopups.Item> best = popup.items(true);
		assertEquals(1, best.get(0).getId());
		assertTrue(best.get(0).showsHa(true));
		assertEquals(500, best.get(0).shownValue(true));
		assertFalse(best.get(0).showsHa(false));
		assertEquals(500 + 450, popup.worth());
		assertEquals(500 + 100, popup.haTotal());
	}

	@Test
	public void untradeableItemsHaveNoAlchValue()
	{
		DropPopups.Item untradeable = new DropPopups.Item(1, "Clue", 1, 0, new ItemValue(50, false));
		assertFalse(untradeable.isTradeable());
		assertEquals(0, untradeable.getHaValue());
		assertFalse(untradeable.showsHa(true));
		assertTrue(new DropPopups.Item(1, "Unknown", 1, 0, null).isTradeable());
	}

	@Test
	public void emptyDropsShowNothing()
	{
		DropPopups popups = new DropPopups();
		popups.add("Goblin", LootSourceType.NPC, SPRITE, Collections.emptyList(), 0, HOLD);
		assertTrue(popups.isEmpty());
	}

	@Test
	public void sameNameFromAnotherKindOfSourceGetsItsOwnPopup()
	{
		DropPopups popups = new DropPopups();
		popups.add("Man", LootSourceType.NPC, SPRITE, Collections.singletonList(item(1, 1, 10)), 0, HOLD);
		popups.add("Man", LootSourceType.PICKPOCKET, SPRITE, Collections.singletonList(item(1, 1, 10)), 100, HOLD);
		assertEquals(2, popups.showing(100).size());
	}
}
