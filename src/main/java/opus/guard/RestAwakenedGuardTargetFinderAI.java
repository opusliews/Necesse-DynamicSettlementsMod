package opus.guard;

import java.awt.Point;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import necesse.engine.gameLoop.tickManager.Performance;
import necesse.engine.util.ComputedValue;
import necesse.engine.util.GameMath;
import necesse.engine.util.gameAreaSearch.GameAreaStream;
import necesse.engine.util.pathfinding.PathResult;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanTargetFinderAI;
import necesse.entity.mobs.ai.behaviourTree.util.TargetFinderDistance;
import necesse.entity.mobs.ai.path.TilePathfinding;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.ZoneTester;
import opus.logging.Logging;

public class RestAwakenedGuardTargetFinderAI extends HumanTargetFinderAI {
	public RestAwakenedGuardTargetFinderAI(int searchDistance) {
		super(searchDistance, true, true);
	}

	@Override
	public boolean isValidTarget(
			HumanMob mob,
			Mob target,
			boolean homeOutside,
			boolean isHidingInside,
			ComputedValue zoneTester,
			boolean isNewTarget
	) {
		if (!(mob instanceof GuardHumanMob)) {
			return false;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardFatigueSystem.isRestAwakenedByCombat(guard)) {
			return false;
		}

		if (humanAngerTargetAINode.enemies.contains(target)) {
			return true;
		}

		int maxDistance = isNewTarget ? 20 : 30;
		Point baseTile = new Point(mob.getTileX(), mob.getTileY());
		if (GameMath.squareDistance(
				(float)baseTile.x,
				(float)baseTile.y,
				(float)target.getTileX(),
				(float)target.getTileY()
		) > (float)maxDistance) {
			return false;
		}

		ZoneTester zone = (ZoneTester)zoneTester.get();
		if (zone != null && !zone.containsTile(target.getTileX(), target.getTileY())) {
			return false;
		}

		if (!isNewTarget) {
			return true;
		}

		boolean foundPath = (Boolean)Performance.record(mob.getLevel().tickManager(), "restAwakenedGuardTarget", (Supplier)(() -> {
			BiPredicate isAtTarget = mob.canBeTargetedFromAdjacentTiles()
					? TilePathfinding.isAtOrAdjacentObject(target.getLevel(), target.getTileX(), target.getTileY())
					: null;
			TilePathfinding pathfinding = new TilePathfinding(
					mob.getLevel().tickManager(),
					mob.getLevel(),
					mob,
					isAtTarget,
					getBlackboard().mover.getPathOptions(this)
			);
			PathResult result = pathfinding.findPath(
					baseTile,
					new Point(target.getTileX(), target.getTileY()),
					maxDistance + 5
			);
			return result.foundTarget;
		}));

		if (foundPath) {
			Logging.logMessage("GuardFatigueDebug: awakened combat finder guard=" + guard.getUniqueID()
					+ " accepted target=" + target.getStringID() + "#" + target.getUniqueID());
		}
		return foundPath;
	}

	@Override
	public GameAreaStream streamHumanAITargets(HumanMob mob, TargetFinderDistance distance) {
		if (!(mob instanceof GuardHumanMob)) {
			return GameAreaStream.empty();
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardFatigueSystem.isRestAwakenedByCombat(guard)) {
			return GameAreaStream.empty();
		}

		Point base = new Point(mob.getX(), mob.getY());
		return distance.streamMobsAndPlayersInRange(base, mob)
				.filter(targetObject -> {
					Mob target = (Mob)targetObject;
					return target.canTakeDamage() && target.canBeHit(mob);
				})
				.filter(mob.filterHumanTargets())
				.filter(targetObject -> {
					Mob target = (Mob)targetObject;
					return target.getDistance((float)base.x, (float)base.y)
							< (float)distance.getSearchDistance(mob, target);
				});
	}
}
