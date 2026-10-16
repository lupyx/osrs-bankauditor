package io.lupix.runelite.bankauditor.sell;

import static io.lupix.runelite.bankauditor.sell.SellMode.ALL_TRADEABLE;
import static io.lupix.runelite.bankauditor.sell.SellMode.LOOT_ONLY;
import static io.lupix.runelite.bankauditor.sell.SellableCalculator.isWorthSelling;
import static io.lupix.runelite.bankauditor.sell.SellableCalculator.sellableQuantity;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SellableCalculatorTest
{
	private static KeepRule rule(String text)
	{
		return KeepList.parse(text).getRules().get(0);
	}

	@Test
	public void lootOnlySellsTheLootedPart()
	{
		assertEquals(1200, sellableQuantity(LOOT_ONLY, true, 6200, 1200, null));
		assertEquals(0, sellableQuantity(LOOT_ONLY, true, 6200, 0, null));
	}

	@Test
	public void lootedAmountIsCappedByWhatIsInBankAndInventory()
	{
		// Some of the looted stack is worn, which doesn't count
		assertEquals(1, sellableQuantity(LOOT_ONLY, true, 1, 2, null));
	}

	@Test
	public void allTradeableSellsEverything()
	{
		assertEquals(6200, sellableQuantity(ALL_TRADEABLE, true, 6200, 0, null));
	}

	@Test
	public void untradeableNeverSells()
	{
		assertEquals(0, sellableQuantity(LOOT_ONLY, false, 10, 10, null));
		assertEquals(0, sellableQuantity(ALL_TRADEABLE, false, 10, 10, null));
	}

	@Test
	public void keptItemsNeverSell()
	{
		assertEquals(0, sellableQuantity(LOOT_ONLY, true, 100, 100, rule("Shark")));
		assertEquals(0, sellableQuantity(ALL_TRADEABLE, true, 100, 0, rule("Shark")));
	}

	@Test
	public void keepAmountHoldsBackPartOfTheStack()
	{
		// Keep 200 sharks. 250 owned of which 120 looted: only 50 can go
		assertEquals(50, sellableQuantity(LOOT_ONLY, true, 250, 120, rule("Shark:200")));
		// Plenty above the amount, so all the loot can go
		assertEquals(120, sellableQuantity(LOOT_ONLY, true, 1000, 120, rule("Shark:200")));
		// Below the amount, nothing goes
		assertEquals(0, sellableQuantity(LOOT_ONLY, true, 150, 120, rule("Shark:200")));
		assertEquals(800, sellableQuantity(ALL_TRADEABLE, true, 1000, 0, rule("Shark:200")));
	}

	@Test
	public void nothingOwnedNothingToSell()
	{
		assertEquals(0, sellableQuantity(ALL_TRADEABLE, true, 0, 5, null));
	}

	@Test
	public void minimumValue()
	{
		assertTrue(isWorthSelling(10, 0, 0));
		assertTrue(isWorthSelling(10, 100, 1000));
		assertFalse(isWorthSelling(9, 100, 1000));
		assertFalse(isWorthSelling(10, 0, 1));
		assertFalse(isWorthSelling(0, 100, 0));
		assertTrue(isWorthSelling(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
	}

	@Test
	public void saturatedMultiply()
	{
		assertEquals(Long.MAX_VALUE, SellableCalculator.saturatedMultiply(Long.MAX_VALUE, 2));
		assertEquals(6, SellableCalculator.saturatedMultiply(2, 3));
	}
}
