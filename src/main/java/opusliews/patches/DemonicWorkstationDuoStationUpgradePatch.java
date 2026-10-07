package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.recipe.Ingredient;
import necesse.level.gameObject.container.CraftingStationUpgrade;
import necesse.level.gameObject.container.DemonicWorkstationDuoObject;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

@ModMethodPatch(target = DemonicWorkstationDuoObject.class, name = "getStationUpgrade", arguments = {})
public class DemonicWorkstationDuoStationUpgradePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) CraftingStationUpgrade result) {
		result = new CraftingStationUpgrade(
			ObjectRegistry.getObject("tungstenworkstation"),
			new Ingredient[]{
				new Ingredient("tungstenbar", 8),
				new Ingredient("quartz", 4)
			}
		);

		if (Logging.logEnabled) {
			Logging.logMessage("Patched Demonic Workstation Duo -> Tungsten Workstation upgrade cost: 8 tungstenbar, 4 quartz");
		}
	}
}
