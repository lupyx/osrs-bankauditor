package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * When each loot trip ended. A trip is every drop after the previous end, up to and including its own end.
 * Drops after the last end make up the trip still going.
 */
public final class TripLog
{
	/**
	 * Ascending, no duplicates.
	 */
	private final List<Long> ends = new ArrayList<>();

	public TripLog()
	{
	}

	public TripLog(List<Long> saved)
	{
		if (saved == null)
		{
			return;
		}
		for (Long end : saved)
		{
			if (end != null && !ends.contains(end))
			{
				ends.add(end);
			}
		}
		Collections.sort(ends);
	}

	/**
	 * Ends the trip that is going, if it has any drops. The next drop starts a new one.
	 *
	 * @param newestDrop timestamp of the newest recorded drop, or null when there are none
	 * @return whether a trip was ended
	 */
	public boolean end(long now, Long newestDrop)
	{
		if (!hasCurrent(newestDrop))
		{
			return false;
		}
		ends.add(Math.max(now, newestDrop));
		return true;
	}

	/**
	 * Whether there are drops since the last trip ended.
	 */
	public boolean hasCurrent(Long newestDrop)
	{
		return newestDrop != null && (ends.isEmpty() || newestDrop > ends.get(ends.size() - 1));
	}

	/**
	 * Forgets ends from before the oldest drop, since they no longer separate anything.
	 *
	 * @param oldestDrop timestamp of the oldest recorded drop, or null when there are none
	 * @return whether anything was forgotten
	 */
	public boolean prune(Long oldestDrop)
	{
		if (oldestDrop == null)
		{
			boolean had = !ends.isEmpty();
			ends.clear();
			return had;
		}
		return ends.removeIf(end -> end < oldestDrop);
	}

	/**
	 * The end of the trip a drop belongs to, or -1 for the trip still going.
	 */
	public long tripEnd(long timestamp)
	{
		int i = Collections.binarySearch(ends, timestamp);
		if (i < 0)
		{
			i = -i - 1;
		}
		return i < ends.size() ? ends.get(i) : -1;
	}

	public List<Long> getEnds()
	{
		return Collections.unmodifiableList(new ArrayList<>(ends));
	}

	public void clear()
	{
		ends.clear();
	}
}
