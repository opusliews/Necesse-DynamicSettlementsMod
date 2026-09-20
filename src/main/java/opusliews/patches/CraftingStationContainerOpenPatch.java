package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.object.CraftingStationContainer;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.crafting.CraftingStationFeature;
import opusliews.object.DynamicCraftingStationObjectEntity;
import necesse.level.maps.LevelObject;

@ModMethodPatch(
		target = CraftingStationContainer.class,
		name = "openAndSendContainer",
		arguments = {int.class, ServerClient.class, Level.class, int.class, int.class}
)
public class CraftingStationContainerOpenPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.Argument(value = 0, readOnly = false) int containerID,
			@Advice.Argument(2) Level level,
			@Advice.Argument(3) int tileX,
			@Advice.Argument(4) int tileY
	) {
		LevelObject object = level.getLevelObject(tileX, tileY);
		LevelObject master = object == null ? null : (LevelObject)object.getMasterLevelObject().orElse(object);
		if (master != null && master.getObjectEntity() instanceof DynamicCraftingStationObjectEntity) {
			containerID = CraftingStationFeature.containerID;
		}
	}
}
