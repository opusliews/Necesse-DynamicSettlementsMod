package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreaching;

@ModMethodPatch(target = Mob.class, name = "getPathBreakDownCost", arguments = {Level.class, int.class, int.class, GameObject.class})
public class ZombieBreachingCostPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This Mob mob,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) GameObject object,
			@Advice.Return(readOnly = false) double result
	) {
		if (!ZombieBreaching.isZombie(mob)) {
			return;
		}

		if (!ZombieBreaching.getState(mob).pathDoorOption.canBreakDown(tileX, tileY)) {
			return;
		}

		double breachCost = ZombieBreaching.getBreakPathCost(level, tileX, object);
		if (breachCost >= 0.0) {
			result = breachCost;
		}
	}
}
