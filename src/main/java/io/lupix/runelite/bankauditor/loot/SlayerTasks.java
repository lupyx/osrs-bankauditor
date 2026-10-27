/*
 * Copyright (c) 2026, lupyx
 * Task names and target names from RuneLite's Slayer plugin:
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.lupix.runelite.bankauditor.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which monsters count for a slayer task, by name. The list is RuneLite's Slayer plugin's: each task, then any
 * monsters that count for it without having the task's name in theirs. Like the Slayer plugin, a monster
 * also counts when its name has the task name in it without the plural "s", so a Greater abyssal demon counts
 * for Abyssal demons.
 */
public final class SlayerTasks
{
	private static final String[][] TASKS = {
		{"Aberrant spectres", "Spectre"},
		{"Abyssal demons", "Abyssal Sire"},
		{"The Abyssal Sire"},
		{"The Alchemical Hydra"},
		{"Ankou"},
		{"Aquanites"},
		{"Araxxor"},
		{"Araxytes", "Araxxor"},
		{"Aviansies", "Kree'arra", "Flight Kilisa", "Flockleader Geerin", "Wingman Skree"},
		{"Bandits", "Bandit", "Black Heather", "Donny the Lad", "Speedy Keith"},
		{"Banshees"},
		{"Barrows Brothers"},
		{"Basilisks"},
		{"Bats", "Death wing"},
		{"Bears", "Callisto", "Artio"},
		{"Birds", "Chicken", "Rooster", "Terrorbird", "Seagull", "Vulture", "Duck", "Penguin", "Baby Roc"},
		{"Black demons", "Demonic gorilla", "Balfrug Kreeyath", "Skotizo", "Porazdir"},
		{"Black dragons"},
		{"Black Knights", "Black Knight"},
		{"Bloodveld"},
		{"Blue dragons", "Vorkath"},
		{"Brine rats"},
		{"Callisto"},
		{"Catablepon"},
		{"Cave bugs"},
		{"Cave crawlers", "Chasm crawler"},
		{"Cave horrors", "Cave abomination"},
		{"Cave kraken", "Kraken"},
		{"Cave slimes"},
		{"Cerberus"},
		{"Chaos druids"},
		{"The Chaos Elemental"},
		{"The Chaos Fanatic"},
		{"Cockatrice", "Cockathrice"},
		{"Cows", "Buffalo", "Brutus"},
		{"Crabs", "Ammonite Crab", "Frost Crab", "King Sand Crab", "Rock Crab", "Giant Rock Crab", "Sand Crab", "Swamp Crab"},
		{"Crawling hands", "Crushing hand"},
		{"Crazy Archaeologists"},
		{"Crocodiles"},
		{"Custodian Stalkers", "Ancient Custodian"},
		{"Dagannoth"},
		{"Dagannoth Kings"},
		{"Dark beasts", "Night beast"},
		{"Dark warriors", "Dark warrior"},
		{"Deranged Archaeologist"},
		{"Dogs", "Jackal", "Temple Guardian"},
		{"Drakes"},
		{"Duke Sucellus"},
		{"Dust devils", "Choke devil"},
		{"Dwarves", "Dwarf", "Black Guard"},
		{"Earth warriors"},
		{"Elves", "Elf", "Iorwerth Warrior", "Iorwerth Archer"},
		{"Ents"},
		{"Fever spiders"},
		{"Fire giants", "Branda the Fire Queen"},
		{"Fleshcrawlers", "Flesh crawler"},
		{"Fossil island wyverns", "Ancient wyvern", "Long-tailed wyvern", "Spitting wyvern", "Taloned wyvern"},
		{"Frost dragons"},
		{"Gargoyles", "Dusk", "Dawn"},
		{"General Graardor"},
		{"Ghosts", "Death wing", "Tortured soul", "Forgotten Soul", "Revenant"},
		{"Ghouls"},
		{"The Giant Mole"},
		{"Goblins", "Sergeant Strongstack", "Sergeant Grimspike", "Sergeant Steelwill"},
		{"Greater demons", "K'ril Tsutsaroth", "Tstanon Karlak", "Skotizo", "Tormented Demon"},
		{"Green dragons", "Elvarg"},
		{"The Grotesque Guardians", "Dusk", "Dawn"},
		{"Gryphons"},
		{"Harpie bug swarms"},
		{"Hellhounds", "Cerberus"},
		{"Hill giants", "Cyclops", "Reanimated giant", "Obor"},
		{"Hobgoblins"},
		{"Hydras"},
		{"Icefiends"},
		{"Ice giants", "Eldric the Ice King"},
		{"Ice warriors", "Icelord"},
		{"Infernal mages", "Malevolent mage"},
		{"TzTok-Jad"},
		{"Jellies", "Jelly"},
		{"Jungle horrors"},
		{"Kalphites"},
		{"The Kalphite Queen"},
		{"Killerwatts"},
		{"The King Black Dragon"},
		{"The Cave Kraken Boss", "Kraken"},
		{"Kree'arra"},
		{"K'ril Tsutsaroth"},
		{"Kurask"},
		{"Lava Dragons", "Lava dragon"},
		{"Lesser demons", "Zakl'n Gritch"},
		{"Lesser Nagua", "Sulphur Nagua", "Frost Nagua", "Amoxliatl"},
		{"Lizardmen", "Lizardman"},
		{"Lizards"},
		{"The Maggot King"},
		{"Magic axes"},
		{"Mammoths"},
		{"Metal dragons", "Bronze dragon", "Iron Dragon", "Steel dragon", "Mithril dragon", "Adamant dragon", "Rune dragon"},
		{"Minotaurs"},
		{"Mogres"},
		{"Molanisks"},
		{"Monkeys", "Tortured gorilla", "Demonic gorilla", "Padulah"},
		{"Moss giants", "Bryophyta"},
		{"Mutated zygomites", "Zygomite", "Fungi"},
		{"Nechryael", "Nechryarch"},
		{"Ogres", "Enclave guard", "Mogre", "Ogress", "Skogre", "Zogre"},
		{"Otherworldly beings"},
		{"The Phantom Muspah"},
		{"Pirates", "Pirate"},
		{"Pyrefiends", "Flaming pyrelord"},
		{"Rats"},
		{"Red dragons"},
		{"Revenants"},
		{"Rockslugs"},
		{"Rogues"},
		{"Sarachnis"},
		{"Scabarites", "Scarab swarm", "Locust rider", "Scarab mage", "Small Scarab"},
		{"Scorpia"},
		{"Scorpions", "Scorpia", "Lobstrosity"},
		{"Sea snakes"},
		{"Shades", "Loar", "Phrin", "Riyl", "Asyn", "Fiyr", "Urium"},
		{"Shadow warriors"},
		{"The Shellbane Gryphon"},
		{"Skeletal wyverns"},
		{"Skeletons", "Vet'ion", "Calvar'ion", "Skeletal Mystic"},
		{"Smoke devils"},
		{"Sourhogs"},
		{"Spiders", "Kalrag", "Sarachnis", "Venenatis", "Spindel", "Araxxor", "Araxyte"},
		{"Spiritual creatures", "Spiritual ranger", "Spiritual mage", "Spiritual warrior"},
		{"Suqahs"},
		{"Terror dogs"},
		{"The Leviathan"},
		{"The Whisperer"},
		{"The Thermonuclear Smoke Devil"},
		{"Trolls", "Dad", "Arrg", "Stick", "Kraka", "Pee Hat", "Rock", "Twig", "Berry"},
		{"Turoth"},
		{"Tzhaar", "TzTok-Jad", "TzKal-Zuk"},
		{"Vampyres", "Vyrewatch"},
		{"Vardorvis"},
		{"Venators"},
		{"Venenatis"},
		{"Vet'ion"},
		{"Vorkath"},
		{"Wall beasts"},
		{"Warped Creatures", "Warped terrorbird", "Warped tortoise", "Mutated terrorbird", "Mutated tortoise"},
		{"Waterfiends"},
		{"Werewolves", "Werewolf"},
		{"Wolves", "Wolf"},
		{"Wyrms", "Wyrmling", "Strykewyrm"},
		{"Commander Zilyana"},
		{"Zombies", "Undead", "Vorkath", "Zogre"},
		{"TzKal-Zuk"},
		{"Zulrah"},
	};

