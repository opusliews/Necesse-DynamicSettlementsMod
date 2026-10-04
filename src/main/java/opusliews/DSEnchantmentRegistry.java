package opusliews;

import necesse.engine.modifiers.ModifierList;
import necesse.engine.registries.EnchantmentRegistry;
import necesse.inventory.enchants.ItemEnchantment;
import opusliews.logging.Logging;

public final class DSEnchantmentRegistry {
	public static final String unbreakingStringID = "dsunbreaking";
	public static int unbreakingID = -1;

	private DSEnchantmentRegistry() {
	}

	public static void registerEnchantments() {
		unbreakingID = EnchantmentRegistry.registerEnchantment(
				unbreakingStringID,
				new ItemEnchantment(new ModifierList(), 0)
		);
		Logging.logMessage("[MoldEnchant] Registered enchantment stringID=" + unbreakingStringID + " id=" + unbreakingID);
	}
}
