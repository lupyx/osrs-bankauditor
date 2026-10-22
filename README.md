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

**Loot history.** The side panel lists recent drops with the source, how long ago, and the area and world. Hover the area to see the exact tile. The small buttons above the list switch between every drop on its own and drops combined by location, by source or by time range. The time range view has Hour, Day, Week and Month buttons for how much time each box covers. Hover a button to see what it does. A combined box adds up the items and their value, and says how many drops went into it. The panel remembers the last view you picked. The search bar above it finds drops by source, location or item name, and the Source, Location and Item toggles under it choose what it looks at. Matching drops are shown whole, with the matching text highlighted.

Each box shows the boss icon when the source has one on the hiscores (Vorkath, Zulrah, raids and so on), and every item line has its icon and quantity, with its Grand Exchange and high alchemy value in a column on the right. A crossed out GE or HA means the item can't be traded or alched; hover it to see which. Coins only show their amount. The header shows what the box is worth in bold, taking the better of GE and HA for each item, and the footer adds up the GE and HA values. The value color on the left edge starts at the first item. A box with more than 15 items has a Show more line you can click to see the rest. Kill drops you never pick up are grayed out once they vanish from the floor, with "not picked up" on hover, and don't count toward the box totals. The plugin decides this from the pile itself: a pile that disappears while only you can see it was yours to take, one that runs out its timer was left, and one that disappears after other players can see it only counts as picked up if you clicked Take on it. Teleporting away from loot counts as leaving it.

Casting High Level Alchemy on looted items is noticed too. The item gets a line of its own saying it was high alched, with the gold it gave, and that gold counts toward the box totals. Only items the plugin counts as loot are marked, starting with the newest drop that still has one. Stacks worth a lot get colored by value tier like ground items: 20K, 100K, 1M and 10M gp by default. The tiers and colors are in the Loot colors section of the settings. Clear loot data asks you to confirm first, with Cancel as the default.

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
| Loot colors | Color by value | on |
| | Low, medium, high, insane value | 20K, 100K, 1M, 10M gp |
| | Their colors | blue, green, orange, pink |

## How the loot count works

The client only knows your whole bank while it is open, so the plugin settles its numbers then. Loot that drops is noted as pending. The next time the bank is open and nothing has moved for a couple of ticks, it compares what you own (bank, inventory, worn items and items in Grand Exchange sell offers) with what it saw last time:

- If you have less than it expected, the difference comes out of the loot first. That covers loot you never picked up, sold, alched, ate or dropped. Anything above what you had before is treated as loot, so using or selling items eats into the loot before your own supply.
- If you have more than it expected, the extra is yours (bought, crafted, and so on). The exception is loot that went missing earlier in the same bank visit, like when you empty a looting bag into the bank.

Worn items and items in a sell offer count as owned for this, but they are in use or already being sold, so they are never shown as sellable. An offer you abort keeps its items counted as loot until you collect them. The client can't see inside the collection box, so if you take the items out but leave the coins, those items count twice until the slot is empty. That only makes the plugin show less loot, never more.

Between bank visits the numbers follow the same rule as you go. Sell 500 looted runes at the Grand Exchange and the sellable count drops by 500 straight away, so the highlight never moves on to your own stack.

Data is saved per account in your RuneLite profile.

## Loot sources

NPC kills, PvP kills and pickpocketing are always tracked. Chests, clue caskets, raids, minigames and the rest come from RuneLite's Loot Tracker plugin, so keep it enabled if you want those too. While the Loot Tracker is on, Bank Auditor reads everything from it so nothing gets counted twice. Kills in Last Man Standing and Soul Wars are skipped, like the Loot Tracker does.

## Known limits

- Potion storage, the rune pouch, the looting bag (outside the bank visit where you empty it) and the group storage of group ironmen are not part of the bank as far as the client is concerned, so loot that goes there looks used up.
- For kills, the plugin remembers what you owned when the drop happened. Chest, clue and other rewards often land straight in your inventory, so the first time an item comes from one of those (or before you have opened your bank since logging in) the plugin assumes the whole reward is still there at the next bank visit.
- If you close the bank at the same moment as a withdraw or deposit, the client never receives the new bank contents. The plugin works out the change from your inventory instead. If it was a looting bag or a sack you emptied into the bank, it waits for your next bank visit.
- Items you collect from the Grand Exchange straight to your bank, or put in through a deposit box, aren't seen until you next open the bank.
- The keep list editor in the config page does not refresh by itself. If it is open while you use the Keep menu option, close and reopen it before typing in it, or your edit will undo the menu change.
- The `sellable` bank tag is a Bank Tags search, so it needs the Bank Tags plugin (on by default). It does not work in the Grand Exchange item search.
- Area names come from the table RuneLite uses for Discord status. Places it does not know show up as a region id.

## Installing

Bank Auditor is not on the Plugin Hub yet. RuneLite only loads plugins from outside the hub in developer mode, and the official launcher always turns developer mode off, so the build starts the client for you instead.

1. Install JDK 11 or newer, then clone the repository.
2. Run `./gradlew run` from the repository folder (`gradlew.bat run` on Windows). It builds the plugin, copies it to `.runelite/sideloaded-plugins` in your home folder, and starts RuneLite in developer mode. The client uses your normal `.runelite` folder, so your profiles, settings and Plugin Hub plugins all come along, and you can play as usual.
3. RuneLite picks your active profile like it normally does. To use a different one, add its name: `./gradlew run -Pprofile=Main`.
4. With a Jagex account, the client started this way can't log in through the Jagex Launcher. Open the RuneLite launcher settings ("RuneLite (configure)" on Windows, or run the launcher with `--configure`), add `--insecure-write-credentials` to the client arguments, and start RuneLite once from the Jagex Launcher. That saves a login token to `.runelite/credentials.properties` in your home folder, which the developer client then uses. Treat that file like a password, and delete it when you are done.

The launcher doesn't update this client. After a game update, if it says it is out of date, run `./gradlew run --refresh-dependencies` to get the newest RuneLite.

`./gradlew sideload` only copies the jar, and `./gradlew unsideload` removes it. To debug in IntelliJ, open the folder as a Gradle project and run the `main` method in `src/test/java/io/lupix/runelite/bankauditor/BankAuditorPluginTest.java` with the VM option `-ea` and the program argument `--developer-mode`. That loads the plugin from the source, so run `unsideload` first or it shows up twice.

To get it on the Plugin Hub so it installs like any other plugin:

1. Make this repository public.
2. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) and add a file `plugins/bank-auditor` containing:
   ```
   repository=https://github.com/lupyx/osrs-bankauditor.git
   commit=<full hash of the commit to release>
   ```
3. Open a pull request. The RuneLite team reviews the code, and once it is merged the plugin shows up in the Plugin Hub in every client. Each update is another pull request that changes the commit hash.

## Building and testing

Tests run in Docker, so you only need Docker installed:

```
docker compose run --rm --build test
```

That builds the plugin with Gradle and JDK 11 inside the container and runs the unit tests. Pass a different Gradle task at the end if you want one, for example `docker compose run --rm --build test test`. Downloaded dependencies are kept in a Docker volume between runs.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
