package io.lupix.runelite.bankauditor.loot;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the ledger, the loot history and the trip log into JSON for the RuneScape profile config, and back.
 * Bad or missing data loads as empty rather than failing the plugin.
 */
public final class LootStateCodec
{
	private static final Type LEDGER_TYPE = new TypeToken<Map<Integer, LootLedger.Entry>>()
	{
	}.getType();
	private static final Type HISTORY_TYPE = new TypeToken<List<LootRecord>>()
	{
	}.getType();
	private static final Type COUNTS_TYPE = new TypeToken<Map<Integer, Long>>()
	{
	}.getType();
	private static final Type TRIPS_TYPE = new TypeToken<List<Long>>()
	{
	}.getType();

	private final Gson gson;

	public LootStateCodec(Gson gson)
	{
		this.gson = gson;
	}

	public String writeLedger(LootLedger ledger)
	{
		return gson.toJson(ledger.snapshot(), LEDGER_TYPE);
	}

	public LootLedger readLedger(String json)
	{
		if (json == null || json.isEmpty())
		{
			return new LootLedger();
		}
		try
		{
			Map<Integer, LootLedger.Entry> saved = gson.fromJson(json, LEDGER_TYPE);
			return new LootLedger(saved);
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			return new LootLedger();
		}
	}

	public String writeHistory(LootHistory history)
	{
		return gson.toJson(history.oldestFirst(), HISTORY_TYPE);
	}

	public LootHistory readHistory(String json)
	{
		if (json == null || json.isEmpty())
		{
			return new LootHistory();
		}
		try
		{
			List<LootRecord> saved = gson.fromJson(json, HISTORY_TYPE);
			return new LootHistory(saved);
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			return new LootHistory();
		}
	}

	public String writeTrips(TripLog trips)
	{
		return gson.toJson(trips.getEnds(), TRIPS_TYPE);
	}

	public TripLog readTrips(String json)
	{
		if (json == null || json.isEmpty())
		{
			return new TripLog();
		}
		try
		{
			List<Long> saved = gson.fromJson(json, TRIPS_TYPE);
			return new TripLog(saved);
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			return new TripLog();
		}
	}

	/**
	 * Item id to quantity, used for the last seen bank contents.
	 */
	public String writeCounts(Map<Integer, Long> counts)
	{
		return gson.toJson(counts, COUNTS_TYPE);
	}

	/**
	 * @return the saved counts, or null when nothing (valid) was saved
	 */
	public Map<Integer, Long> readCounts(String json)
	{
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			Map<Integer, Long> saved = gson.fromJson(json, COUNTS_TYPE);
			if (saved == null)
			{
				return null;
			}
			Map<Integer, Long> clean = new HashMap<>();
			for (Map.Entry<Integer, Long> e : saved.entrySet())
			{
				if (e.getKey() != null && e.getValue() != null && e.getValue() > 0)
				{
					clean.put(e.getKey(), e.getValue());
				}
			}
			return clean;
		}
		catch (JsonParseException | IllegalStateException | ClassCastException e)
		{
			return null;
		}
	}
}
