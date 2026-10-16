package io.lupix.runelite.bankauditor.sell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Items the player intends to use, so they never show up as sellable.
 *
 * <p>The list is plain text, one entry per line or separated by commas. An entry is an item name, which may
 * use * as a wildcard ("* rune", "Super *"), and may end with ":amount" to keep only that many and sell
 * the rest ("Shark:200", "Death rune:5k"). Matching ignores case. A plain name beats a wildcard.
 */
public final class KeepList
{
	private static final Pattern SEPARATOR = Pattern.compile("[,\\n\\r]+");
	private static final Pattern AMOUNT = Pattern.compile("^(.*?)\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([kmb]?)$", Pattern.CASE_INSENSITIVE);

	public static final KeepList EMPTY = new KeepList(Collections.emptyList());

	private final List<KeepRule> rules;

	private KeepList(List<KeepRule> rules)
	{
		this.rules = rules;
	}

	public static KeepList parse(String text)
	{
		if (text == null || text.trim().isEmpty())
		{
			return EMPTY;
		}

		List<KeepRule> rules = new ArrayList<>();
		for (String raw : SEPARATOR.split(text))
		{
			KeepRule rule = parseEntry(raw);
			if (rule != null)
			{
				rules.add(rule);
			}
		}
		return new KeepList(Collections.unmodifiableList(rules));
	}

	static KeepRule parseEntry(String raw)
	{
		String entry = raw.trim().replaceAll("\\s+", " ");
		if (entry.isEmpty())
		{
			return null;
		}

		Matcher m = AMOUNT.matcher(entry);
		if (m.matches() && !m.group(1).isEmpty())
		{
			long amount = parseAmount(m.group(2), m.group(3));
			return new KeepRule(m.group(1), amount);
		}
		return new KeepRule(entry, KeepRule.ALL);
	}

	private static long parseAmount(String number, String suffix)
	{
		double value = Double.parseDouble(number);
		switch (suffix.toLowerCase(Locale.ROOT))
		{
			case "k":
				value *= 1_000;
				break;
			case "m":
				value *= 1_000_000;
				break;
			case "b":
				value *= 1_000_000_000;
				break;
			default:
				break;
		}
		return (long) Math.min(value, Long.MAX_VALUE);
	}

	/**
	 * The rule that applies to this item, or null if the item isn't kept.
	 */
	public KeepRule match(String itemName)
	{
		for (KeepRule rule : rules)
		{
			if (!rule.isWildcard() && rule.matches(itemName))
			{
				return rule;
			}
		}
		for (KeepRule rule : rules)
		{
			if (rule.isWildcard() && rule.matches(itemName))
			{
				return rule;
			}
		}
		return null;
	}

	/**
	 * True when the item is on the list by its own name, as opposed to through a wildcard.
	 * Only those entries can be removed from the right-click menu.
	 */
	public boolean containsExactly(String itemName)
	{
		KeepRule rule = match(itemName);
		return rule != null && !rule.isWildcard();
	}

	public List<KeepRule> getRules()
	{
		return rules;
	}

	public boolean isEmpty()
	{
		return rules.isEmpty();
	}

	/**
	 * Returns the list text with the item added to the end, or unchanged if it is already listed by name.
	 * The user's own formatting is left alone.
	 */
	public static String add(String text, String itemName)
	{
		String name = itemName.trim();
		if (name.isEmpty() || parse(text).containsExactly(name))
		{
			return text == null ? "" : text;
		}
		if (text == null || text.trim().isEmpty())
		{
			return name;
		}
		return stripTrailingSeparators(text) + (text.contains("\n") ? "\n" : ", ") + name;
	}

	/**
	 * Returns the list text without the entries for this exact item name (wildcards are left in place).
	 */
	public static String remove(String text, String itemName)
	{
		if (text == null || text.trim().isEmpty())
		{
			return "";
		}

		String separator = text.contains("\n") ? "\n" : ", ";
		List<String> kept = new ArrayList<>();
		for (String raw : SEPARATOR.split(text))
		{
			KeepRule rule = parseEntry(raw);
			if (rule == null)
			{
				continue;
			}
			if (!rule.isWildcard() && rule.matches(itemName))
			{
				continue;
			}
			kept.add(raw.trim());
		}
		return String.join(separator, kept);
	}

	private static String stripTrailingSeparators(String text)
	{
		int end = text.length();
		while (end > 0)
		{
			char c = text.charAt(end - 1);
			if (c == ',' || Character.isWhitespace(c))
			{
				end--;
			}
			else
			{
				break;
			}
		}
		return text.substring(0, end);
	}
}
