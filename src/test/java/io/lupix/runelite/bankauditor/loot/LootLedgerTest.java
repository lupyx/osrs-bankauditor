package io.lupix.runelite.bankauditor.loot;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class LootLedgerTest
{
	private static final int DEATH_RUNE = 560;
	private static final int SHARK = 385;
	private static final int WHIP = 4151;

	private LootLedger ledger;

	@Before
	public void setUp()
	{
		ledger = new LootLedger();
	}

	private static Map<Integer, Long> owned(Object... pairs)
	{
		Map<Integer, Long> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((Integer) pairs[i], ((Number) pairs[i + 1]).longValue());
		}
		return map;
	}

	@Test
	public void lootIsPendingUntilReconciled()
	{
		ledger.addLoot(DEATH_RUNE, 300);

		assertEquals(0, ledger.getLooted(DEATH_RUNE));
		assertEquals(300, ledger.getPending(DEATH_RUNE));
		assertEquals(300, ledger.estimateLooted(DEATH_RUNE, 5300));
	}

	@Test
	public void firstSightingAssumesLootIsThereWhenItFits()
	{
		ledger.addLoot(DEATH_RUNE, 300);

		assertTrue(ledger.reconcile(owned(DEATH_RUNE, 5300)));

		assertEquals(300, ledger.getLooted(DEATH_RUNE));
		assertEquals(0, ledger.getPending(DEATH_RUNE));
	}

	@Test
	public void firstSightingNeverCountsMoreLootThanOwned()
	{
		ledger.addLoot(WHIP, 2);

		ledger.reconcile(owned(WHIP, 1));

		assertEquals(1, ledger.getLooted(WHIP));
	}

	@Test
	public void lootOnTopOfExistingStackIsSeparatedFromWhatWasThere()
	{
		// The rune story: 5000 death runes of my own, then 1200 from loot
		Map<Integer, LootLedger.Entry> saved = new HashMap<>();
		saved.put(DEATH_RUNE, new LootLedger.Entry(0, 0, 0, 5000L));
		ledger = new LootLedger(saved);

		ledger.addLoot(DEATH_RUNE, 700);
		ledger.addLoot(DEATH_RUNE, 500);
		ledger.reconcile(owned(DEATH_RUNE, 6200));

		assertEquals(1200, ledger.getLooted(DEATH_RUNE));
	}

	@Test
	public void lootThatWasNeverPickedUpIsDropped()
	{
		ledger.addLoot(SHARK, 5);
		ledger.reconcile(owned(SHARK, 105));
		assertEquals(5, ledger.getLooted(SHARK));

		// Another 3 sharks drop but the player leaves them on the floor
		ledger.addLoot(SHARK, 3);
		ledger.reconcile(owned(SHARK, 105));

		assertEquals(5, ledger.getLooted(SHARK));
	}

	@Test
	public void usingItemsEatsIntoLootBeforeTheOwnSupply()
	{
		ledger.addLoot(DEATH_RUNE, 1200);
		ledger.reconcile(owned(DEATH_RUNE, 6200));

		// Cast some spells
		ledger.reconcile(owned(DEATH_RUNE, 5900));

		assertEquals(900, ledger.getLooted(DEATH_RUNE));
	}

	@Test
	public void sellingTheLootLeavesTheOwnSupply()
	{
		ledger.addLoot(DEATH_RUNE, 1200);
		ledger.reconcile(owned(DEATH_RUNE, 6200));

		// Put the extra 1200 on the GE
		ledger.reconcile(owned(DEATH_RUNE, 5000));
		ledger.endBankVisit();

		assertEquals(0, ledger.getLooted(DEATH_RUNE));

		// Buying more later doesn't turn into loot
		ledger.reconcile(owned(DEATH_RUNE, 8000));
		assertEquals(0, ledger.getLooted(DEATH_RUNE));
	}

	@Test
	public void buyingMoreDoesNotCountAsLoot()
	{
		ledger.addLoot(SHARK, 10);
		ledger.reconcile(owned(SHARK, 10));

		ledger.reconcile(owned(SHARK, 210));

		assertEquals(10, ledger.getLooted(SHARK));
	}

	@Test
	public void lootThatReappearsInTheSameBankVisitIsRestored()
	{
		// Wilderness trip: 50 looted runes sit in the looting bag when the bank opens
		ledger.addLoot(DEATH_RUNE, 1);
		ledger.reconcile(owned(DEATH_RUNE, 1));
		ledger.addLoot(DEATH_RUNE, 50);

		ledger.reconcile(owned(DEATH_RUNE, 1));
		assertEquals(1, ledger.getLooted(DEATH_RUNE));
		assertEquals(50, ledger.getMissing(DEATH_RUNE));

		// The player empties the looting bag into the bank
		ledger.reconcile(owned(DEATH_RUNE, 51));

		assertEquals(51, ledger.getLooted(DEATH_RUNE));
		assertEquals(0, ledger.getMissing(DEATH_RUNE));
	}

	@Test
	public void missingLootIsGoneAfterTheBankCloses()
	{
		ledger.addLoot(DEATH_RUNE, 1);
		ledger.reconcile(owned(DEATH_RUNE, 1));
		ledger.addLoot(DEATH_RUNE, 50);
		ledger.reconcile(owned(DEATH_RUNE, 1));

		ledger.endBankVisit();
		ledger.reconcile(owned(DEATH_RUNE, 51));

		assertEquals(1, ledger.getLooted(DEATH_RUNE));
	}

	@Test
	public void restoringNeverExceedsWhatWentMissing()
	{
		ledger.addLoot(SHARK, 10);
		ledger.reconcile(owned(SHARK, 10));
		ledger.reconcile(owned(SHARK, 4));
		assertEquals(4, ledger.getLooted(SHARK));

		ledger.reconcile(owned(SHARK, 100));

		assertEquals(10, ledger.getLooted(SHARK));
		assertEquals(0, ledger.getMissing(SHARK));
	}

	@Test
	public void movingItemsBetweenBankAndInventoryChangesNothing()
	{
		ledger.addLoot(SHARK, 20);
		ledger.reconcile(owned(SHARK, 120));

		// Same total, it just moved
		assertFalse(ledger.reconcile(owned(SHARK, 120)));
		assertEquals(20, ledger.getLooted(SHARK));
	}

	@Test
	public void itemsNotInTheTotalsAreTreatedAsGone()
	{
		ledger.addLoot(WHIP, 1);
		ledger.reconcile(owned(WHIP, 1));

		ledger.reconcile(owned());

		assertEquals(0, ledger.getLooted(WHIP));
		assertEquals(Long.valueOf(0), ledger.snapshot().get(WHIP).getLastTotal());
	}

	@Test
	public void forgetKeepsTheBaseline()
	{
		ledger.addLoot(SHARK, 20);
		ledger.reconcile(owned(SHARK, 120));

		ledger.forget(SHARK);
		assertEquals(0, ledger.getLooted(SHARK));

		ledger.addLoot(SHARK, 5);
		ledger.reconcile(owned(SHARK, 125));
		assertEquals(5, ledger.getLooted(SHARK));
	}

	@Test
	public void estimateIsCappedByWhatIsOwned()
	{
		ledger.addLoot(SHARK, 20);
		ledger.reconcile(owned(SHARK, 120));
		ledger.addLoot(SHARK, 5);

		assertEquals(25, ledger.estimateLooted(SHARK, 125));
		assertEquals(10, ledger.estimateLooted(SHARK, 10));
		assertEquals(0, ledger.estimateLooted(SHARK, 0));
		assertEquals(0, ledger.estimateLooted(WHIP, 10));
	}

	@Test
	public void ignoresNonPositiveLoot()
	{
		ledger.addLoot(SHARK, 0);
		ledger.addLoot(SHARK, -4);

		assertEquals(0, ledger.size());
	}

	@Test
	public void savedStateRoundTripsAndDropsMissing()
	{
		ledger.addLoot(DEATH_RUNE, 1);
		ledger.reconcile(owned(DEATH_RUNE, 1));
		ledger.addLoot(DEATH_RUNE, 50);
		ledger.reconcile(owned(DEATH_RUNE, 1));
		ledger.addLoot(SHARK, 3);

		LootLedger restored = new LootLedger(ledger.snapshot());

		assertEquals(1, restored.getLooted(DEATH_RUNE));
		assertEquals(0, restored.getMissing(DEATH_RUNE));
		assertEquals(3, restored.getPending(SHARK));
	}

	@Test
	public void badSavedDataIsClampedOnReconcile()
	{
		Map<Integer, LootLedger.Entry> saved = new HashMap<>();
		saved.put(SHARK, new LootLedger.Entry(500, -3, 0, 100L));
		LootLedger restored = new LootLedger(saved);

		restored.reconcile(owned(SHARK, 100));

		assertEquals(100, restored.getLooted(SHARK));
	}

	@Test
	public void lootedItemIdsListsConfirmedAndPending()
	{
		ledger.addLoot(SHARK, 1);
		ledger.reconcile(owned(SHARK, 1));
		ledger.addLoot(WHIP, 1);
		ledger.addLoot(DEATH_RUNE, 1);
		ledger.reconcile(owned(SHARK, 1, WHIP, 1));
		ledger.addLoot(DEATH_RUNE, 4);

		assertEquals(3, ledger.lootedItemIds().size());
		ledger.forget(SHARK);
		assertEquals(2, ledger.lootedItemIds().size());
	}
}
