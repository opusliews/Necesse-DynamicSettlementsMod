package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.level.gameObject.container.CarpentersBenchObject;
import necesse.level.gameObject.container.CraftingStationUpgrade;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = CarpentersBenchObject.class, name = "getStationUpgrade", arguments = {})
public class CarpentersBenchStationUpgradePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) CraftingStationUpgrade result) {
		result = new CraftingStationUpgrade(
			ObjectRegistry.getObject("tungstencarpentersbench"),
			new Ingredient[]{new Ingredient("tungstenplate", 4)}
		);

		if (Logging.logEnabled) {
			Logging.logMessage("Patched Carpenter's Bench -> Tungsten Carpenter's Bench upgrade cost: 4 tungstenplate");
		}
	}
}
