package io.lupix.runelite.bankauditor.loot;

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TripLogTest
{
	@Test
	public void aTripOnlyEndsWithDropsInIt()
	{
		TripLog trips = new TripLog();
		assertFalse(trips.end(1_000, null));

		assertTrue(trips.end(1_000, 500L));
		assertEquals(Arrays.asList(1_000L), trips.getEnds());
		// Opening the bank again before the next drop changes nothing
		assertFalse(trips.end(2_000, 500L));
		assertFalse(trips.hasCurrent(500L));

		assertTrue(trips.hasCurrent(2_500L));
		assertTrue(trips.end(3_000, 2_500L));
		assertEquals(Arrays.asList(1_000L, 3_000L), trips.getEnds());
	}

	@Test
	public void dropsBelongToTheTripTheyEndedIn()
	{
		TripLog trips = new TripLog(Arrays.asList(3_000L, 1_000L, 1_000L, null));
		assertEquals(Arrays.asList(1_000L, 3_000L), trips.getEnds());
		assertEquals(1_000, trips.tripEnd(10));
		assertEquals(1_000, trips.tripEnd(1_000));
		assertEquals(3_000, trips.tripEnd(1_001));
		assertEquals(-1, trips.tripEnd(3_001));
	}

	@Test
	public void endsBeforeTheOldestDropAreForgotten()
	{
		TripLog trips = new TripLog(Arrays.asList(1_000L, 2_000L, 3_000L));
		assertFalse(trips.prune(500L));
		assertTrue(trips.prune(2_500L));
		assertEquals(Arrays.asList(3_000L), trips.getEnds());
		assertTrue(trips.prune(null));
		assertEquals(0, trips.getEnds().size());
	}

	@Test
	public void aClockBehindTheNewestDropStillEndsAfterIt()
	{
		TripLog trips = new TripLog();
		assertTrue(trips.end(1_000, 1_500L));
		assertEquals(Arrays.asList(1_500L), trips.getEnds());
		assertEquals(1_500, trips.tripEnd(1_500));
	}
}
