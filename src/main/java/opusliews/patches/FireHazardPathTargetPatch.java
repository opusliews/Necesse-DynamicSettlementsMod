package opusliews.patches;

import java.awt.Point;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.ai.path.TilePathfinding;
import net.bytebuddy.asm.Advice;
import opusliews.tile.FireHazardPathing;

@ModMethodPatch(
		target = TilePathfinding.class,
		name = "checkCanPassDoorOrTile",
		arguments = {Point.class}
)
public class FireHazardPathTargetPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This TilePathfinding finder,
			@Advice.Argument(0) Point targetTile,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (result && FireHazardPathing.pathTargetContainsFire(finder, targetTile)) result = false;
	}
}
