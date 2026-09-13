package opus.guard;

import java.awt.Point;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.level.maps.Level;

public final class GuardDutySystem {
	private static final Map<GuardHumanMob, GuardDuty> duties =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Level, Map<Integer, Point>> patrolTargets =
			Collections.synchronizedMap(new WeakHashMap<>());

	private GuardDutySystem() {
	}

	public static GuardDuty getDuty(GuardHumanMob guard) {
		if (guard == null) {
			return GuardDuty.DAY;
		}

		synchronized (duties) {
			return duties.getOrDefault(guard, GuardDuty.DAY);
		}
	}

	public static boolean isNightDuty(GuardHumanMob guard) {
		return getDuty(guard) == GuardDuty.NIGHT;
	}

	public static void setDuty(GuardHumanMob guard, GuardDuty duty) {
		if (guard == null) {
			return;
		}

		GuardDuty actualDuty = duty == null ? GuardDuty.DAY : duty;
		synchronized (duties) {
			if (actualDuty == GuardDuty.DAY) {
				duties.remove(guard);
			}
			else {
				duties.put(guard, actualDuty);
			}
		}

		releasePatrolTarget(guard);
		GuardNeedsSystem.releaseBreakForDutyChange(guard);
		if (guard.isServer()) {
			guard.cancelJob();
		}
	}

	public static boolean usesNightSchedule(GuardHumanMob guard) {
		return guard != null
				&& isNightDuty(guard)
				&& guard.isSettlerOnCurrentLevel()
				&& !guard.adventureParty.isInAdventureParty();
	}

	public static boolean shouldPatrol(GuardHumanMob guard) {
		return usesNightSchedule(guard)
				&& guard.getWorldEntity().isNight()
				&& !guard.isHiding
				&& !guard.hasCommandOrders();
	}

	public static void claimPatrolTarget(GuardHumanMob guard, Point target) {
		if (guard == null || target == null || guard.getLevel() == null) {
			return;
		}

		Level level = guard.getLevel();
		synchronized (patrolTargets) {
			Map<Integer, Point> levelTargets = patrolTargets.computeIfAbsent(level, ignored -> new HashMap<>());
			levelTargets.put(guard.getUniqueID(), new Point(target));
		}
	}

	public static void releasePatrolTarget(GuardHumanMob guard) {
		if (guard == null || guard.getLevel() == null) {
			return;
		}

		Level level = guard.getLevel();
		synchronized (patrolTargets) {
			Map<Integer, Point> levelTargets = patrolTargets.get(level);
			if (levelTargets == null) {
				return;
			}

			levelTargets.remove(guard.getUniqueID());
			if (levelTargets.isEmpty()) {
				patrolTargets.remove(level);
			}
		}
	}

	public static int getPatrolSpacingScore(GuardHumanMob guard, Point candidate) {
		if (guard == null || candidate == null || guard.getLevel() == null) {
			return Integer.MAX_VALUE;
		}

		Level level = guard.getLevel();
		int bestDistance = Integer.MAX_VALUE;

		synchronized (patrolTargets) {
			Map<Integer, Point> levelTargets = patrolTargets.get(level);
			if (levelTargets == null || levelTargets.isEmpty()) {
				return bestDistance;
			}

			Iterator<Map.Entry<Integer, Point>> iterator = levelTargets.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<Integer, Point> entry = iterator.next();
				Mob mob = GameUtils.getLevelMob(entry.getKey(), level);
				if (!(mob instanceof GuardHumanMob) || mob.removed() || !shouldPatrol((GuardHumanMob)mob)) {
					iterator.remove();
					continue;
				}

				if (entry.getKey() == guard.getUniqueID()) {
					continue;
				}

				Point other = entry.getValue();
				int dx = candidate.x - other.x;
				int dy = candidate.y - other.y;
				bestDistance = Math.min(bestDistance, dx * dx + dy * dy);
			}
		}

		return bestDistance;
	}
}
