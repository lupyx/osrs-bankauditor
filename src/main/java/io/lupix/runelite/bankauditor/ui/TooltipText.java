package io.lupix.runelite.bankauditor.ui;

import io.lupix.runelite.bankauditor.loot.LootAttribution;
import io.lupix.runelite.bankauditor.loot.LootRecord;
import io.lupix.runelite.bankauditor.sell.ItemSummary;
import io.lupix.runelite.bankauditor.sell.KeepRule;
import io.lupix.runelite.bankauditor.util.Formats;

/**
 * Builds the text of the bank item tooltip, using RuneLite's tooltip markup
 * ({@code </br>} for new lines and {@code <col=rrggbb>} for colors).
 */
public final class TooltipText
{
	static final String BREAK = "</br>";
	private static final String TITLE = "<col=ff9040>";
	private static final String MUTED = "<col=a0a0a0>";
	private static final String SELL = "<col=ffc800>";
	private static final String END = "</col>";

	private TooltipText()
	{
	}

	/**
	 * @return the tooltip, or null when there is nothing worth saying about the item
	 */
	public static String build(ItemSummary item, LootAttribution attribution, long now, int maxSources)
	{
		boolean looted = item.getLooted() > 0;
		if (!looted && !item.isSellable() && !item.isKept())
		{
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(TITLE).append(item.getName()).append(END);

		if (looted)
		{
			sb.append(BREAK).append("From loot: ").append(Formats.commas(item.getLooted()));
			if (item.getOwned() != item.getLooted())
			{
				sb.append(" of ").append(Formats.commas(item.getOwned()));
			}

			if (attribution != null)
			{
				int shown = 0;
				long hidden = attribution.getUnexplained();
				for (LootAttribution.Share share : attribution.getShares())
				{
					if (shown < maxSources)
					{
						sb.append(BREAK).append(MUTED).append(describe(share, now)).append(END);
						shown++;
					}
					else
					{
						hidden += share.getQuantity();
					}
				}
				if (hidden > 0)
				{
					sb.append(BREAK).append(MUTED).append("+").append(Formats.commas(hidden)).append(" from older loot").append(END);
				}
			}
		}

		if (item.isSellable())
		{
			sb.append(BREAK).append(SELL).append("Sellable: ").append(Formats.commas(item.getSellable()));
			if (item.getSellableValue() > 0)
			{
				sb.append(" (").append(Formats.compact(item.getSellableValue())).append(" gp)");
			}
			sb.append(END);
		}

		KeepRule rule = item.getKeepRule();
		if (rule != null)
		{
			sb.append(BREAK).append(MUTED);
			if (rule.keepsAll())
			{
				sb.append("On your keep list");
			}
			else
			{
				sb.append("Keeping ").append(Formats.commas(rule.getAmount()));
			}
			sb.append(END);
		}

		return sb.toString();
	}

	static String describe(LootAttribution.Share share, long now)
	{
		LootRecord record = share.getRecord();
		StringBuilder sb = new StringBuilder();
		sb.append(Formats.commas(share.getQuantity())).append(" from ").append(record.describeSource())
			.append(", ").append(Formats.ago(now, record.getTimestamp()));
		String where = record.describeLocation();
		if (where != null)
		{
			sb.append(", ").append(where);
		}
		return sb.toString();
	}
}
