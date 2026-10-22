package io.lupix.runelite.bankauditor.util;

import static io.lupix.runelite.bankauditor.util.Formats.ago;
import static io.lupix.runelite.bankauditor.util.Formats.commas;
import static io.lupix.runelite.bankauditor.util.Formats.compact;
import static io.lupix.runelite.bankauditor.util.Formats.duration;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatsTest
{
	@Test
	public void commasGroupThousands()
	{
		assertEquals("0", commas(0));
		assertEquals("6,200", commas(6200));
		assertEquals("1,234,567", commas(1_234_567));
	}

	@Test
	public void compactLabels()
	{
		assertEquals("950", compact(950));
		assertEquals("1K", compact(1_000));
		assertEquals("1.2K", compact(1_250));
		assertEquals("9.9K", compact(9_999));
		assertEquals("25K", compact(25_400));
		assertEquals("999K", compact(999_999));
		assertEquals("1.5M", compact(1_500_000));
		assertEquals("12M", compact(12_345_678));
		assertEquals("2.1B", compact(2_147_483_647));
		assertEquals("-1.2K", compact(-1_250));
		assertEquals("-500", compact(-500));
	}

	@Test
	public void agoBuckets()
	{
		long now = 100L * 24 * 3_600_000;
		assertEquals("just now", ago(now, now - 59_000));
		assertEquals("just now", ago(now, now + 5_000));
		assertEquals("1 min ago", ago(now, now - 60_000));
		assertEquals("59 min ago", ago(now, now - 59 * 60_000));
		assertEquals("2 h ago", ago(now, now - 2 * 3_600_000 - 5));
		assertEquals("1 day ago", ago(now, now - 25 * 3_600_000));
		assertEquals("3 days ago", ago(now, now - 3 * 24 * 3_600_000));
	}

	@Test
	public void durations()
	{
		assertEquals("under 1 min", duration(0));
		assertEquals("under 1 min", duration(59_000));
		assertEquals("43 min", duration(43 * 60_000 + 5));
		assertEquals("2 h", duration(2 * 3_600_000));
		assertEquals("1 h 5 min", duration(65 * 60_000));
	}
}
