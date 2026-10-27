package io.lupix.runelite.bankauditor.loot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlayerTaskTrackerTest
{
	@Test
	public void killsOfTheTaskMonsterCount()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		assertNull(tracker.taskFor("Abyssal demon", 1));
		assertTrue(tracker.update("ABYSSAL DEMONS", 180, 180, 1000, 1));

		SlayerTask task = tracker.getCurrent();
		assertEquals("Abyssal demons", task.getName());
		assertEquals(1000, task.getStart());
		assertEquals(task, tracker.taskFor("Abyssal demon", 2));
		assertEquals(task, tracker.taskFor("Greater abyssal demon", 2));
		// Named in the task's list of other monsters that count
		assertEquals(task, tracker.taskFor("Abyssal Sire", 2));
		assertNull(tracker.taskFor("Goblin", 2));
	}

	@Test
	public void killsGoingDownIsTheSameTask()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		tracker.update("Abyssal demons", 180, 180, 1000, 1);
		assertTrue(tracker.update("Abyssal demons", 179, 180, 2000, 2));
		assertFalse(tracker.update("Abyssal demons", 179, 180, 3000, 3));
		assertEquals(1000, tracker.getCurrent().getStart());
	}

	@Test
	public void aNewAssignmentOfTheSameMonsterIsANewTask()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		tracker.update("Abyssal demons", 5, 180, 1000, 1);
		tracker.update("Abyssal demons", 150, 150, 2000, 2);
		assertEquals(2000, tracker.getCurrent().getStart());

		// Same amount as before, but more kills left than there were
		tracker.update("Abyssal demons", 150, 150, 3000, 3);
		assertEquals(2000, tracker.getCurrent().getStart());
		tracker.update("Abyssal demons", 10, 150, 4000, 4);
		tracker.update("Abyssal demons", 150, 150, 5000, 5);
		assertEquals(5000, tracker.getCurrent().getStart());
	}

	@Test
	public void theLastKillCountsJustAfterTheTaskIsDone()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		tracker.update("Hellhounds", 1, 100, 1000, 10);
		SlayerTask task = tracker.getCurrent();
		assertTrue(tracker.update(null, 0, 0, 2000, 11));
		assertNull(tracker.getCurrent());
		assertNull(tracker.save());

		assertEquals(task, tracker.taskFor("Hellhound", 11 + SlayerTaskTracker.FINISHED_GRACE_TICKS));
		assertNull(tracker.taskFor("Hellhound", 12 + SlayerTaskTracker.FINISHED_GRACE_TICKS));
		assertFalse(tracker.update(null, 0, 0, 3000, 40));
	}

	@Test
	public void anotherTaskEndsTheFirst()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		tracker.update("Hellhounds", 50, 100, 1000, 1);
		SlayerTask hellhounds = tracker.getCurrent();
		tracker.update("Bloodveld", 120, 120, 2000, 2);
		assertNotEquals(hellhounds, tracker.getCurrent());
		assertNull(tracker.taskFor("Hellhound", 3));
		assertEquals("Bloodveld", tracker.taskFor("Mutated Bloodveld", 3).getName());
	}

	@Test
	public void restoreThenClear()
	{
		SlayerTaskTracker tracker = new SlayerTaskTracker();
		tracker.update("Hellhounds", 50, 100, 1000, 1);
		SlayerTaskTracker other = new SlayerTaskTracker();
		other.restore(tracker.save());
		assertEquals(tracker.getCurrent(), other.taskFor("Hellhound", 5));
		other.clear();
		assertNull(other.getCurrent());
		other.restore(null);
		assertNull(other.taskFor("Hellhound", 5));
	}
}
