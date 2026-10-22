package io.lupix.runelite.bankauditor.ui;

import java.awt.Color;

/**
 * Colors for loot by the value of a stack, from low to insane, like the ground items plugin.
 */
public final class ValueTiers
{
	public static final ValueTiers NONE = new ValueTiers(new long[0], new Color[0]);

	private final long[] thresholds;
	private final Color[] colors;

	/**
	 * @param thresholds lowest tier first; a threshold of 0 or less switches that tier off
	 * @param colors     one per threshold
	 */
	public ValueTiers(long[] thresholds, Color[] colors)
	{
		if (thresholds.length != colors.length)
		{
			throw new IllegalArgumentException("One color per threshold");
		}
		this.thresholds = thresholds.clone();
		this.colors = colors.clone();
	}

	/**
	 * Color of the highest tier the value reaches, or null when it reaches none.
	 */
	public Color colorFor(long value)
	{
		Color color = null;
		long reached = 0;
		for (int i = 0; i < thresholds.length; i++)
		{
			// Highest threshold wins, so tiers set out of order still behave
			if (thresholds[i] > 0 && value >= thresholds[i] && thresholds[i] >= reached)
			{
				reached = thresholds[i];
				color = colors[i];
			}
		}
		return color;
	}
}
