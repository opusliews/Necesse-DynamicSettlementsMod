package opus.guard;

import java.awt.Point;

import necesse.engine.util.ComputedValue;
import necesse.engine.util.gameAreaSearch.GameAreaStream;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.trees.HumanTargetFinderAI;
import necesse.entity.mobs.ai.behaviourTree.util.TargetFinderDistance;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;

public class NightGuardTargetFinderAI extends HumanTargetFinderAI {
	public NightGuardTargetFinderAI(int searchDistance) {
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
		if (!GuardDutySystem.shouldPatrol(guard) || GuardNeedsSystem.isOnBreak(guard)) {
			return false;
		}

		return super.isValidTarget(mob, target, true, false, zoneTester, isNewTarget);
	}

	@Override
	public GameAreaStream streamHumanAITargets(HumanMob mob, TargetFinderDistance distance) {
		if (!(mob instanceof GuardHumanMob)) {
			return GameAreaStream.empty();
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!GuardDutySystem.shouldPatrol(guard) || GuardNeedsSystem.isOnBreak(guard)) {
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
