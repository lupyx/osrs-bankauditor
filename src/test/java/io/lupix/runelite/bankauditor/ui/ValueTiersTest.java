package io.lupix.runelite.bankauditor.ui;

import java.awt.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class ValueTiersTest
{
	private static final ValueTiers TIERS = new ValueTiers(
		new long[]{20_000, 100_000, 1_000_000, 10_000_000},
		new Color[]{Color.BLUE, Color.GREEN, Color.ORANGE, Color.PINK});

	@Test
	public void highestTierReached()
	{
		assertNull(TIERS.colorFor(19_999));
		assertEquals(Color.BLUE, TIERS.colorFor(20_000));
		assertEquals(Color.GREEN, TIERS.colorFor(999_999));
		assertEquals(Color.ORANGE, TIERS.colorFor(1_000_000));
		assertEquals(Color.PINK, TIERS.colorFor(2_000_000_000L));
	}

	@Test
	public void zeroSwitchesATierOff()
	{
		ValueTiers tiers = new ValueTiers(new long[]{0, 100_000}, new Color[]{Color.BLUE, Color.GREEN});

		assertNull(tiers.colorFor(50_000));
		assertNull(tiers.colorFor(0));
		assertEquals(Color.GREEN, tiers.colorFor(100_000));
		assertNull(ValueTiers.NONE.colorFor(Long.MAX_VALUE));
	}

	@Test
	public void tiersOutOfOrderStillPickTheHighest()
	{
		ValueTiers tiers = new ValueTiers(new long[]{1_000_000, 20_000}, new Color[]{Color.ORANGE, Color.BLUE});

		assertEquals(Color.BLUE, tiers.colorFor(50_000));
		assertEquals(Color.ORANGE, tiers.colorFor(5_000_000));
	}
}
