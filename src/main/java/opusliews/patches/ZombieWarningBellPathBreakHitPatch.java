package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.gameObject.DoorObject;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.WarningBellSystem;
import opusliews.breaching.ZombieBreaching;
import opusliews.logging.Logging;

@ModMethodPatch(
		target = Mob.class,
		name = "onPathBreakDownHit",
		arguments = {LevelObject.class, boolean.class, boolean.class}
)
public class ZombieWarningBellPathBreakHitPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo) {
		Logging.logMessage("WarningBell: Mob.onPathBreakDownHit advice reached for "
				+ (mob == null ? "null mob" : mob.getStringID())
				+ " object=" + (lo == null || lo.object == null ? "null" : lo.object.getStringID()));
		if (!mob.isServer()
				|| !ZombieBreaching.isZombie(mob)
				|| lo == null
				|| lo.object == null) {
			Logging.logMessage("WarningBell: path-break callback rejected by server/zombie/null checks");
			return;
		}

		if (lo.object.isFence || lo.object instanceof DoorObject) {
			Logging.logMessage("WarningBell: path-break callback forwarding barrier hit at "
					+ lo.tileX + "," + lo.tileY + " to warning bell system");
			WarningBellSystem.onZombieBarrierAttacked(mob, lo);
		}
		else {
			Logging.logMessage("WarningBell: path-break callback object is not fence/door");
		}
	}
}
