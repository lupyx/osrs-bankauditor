package io.lupix.runelite.bankauditor;

public enum HighlightStyle
{
	UNDERLINE("Underline"),
	OUTLINE("Outline"),
	CORNER("Corner mark");

	private final String label;

	HighlightStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
