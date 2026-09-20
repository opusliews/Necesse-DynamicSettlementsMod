package opusliews.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.ai.path.PathOptions;
import necesse.level.maps.CollisionFilter;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.tile.FireHazardPathing;

@ModMethodPatch(
		target = PathOptions.class,
		name = "canPassTile",
		arguments = {TickManager.class, Level.class, Mob.class, PathDoorOption.class, CollisionFilter.class, int.class, int.class}
)
public class FireHazardPathTilePatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(1) Level level,
			@Advice.Argument(2) Mob mob,
			@Advice.Argument(5) int tileX,
			@Advice.Argument(6) int tileY,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (result && FireHazardPathing.shouldBlockFire(level, mob, tileX, tileY)) result = false;
	}
}
