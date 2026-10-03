package opusliews.patches;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.levelData.settlementData.SettlementWorkZoneManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import net.bytebuddy.asm.Advice;
import opusliews.hud.SurfaceSettlementZoneHudDrawElement;

@ModMethodPatch(
		target = SettlementWorkZone.class,
		name = "getHudDrawElement",
		arguments = {int.class, BooleanSupplier.class, Color.class, Color.class}
)
public class SettlementWorkZoneHudLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementWorkZone zone,
			@Advice.FieldValue("manager") SettlementWorkZoneManager manager,
			@Advice.Return(readOnly = false) HudDrawElement result
	) {
		if (result != null && !(result instanceof SurfaceSettlementZoneHudDrawElement)) {
			Integer settlementUniqueID = manager == null || manager.data == null ? null : manager.data.uniqueID;
			result = new SurfaceSettlementZoneHudDrawElement(result, settlementUniqueID, zone.getUniqueID());
		}
	}
}
