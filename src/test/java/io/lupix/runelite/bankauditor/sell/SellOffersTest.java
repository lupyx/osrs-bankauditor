package io.lupix.runelite.bankauditor.sell;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SellOffersTest
{
	private static final int RUNE = 560;
	private static final int SHARK = 385;

	private final SellOffers offers = new SellOffers();
	private final Map<Integer, Long> held = new HashMap<>();

	private long offered(int itemId)
	{
		return offers.counts(id -> held.getOrDefault(id, 0L)).getOrDefault(itemId, 0L);
	}

	@Test
	public void sellingCountsWhatIsLeft()
	{
		offers.update(0, RUNE, SellOffers.Kind.SELLING, 1000, 0, 0);
		assertEquals(1000, offered(RUNE));

		offers.update(0, RUNE, SellOffers.Kind.SELLING, 1000, 400, 0);
		assertEquals(600, offered(RUNE));

		offers.update(0, RUNE, SellOffers.Kind.OTHER, 1000, 1000, 0);
		assertTrue(offers.counts(id -> 0).isEmpty());
	}

	@Test
	public void abortedOfferShrinksAsItemsAreCollected()
	{
		held.put(RUNE, 900L);
		offers.update(3, RUNE, SellOffers.Kind.SELLING, 100, 10, 900);
		offers.update(3, RUNE, SellOffers.Kind.CANCELLED, 100, 10, 900);
		assertEquals(90, offered(RUNE));

		// The 90 runes are taken out of the collection box, the coins are left in it
		held.put(RUNE, 990L);
		offers.update(3, RUNE, SellOffers.Kind.CANCELLED, 100, 10, 990);
		assertEquals(0, offered(RUNE));
	}

	@Test
	public void usingTheItemAfterAbortingDoesNotGrowTheOffer()
	{
		held.put(SHARK, 50L);
		offers.update(1, SHARK, SellOffers.Kind.CANCELLED, 20, 0, 50);
		held.put(SHARK, 10L);

		assertEquals(20, offered(SHARK));
	}

	@Test
	public void slotsAddUpPerItem()
	{
		offers.update(0, RUNE, SellOffers.Kind.SELLING, 500, 0, 0);
		offers.update(1, RUNE, SellOffers.Kind.SELLING, 300, 100, 0);
		offers.update(2, SHARK, SellOffers.Kind.SELLING, 10, 0, 0);

		assertEquals(700, offered(RUNE));
		assertEquals(10, offered(SHARK));

		offers.clear();
		assertEquals(0, offered(RUNE));
	}
}
