package io.lupix.runelite.bankauditor.loot;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * One slayer task assignment, stored on the drops that counted for it. Two tasks of the same monster are told
 * apart by when they started.
 */
public final class SlayerTask
{
	@SerializedName("n")
	private final String name;
	/**
	 * Epoch milliseconds of when the plugin first saw the task.
	 */
	@SerializedName("s")
	private final long start;

	public SlayerTask(String name, long start)
	{
		this.name = name;
		this.start = start;
	}

	public String getName()
	{
		return name == null ? "Slayer task" : name;
	}

	public long getStart()
	{
		return start;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof SlayerTask))
		{
			return false;
		}
		SlayerTask other = (SlayerTask) o;
		return start == other.start && Objects.equals(name, other.name);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, start);
	}

	@Override
	public String toString()
	{
		return getName() + " since " + start;
	}
}
