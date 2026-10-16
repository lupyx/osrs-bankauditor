package io.lupix.runelite.bankauditor;

import java.io.File;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with the plugin loaded straight from the source tree, for running and debugging it from an IDE.
 * {@code ./gradlew run} uses the sideloaded jar instead.
 */
public class BankAuditorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		File sideloaded = new File(RuneLite.RUNELITE_DIR, "sideloaded-plugins/bank-auditor.jar");
		if (sideloaded.exists())
		{
			System.err.println("Bank Auditor is also in " + sideloaded + " and will be loaded twice."
				+ " Run ./gradlew unsideload first.");
		}
		ExternalPluginManager.loadBuiltin(BankAuditorPlugin.class);
		RuneLite.main(args);
	}
}
