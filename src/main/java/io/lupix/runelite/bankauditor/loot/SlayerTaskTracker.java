package io.lupix.runelite.bankauditor.loot;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Follows the slayer task from what the game says it is, and tells which kills count for it.
 */
public final class SlayerTaskTracker
{
	/**
	 * How long after a task is done its last kill's drop can still show up. The count goes down as the
	 * monster dies, but the drop only lands once it has finished dying.
	 */
	static final int FINISHED_GRACE_TICKS = 20;

	/**
	 * What is saved between sessions, so a task keeps its box after logging out.
	 */
	public static final class Saved
	{
		@SerializedName("t")
		private final SlayerTask task;
		@SerializedName("r")
		private final int remaining;
		@SerializedName("o")
		private final int original;

		public Saved(SlayerTask task, int remaining, int original)
		{
			this.task = task;
			this.remaining = remaining;
			this.original = original;
		}
	}

	private SlayerTask current;
	private List<Pattern> targets;
	private int remaining;
	private int original;
	private SlayerTask finished;
	private List<Pattern> finishedTargets;
	private int finishedTick;

	/**
	 * Takes in what the game says the task is now.
	 *
	 * @param name      the task's name, or null for no task
	 * @param remaining kills left
	 * @param original  kills the task was given with
	 * @return whether what should be saved changed
	 */
	public boolean update(String name, int remaining, int original, long now, int tick)
	{
		if (name == null || remaining <= 0)
		{
			if (current == null)
			{
				return false;
			}
			finished = current;
			finishedTargets = targets;
			finishedTick = tick;
			current = null;
			targets = null;
			return true;
		}

		String shown = SlayerTasks.displayName(name);
		// Same name and count but more kills left can only be a new assignment, as can another starting amount
		boolean same = current != null && current.getName().equalsIgnoreCase(shown)
			&& original == this.original && remaining <= this.remaining;
		if (same)
		{
			boolean changed = remaining != this.remaining;
			this.remaining = remaining;
			return changed;
		}
		current = new SlayerTask(shown, now);
		targets = SlayerTasks.targets(name);
		this.remaining = remaining;
		this.original = original;
		return true;
	}

	/**
	 * The task a kill of this monster counts for, or null. Right after a task is done, its last kill still
	 * counts for it.
	 */
	public SlayerTask taskFor(String monsterName, int tick)
	{
		if (current != null && SlayerTasks.counts(targets, monsterName))
		{
			return current;
		}
		if (finished != null && tick - finishedTick <= FINISHED_GRACE_TICKS && SlayerTasks.counts(finishedTargets, monsterName))
		{
			return finished;
		}
		return null;
	}

	public SlayerTask getCurrent()
	{
		return current;
	}

	public Saved save()
	{
		return current == null ? null : new Saved(current, remaining, original);
	}

	public void restore(Saved saved)
	{
		clear();
		if (saved != null && saved.task != null)
		{
			current = saved.task;
			targets = SlayerTasks.targets(saved.task.getName());
			remaining = saved.remaining;
			original = saved.original;
		}
	}

	public void clear()
	{
		current = null;
		targets = null;
		remaining = 0;
		original = 0;
		finished = null;
		finishedTargets = null;
		finishedTick = 0;
	}
}
