package opusliews.patches;

import java.awt.Point;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.path.TilePathfinding;
import net.bytebuddy.asm.Advice;
import opusliews.deephole.DeepHolePathing;

@ModMethodPatch(
		target = TilePathfinding.class,
		name = "checkCanPassDiagonalWithCost",
		arguments = {Point.class, double.class, Point.class}
)
public class DeepHolePathDiagonalCostPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This TilePathfinding finder,
			@Advice.Argument(0) Point fromTile,
			@Advice.Argument(2) Point offsetTile,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (!result && DeepHolePathing.canPassDeepHoleDiagonal(finder, fromTile, offsetTile)) result = true;
	}
}
