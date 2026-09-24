package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.container.AlchemyTableObject;
import necesse.level.gameObject.container.CarpentersBenchObject;
import necesse.level.gameObject.container.IronAnvilObject;
import necesse.level.gameObject.container.WorkstationDuoObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.object.AlchemyTableObjectEntity;
import opusliews.object.AnvilObjectEntity;
import opusliews.object.CarpentersBenchObjectEntity;
import opusliews.object.CrudeAnvilObject;
import opusliews.object.CrudeAnvilObjectEntity;
import opusliews.object.WorkstationObjectEntity;

@ModMethodPatch(
		target = GameObject.class,
		name = "getNewObjectEntity",
		arguments = {Level.class, int.class, int.class}
)
public class CraftingStationObjectEntityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This GameObject object,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Return(readOnly = false) ObjectEntity result
	) {
		if (result != null || !object.isMultiTileMaster()) return;

		if (object instanceof CrudeAnvilObject) {
			result = new CrudeAnvilObjectEntity(level, tileX, tileY);
		}
		else if (object instanceof IronAnvilObject) {
			result = new AnvilObjectEntity(level, tileX, tileY);
		}
		else if (object instanceof WorkstationDuoObject) {
			result = new WorkstationObjectEntity(level, tileX, tileY);
		}
		else if (object instanceof AlchemyTableObject) {
			result = new AlchemyTableObjectEntity(level, tileX, tileY);
		}
		else if (object instanceof CarpentersBenchObject) {
			result = new CarpentersBenchObjectEntity(level, tileX, tileY);
		}
	}
}