	private static final Map<String, String[]> BY_NAME = new HashMap<>();

	static
	{
		for (String[] task : TASKS)
		{
			BY_NAME.put(key(task[0]), task);
		}
	}

	private SlayerTasks()
	{
	}

	/**
	 * The task's name as the Slayer plugin writes it, or the name as given for a task it doesn't know.
	 */
	public static String displayName(String taskName)
	{
		String[] task = BY_NAME.get(key(taskName));
		return task != null ? task[0] : taskName;
	}

	/**
	 * Patterns that find the monsters counting for a task in a lowercase monster name.
	 */
	public static List<Pattern> targets(String taskName)
	{
		List<Pattern> patterns = new ArrayList<>();
		String[] task = BY_NAME.get(key(taskName));
		if (task != null)
		{
			for (int i = 1; i < task.length; i++)
			{
				patterns.add(pattern(task[i]));
			}
		}
		patterns.add(pattern(key(taskName).replaceAll("s$", "")));
		return Collections.unmodifiableList(patterns);
	}

	/**
	 * Whether a monster with this name counts for the task.
	 */
	public static boolean counts(List<Pattern> targets, String monsterName)
	{
		if (monsterName == null)
		{
			return false;
		}
		String name = monsterName.replace('\u00A0', ' ').toLowerCase(Locale.ENGLISH);
		for (Pattern target : targets)
		{
			if (target.matcher(name).find())
			{
				return true;
			}
		}
		return false;
	}

	private static Pattern pattern(String name)
	{
		return Pattern.compile("(?:\\s|^)" + Pattern.quote(name.toLowerCase(Locale.ENGLISH)) + "(?:\\s|$)", Pattern.CASE_INSENSITIVE);
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ENGLISH);
	}
}
