package io.lupix.runelite.bankauditor.sell;

import java.util.regex.Pattern;

/**
 * One entry of the keep list: an item name, optionally with * wildcards, and optionally an amount to keep.
 */
public final class KeepRule
{
	/**
	 * Keep the whole stack.
	 */
	public static final long ALL = -1;

	private final String pattern;
	private final long amount;
	private final Pattern regex;

	KeepRule(String pattern, long amount)
	{
		this.pattern = pattern;
		this.amount = amount;
		this.regex = isWildcard() ? toRegex(pattern) : null;
	}

	/**
	 * The name as written, trimmed.
	 */
	public String getPattern()
	{
		return pattern;
	}

	/**
	 * How many to keep, or {@link #ALL}.
	 */
	public long getAmount()
	{
		return amount;
	}

	public boolean keepsAll()
	{
		return amount == ALL;
	}

	public boolean isWildcard()
	{
		return pattern.indexOf('*') >= 0;
	}

	boolean matches(String itemName)
	{
		if (itemName == null)
		{
			return false;
		}
		if (regex != null)
		{
			return regex.matcher(itemName).matches();
		}
		return pattern.equalsIgnoreCase(itemName.trim());
	}

	private static Pattern toRegex(String wildcard)
	{
		String[] parts = wildcard.split("\\*", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				sb.append(".*");
			}
			sb.append(Pattern.quote(parts[i]));
		}
		return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	@Override
	public String toString()
	{
		return keepsAll() ? pattern : pattern + ":" + amount;
	}
}
