package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.gfx.camera.GameCamera;
import necesse.inventory.container.settlement.data.SettlementSettlerData;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementBedLevelIndicatorSystem;

@ModMethodPatch(target = SettlementSettlerData.class, name = "getSettlerFlagDrawOptionsTile", arguments = {int.class, int.class, GameCamera.class, SettlementSettlerData.FaceDrawOptionsGetter.class})
public class SettlementSettlerBedFlagDrawContextPatch {
	@Advice.OnMethodEnter
	public static void onEnter() {
		SettlementBedLevelIndicatorSystem.beginBedFlagDraw();
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit() {
		SettlementBedLevelIndicatorSystem.endBedFlagDraw();
	}
}
