package opusliews.patches;

import java.awt.Point;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.path.TilePathfinding;
import net.bytebuddy.asm.Advice;
import opusliews.deephole.DeepHolePathing;

@ModMethodPatch(
		target = TilePathfinding.class,
		name = "checkCanPassDoorOrTileNoRecord",
		arguments = {Point.class}
)
public class DeepHolePathTargetNoRecordPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This TilePathfinding finder,
			@Advice.Argument(0) Point targetTile,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (result && DeepHolePathing.pathTargetContainsDeepHole(finder, targetTile)) result = false;
	}
}
