package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.level.gameObject.container.CraftingStationUpgrade;
import necesse.level.gameObject.container.WorkstationDuoObject;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = WorkstationDuoObject.class, name = "getStationUpgrade", arguments = {})
public class WorkstationDuoStationUpgradePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) CraftingStationUpgrade result) {
		result = new CraftingStationUpgrade(
			ObjectRegistry.getObject("demonicworkstationduo"),
			new Ingredient[]{new Ingredient("demonicplate", 4)}
		);

		if (Logging.logEnabled) {
			Logging.logMessage("Patched Workstation Duo -> Demonic Workstation Duo upgrade cost: 4 demonicplate");
		}
	}
}
