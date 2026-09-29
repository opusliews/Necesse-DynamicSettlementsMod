package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.inventory.container.settlement.data.SettlementSettlerData;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementBedLevelIndicatorSystem;

@ModMethodPatch(target = SettlementSettlerData.class, name = "getIconDrawOptions", arguments = {Level.class, int.class, int.class, int.class})
public class SettlementSettlerDataBedPortraitIndicatorPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This SettlementSettlerData data,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int size,
			@Advice.Argument(2) int drawX,
			@Advice.Argument(3) int drawY,
			@Advice.Return(readOnly = false) DrawOptions result) {
		result = SettlementBedLevelIndicatorSystem.decorateBedPortrait(data, level, size, drawX, drawY, result);
	}
}
