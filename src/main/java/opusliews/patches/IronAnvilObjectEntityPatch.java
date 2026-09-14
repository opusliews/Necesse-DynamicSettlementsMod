package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.container.IronAnvilObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.object.IronAnvilObjectEntity;

@ModMethodPatch(
		target = GameObject.class,
		name = "getNewObjectEntity",
		arguments = {Level.class, int.class, int.class}
)
public class IronAnvilObjectEntityPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This GameObject object,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Return(readOnly = false) ObjectEntity result
	) {
		if (result == null && object instanceof IronAnvilObject) {
			result = new IronAnvilObjectEntity(level, tileX, tileY);
		}
	}
}
