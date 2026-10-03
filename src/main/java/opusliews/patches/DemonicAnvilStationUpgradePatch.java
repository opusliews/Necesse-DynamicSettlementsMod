package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.level.gameObject.container.CraftingStationUpgrade;
import necesse.level.gameObject.container.DemonicAnvilObject;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = DemonicAnvilObject.class, name = "getStationUpgrade", arguments = {})
public class DemonicAnvilStationUpgradePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) CraftingStationUpgrade result) {
		result = new CraftingStationUpgrade(
			ObjectRegistry.getObject("tungstenanvil"),
			new Ingredient[]{
				new Ingredient("thicktungstenplate", 2),
				new Ingredient("quartz", 8)
			}
		);

		if (Logging.logEnabled) {
			Logging.logMessage("Patched Demonic Anvil -> Tungsten Anvil upgrade cost: 2 thicktungstenplate, 8 quartz");
		}
	}
}
