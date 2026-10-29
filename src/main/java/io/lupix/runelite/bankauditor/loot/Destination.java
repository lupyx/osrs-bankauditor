package io.lupix.runelite.bankauditor.loot;

import com.google.gson.annotations.SerializedName;

/**
 * Somewhere loot went by itself instead of the inventory: used up by a device the moment it dropped, or put
 * straight into an open container when it was picked up.
 */
public enum Destination
{
	@SerializedName("bc")
	BONECRUSHER("crushed", "Crushed by your bonecrusher as it dropped", true),
	@SerializedName("as")
	ASH_SANCTIFIER("scattered", "Scattered by your ash sanctifier as it dropped", true),
	@SerializedName("eb")
	ETHER_BRACELET("in bracelet", "Absorbed by your bracelet of ethereum as it dropped", false),
	@SerializedName("hs")
	HERB_SACK("in herb sack", "Went straight into your open herb sack", false),
	@SerializedName("sb")
	SEED_BOX("in seed box", "Went straight into your open seed box", false),
	@SerializedName("gb")
	GEM_BAG("in gem bag", "Went straight into your open gem bag", false),
	@SerializedName("lb")
	LOOTING_BAG("in looting bag", "Went straight into your open looting bag", false),
	@SerializedName("rp")
	RUNE_POUCH("in rune pouch", "Went straight into your rune pouch", false),
	@SerializedName("am")
	AMMO_SLOT("equipped", "Went straight into your ammo slot", false);

	private final String shortText;
	private final String description;
	private final boolean usedUp;

	Destination(String shortText, String description, boolean usedUp)
	{
		this.shortText = shortText;
		this.description = description;
		this.usedUp = usedUp;
	}

	/**
	 * A few words for after the amount, like "x 4 crushed".
	 */
	public String getShortText()
	{
		return shortText;
	}

	/**
	 * A sentence for a hover.
	 */
	public String getDescription()
	{
		return description;
	}

	/**
	 * Whether the items are gone, as opposed to stored somewhere the player can take them back out of.
	 */
	public boolean isUsedUp()
	{
		return usedUp;
	}
}
