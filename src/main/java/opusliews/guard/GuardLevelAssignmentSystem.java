package opusliews.guard;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.entity.mobs.friendly.human.GuardHumanMob;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

public final class GuardLevelAssignmentSystem {
	private static final Map<GuardHumanMob, GuardLevelAssignment> assignments = Collections.synchronizedMap(new WeakHashMap<>());

	private GuardLevelAssignmentSystem() {
	}

	public static GuardLevelAssignment getAssignment(GuardHumanMob guard) {
		if (guard == null) return GuardLevelAssignment.SURFACE;
		synchronized (assignments) {
			return assignments.getOrDefault(guard, GuardLevelAssignment.SURFACE);
		}
	}

	public static SettlementLevelType getAssignedLevelType(GuardHumanMob guard) {
		return getAssignment(guard).levelType;
	}

	public static void setAssignment(GuardHumanMob guard, GuardLevelAssignment assignment) {
		if (guard == null) return;
		GuardLevelAssignment resolved = assignment == null ? GuardLevelAssignment.SURFACE : assignment;
		synchronized (assignments) {
			if (resolved == GuardLevelAssignment.SURFACE) assignments.remove(guard);
			else assignments.put(guard, resolved);
		}
		GuardDutySystem.releasePatrolTarget(guard);
		if (guard.isServer()) guard.cancelJob();
		if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Set guard=" + guard.getUniqueID() + " level=" + resolved);
	}

	public static boolean isOnAssignedLevel(GuardHumanMob guard) {
		if (guard == null || guard.getLevel() == null) return false;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(guard.getSettlerSettlementServerData());
		if (domain == null) return false;
		SettlementLevelType currentType = domain.getLevelType(guard.getLevel().getIdentifier());
		return currentType != null && currentType == getAssignedLevelType(guard);
	}
}
