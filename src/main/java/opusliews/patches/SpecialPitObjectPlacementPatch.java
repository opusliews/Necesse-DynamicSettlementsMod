package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.tile.CharcoalPitSystem;

@ModMethodPatch(
		target = GameObject.class,
		name = "canPlace",
		arguments = {Level.class, int.class, int.class, int.class, int.class, boolean.class, boolean.class}
)
public class SpecialPitObjectPlacementPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Level level,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Return(readOnly = false) String result
	) {
		if (CharcoalPitSystem.isPitTile(level, tileX, tileY)) {
			result = "occupied";
		}
	}
}
