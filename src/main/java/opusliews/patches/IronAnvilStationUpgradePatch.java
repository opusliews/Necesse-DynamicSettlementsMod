package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.level.gameObject.container.CraftingStationUpgrade;
import necesse.level.gameObject.container.IronAnvilObject;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = IronAnvilObject.class, name = "getStationUpgrade", arguments = {})
public class IronAnvilStationUpgradePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) CraftingStationUpgrade result) {
		result = new CraftingStationUpgrade(
			ObjectRegistry.getObject("demonicanvil"),
			new Ingredient[]{new Ingredient("thickdemonicplate", 2)}
		);

		if (Logging.logEnabled) {
			Logging.logMessage("Patched Iron Anvil -> Demonic Anvil upgrade cost: 2 thickdemonicplate");
		}
	}
}
