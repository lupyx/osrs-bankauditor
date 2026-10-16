# Bank Auditor

A RuneLite plugin for people whose bank fills up with loot they meant to sell.

It remembers what you got, from what, when and where. When you open your bank it shows how much of each stack came from loot, marks what you can sell, and leaves the things you actually use alone.

## What it does

**Loot in the bank.** Bank items that came from loot get a small green number in the corner with the looted amount. Hover an item to see where it came from:

```
Death rune
From loot: 1,200 of 6,200
800 from Abyssal demon (lvl 124), 2 h ago, Catacombs of Kourend, world 302
400 from Greater demon (lvl 92), 1 day ago, Chasm of Fire, world 330
Sellable: 1,200 (240K gp)
```

**Loot stacks vs your own stacks.** If you had 5,000 death runes and looted 1,200 more, the plugin knows that 1,200 of the 6,200 are loot. Only those count as sellable, so you can sell the extra runes and keep your own supply.

**Selling.** Sellable items get a highlight in the bank, and in your inventory while the Grand Exchange is open. Type `tag:sellable` (or just `sellable`) in the bank search to see only the sellable items, then withdraw them as notes and sell them in one go. The side panel has a sell list sorted by value.

**Keep list.** Items you use never show up as sellable. Right click an item in the bank or inventory and pick **Keep**, or edit the list in the config. Each entry is one item name, one per line or comma separated:

| Entry | Meaning |
| --- | --- |
| `Shark` | never sell sharks |
| `* rune` | never sell any rune (`*` is a wildcard) |
| `Death rune:5k` | keep 5,000 death runes, anything above that can be sold |
| `Cannonball:2k` | keep 2,000 (k, m and b work) |

**Loot history.** The side panel lists recent drops with the source, how long ago, and the area and world. Hover the area to see the exact tile.

## Settings

Every part can be turned off on its own.

| Section | Setting | Default |
| --- | --- | --- |
| Loot tracking | Track loot | on |
| | Keep history for | 30 days |
| | Max loot records | 500 |
| | Side panel | on |
| Bank | Loot tooltips | on |
| | Show looted amount | on |
| | Looted amount color | green |
| | Highlight sellable in bank | on |
| | Sellable bank tag | on |
| Selling | Sellable items | Looted items only (or every tradeable item you own) |
| | Minimum stack value | 0 gp (list everything) |
| | Keep list | empty |
| | Keep menu option | on |
| | Highlight at Grand Exchange | on |
| | Highlight style | underline (or outline, or corner mark) |
| | Sellable color | yellow |

## How the loot count works

The client only knows your whole bank while it is open, so the plugin settles its numbers then. Loot that drops is noted as pending. The next time the bank is open and nothing has moved for a couple of ticks, it compares what you own (bank, inventory, worn items and items in Grand Exchange sell offers) with what it saw last time:

- If you have less than it expected, the difference comes out of the loot first. That covers loot you never picked up, sold, alched, ate or dropped. Anything above what you had before is treated as loot, so using or selling items eats into the loot before your own supply.
- If you have more than it expected, the extra is yours (bought, crafted, and so on). The exception is loot that went missing earlier in the same bank visit, like when you empty a looting bag into the bank.

Worn items and items in a sell offer count as owned for this, but they are in use or already being sold, so they are never shown as sellable. An offer you abort keeps its items counted as loot. The client can't see inside the collection box, so after you abort a sale the plugin assumes that any of that item you gain afterwards was collected from the box.

Between bank visits the numbers follow the same rule as you go. Sell 500 looted runes at the Grand Exchange and the sellable count drops by 500 straight away, so the highlight never moves on to your own stack.

Data is saved per account in your RuneLite profile.

## Loot sources

NPC kills, PvP kills and pickpocketing are always tracked. Chests, clue caskets, raids, minigames and the rest come from RuneLite's Loot Tracker plugin, so keep it enabled if you want those too. While the Loot Tracker is on, Bank Auditor reads everything from it so nothing gets counted twice. Kills in Last Man Standing and Soul Wars are skipped, like the Loot Tracker does.

## Known limits

- Potion storage, the rune pouch, the looting bag (outside the bank visit where you empty it) and the group storage of group ironmen are not part of the bank as far as the client is concerned, so loot that goes there looks used up.
- For kills, the plugin remembers what you owned when the drop happened. Chest, clue and other rewards often land straight in your inventory, so the first time an item comes from one of those (or before you have opened your bank since logging in) the plugin assumes the whole reward is still there at the next bank visit.
- If you close the bank at the same moment as a withdraw or deposit, the client never receives the new bank contents. The plugin notices this and waits for your next bank visit before settling anything.
- The keep list editor in the config page does not refresh by itself. If it is open while you use the Keep menu option, close and reopen it before typing in it, or your edit will undo the menu change.
- The `sellable` bank tag is a Bank Tags search, so it needs the Bank Tags plugin (on by default). It does not work in the Grand Exchange item search.
- Area names come from the table RuneLite uses for Discord status. Places it does not know show up as a region id.

## Building and testing

Tests run in Docker, so you only need Docker installed:

```
docker compose run --rm --build test
```

That builds the plugin with Gradle and JDK 11 inside the container and runs the unit tests. Pass a different Gradle task at the end if you want one, for example `docker compose run --rm --build test test`. Downloaded dependencies are kept in a Docker volume between runs.

To try the plugin in a real client, run `./gradlew run` (needs JDK 11 or newer). It starts RuneLite in developer mode with Bank Auditor loaded.

GitHub Actions runs the same Docker build on every push.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
