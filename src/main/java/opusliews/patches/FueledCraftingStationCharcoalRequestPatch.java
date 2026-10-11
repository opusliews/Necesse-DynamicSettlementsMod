package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.gameObject.container.FueledCraftingStationObject;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.SettlementRequestOptions;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = FueledCraftingStationObject.class,
		name = "getFuelRequestOptions",
		arguments = {Level.class, int.class, int.class}
)
public class FueledCraftingStationCharcoalRequestPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Return(readOnly = false) SettlementRequestOptions result) {
		// This advice is inlined into the vanilla target class. Do not instantiate an
		// anonymous subclass here: its generated $1 class is not public and cannot
		// be accessed from FueledCraftingStationObject after transformation.
		result = new CharcoalSettlementRequestOptions();
	}
}
