package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.Zoning;
import necesse.inventory.container.settlement.data.SettlementRestrictZoneData;
import net.bytebuddy.asm.Advice;
import opusliews.hud.LevelAwareRestrictZoning;

import java.awt.Rectangle;
import java.util.function.Supplier;

@ModMethodPatch(target = SettlementRestrictZoneData.class, name = "getZoning", arguments = {Supplier.class})
public class SettlementRestrictZoneDataLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementRestrictZoneData data, @Advice.Argument(0) Supplier<Rectangle> limits, @Advice.Return(readOnly = false) Zoning result) {
		if (result != null && !(result instanceof LevelAwareRestrictZoning)) result = new LevelAwareRestrictZoning(data.uniqueID, limits, result);
	}
}
