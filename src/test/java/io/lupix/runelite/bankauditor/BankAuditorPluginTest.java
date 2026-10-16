package io.lupix.runelite.bankauditor;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with the plugin loaded, for trying it out locally ({@code ./gradlew run}).
 */
public class BankAuditorPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankAuditorPlugin.class);
		RuneLite.main(args);
	}
}
