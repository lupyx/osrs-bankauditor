package io.lupix.runelite.bankauditor.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ItemValueTest
{
	@Test
	public void alchValueOfAStack()
	{
		assertEquals(24_000, ItemValue.haValue(22124, 2, new ItemValue(12_000, true)));
		assertEquals(25_123, ItemValue.haValue(ItemValue.COINS, 25_123, new ItemValue(0, true)));
		assertEquals(25_123, ItemValue.haValue(ItemValue.COINS, 25_123, null));
	}

	@Test
	public void untradeableOrWorthlessCantBeAlched()
	{
		assertFalse(new ItemValue(5_000, false).isAlchable());
		assertFalse(new ItemValue(0, true).isAlchable());
		assertTrue(new ItemValue(1, true).isAlchable());
		assertEquals(0, ItemValue.haValue(1, 10, new ItemValue(5_000, false)));
		assertEquals(0, ItemValue.haValue(1, 10, null));
	}

	@Test
	public void betterValueGoesToTheGeOnATie()
	{
		ItemValue alchable = new ItemValue(12_000, true);
		assertTrue(ItemValue.alchIsBetter(9_500, 12_000, alchable));
		assertFalse(ItemValue.alchIsBetter(12_000, 12_000, alchable));
		assertFalse(ItemValue.alchIsBetter(39_512, 12_000, alchable));
		assertFalse(ItemValue.alchIsBetter(0, 12_000, new ItemValue(12_000, false)));
		assertFalse(ItemValue.alchIsBetter(0, 12_000, null));
	}
}
