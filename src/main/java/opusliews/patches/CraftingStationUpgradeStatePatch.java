package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.object.DynamicCraftingStationObjectEntity;

@ModMethodPatch(
		target = CraftingStationObject.class,
		name = "performUpgrade",
		arguments = {GameObject.class, Level.class, int.class, int.class, ServerClient.class}
)
public class CraftingStationUpgradeStatePatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.Argument(1) Level level,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Local("dynamicStationState") DynamicCraftingStationObjectEntity.StationState state
	) {
		state = DynamicCraftingStationObjectEntity.captureStationState(level, tileX, tileY);
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(1) Level level,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Local("dynamicStationState") DynamicCraftingStationObjectEntity.StationState state
	) {
		DynamicCraftingStationObjectEntity.applyStationState(level, tileX, tileY, state);
	}
}
