package opusliews.patches;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.leaves.ChaserAINode;
import necesse.entity.mobs.hostile.HostileArcherMob;
import necesse.level.maps.CollisionFilter;
import necesse.level.maps.LevelObjectHit;
import net.bytebuddy.asm.Advice;
import opusliews.breaching.ZombieBreaching;

@ModMethodPatch(
		target = ChaserAINode.class,
		name = "hasLineOfSightToTarget",
		arguments = {Mob.class, float.class, float.class, float.class, Mob.class, float.class}
)
public class ZombieArcherBreachingLineOfSightPatch {
	@Advice.OnMethodExit
	static void onExit(
			@Advice.Argument(0) Mob mob,
			@Advice.Argument(1) float fromX,
			@Advice.Argument(2) float fromY,
			@Advice.Argument(3) float startOffset,
			@Advice.Argument(4) Mob target,
			@Advice.Argument(5) float hitBoxWidth,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (result || !(mob instanceof HostileArcherMob)
				|| !ZombieBreaching.isZombie(mob) || target == null) {
			return;
		}

		if (startOffset != 0.0F) {
			Point2D.Float dir = GameMath.normalize(target.x - fromX, target.y - fromY);
			fromX += dir.x * startOffset;
			fromY += dir.y * startOffset;
		}

		CollisionFilter filter = mob.modifyChasingCollisionFilter(new CollisionFilter().projectileCollision(), target);
		ArrayList collisions = mob.getLevel().getCollisions(new Line2D.Float(fromX, fromY, target.x, target.y), filter);
		LevelObjectHit closest = null;
		double closestDistance = Double.MAX_VALUE;

		for (Object entry : collisions) {
			if (!(entry instanceof LevelObjectHit)) {
				continue;
			}
			LevelObjectHit hit = (LevelObjectHit)entry;
			if (hit.invalidPos()) {
				continue;
			}
			double distance = Point2D.distance(fromX, fromY, hit.getCenterX(), hit.getCenterY());
			if (distance < closestDistance) {
				closest = hit;
				closestDistance = distance;
			}
		}

		if (closest != null && ZombieBreaching.isBreachingObject(mob, closest.getLevelObject())) {
			// The normal archer attack still aims at the mob. The arrow will collide
			// with this obstacle first, where ZombieArcherBreachingProjectilePatch
			// converts that collision into normal breach damage.
			result = true;
		}
	}
}
