package opusliews.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import necesse.engine.registries.JobTypeRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobFinder;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.jobs.AbstractLevelJob;
import necesse.level.maps.levelData.jobs.ChatWithOtherLevelJob;
import necesse.level.maps.levelData.jobs.ConsumeFoodLevelJob;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

public final class GuardNeedsSystem {
	public enum BreakType {
		NONE,
		RECREATION,
		HUNGER
	}

	private static final long reservationGraceMs = 30000L;
	private static final long failedBreakRetryMs = 2000L;
	private static final Map<Level, Map<Integer, SettlementBreakState>> states =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<GuardHumanMob, Boolean> nightShiftLogged =
			Collections.synchronizedMap(new WeakHashMap<>());

	private GuardNeedsSystem() {
	}

	public static void logNightShiftIfNeeded(GuardHumanMob guard) {
		if (guard == null || !guard.isServer()) {
			return;
		}

		boolean active = isActiveNightGuard(guard);
		synchronized (nightShiftLogged) {
			boolean wasActive = nightShiftLogged.getOrDefault(guard, false);
			if (active && !wasActive) {
				Logging.logMessage("NightGuard: guard " + guard.getUniqueID()
						+ " started night duty (hunger=" + String.format("%.2f", guard.getHungerLevel())
						+ ", recreation=" + String.format("%.2f", guard.getRecreationLevel()) + ")");
			}
			nightShiftLogged.put(guard, active);
		}
	}

	public static boolean shouldYieldPatrolForBreak(GuardHumanMob guard) {
		if (!isActiveNightGuard(guard) || isInCombat(guard)) {
			return false;
		}

		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		if (settlement == null) {
			return false;
		}

		SettlementBreakState state = getState(guard.getLevel(), settlement.uniqueID);
		synchronized (state) {
			cleanupState(guard.getLevel(), settlement.uniqueID, state);

			if (state.holderUniqueID != -1) {
				return state.holderUniqueID == guard.getUniqueID();
			}

			List<GuardHumanMob> activeGuards = getActiveNightGuards(guard.getLevel(), settlement.uniqueID);

			long now = guard.getLevel().getTime();
			List<GuardHumanMob> breakCandidates = new ArrayList<>();
			for (GuardHumanMob activeGuard : activeGuards) {
				Long nextAttempt = state.nextAttemptTimes.get(activeGuard.getUniqueID());
				if (nextAttempt == null || now >= nextAttempt) {
					breakCandidates.add(activeGuard);
				}
			}
			if (breakCandidates.isEmpty()) {
				return false;
			}

			GuardHumanMob chosen = chooseHungriestGuard(breakCandidates);
			BreakType type = BreakType.HUNGER;

			if (chosen == null && activeGuards.size() > 1) {
				pruneRecreationServed(state, activeGuards);
				if (allActiveGuardsServed(state, activeGuards)) {
					state.recreationServed.clear();
				}

				chosen = chooseMostRecreationNeedyGuard(breakCandidates, state.recreationServed);
				type = BreakType.RECREATION;
			}

			if (chosen == null || chosen.getUniqueID() != guard.getUniqueID()) {
				return false;
			}

			state.holderUniqueID = guard.getUniqueID();
			state.breakType = type;
			state.sequence = null;
			state.reservedAt = guard.getLevel().getTime();
			state.recreationLevelAtStart = type == BreakType.RECREATION ? guard.getRecreationLevel() : -1.0F;
			Logging.logMessage("NightGuard: guard " + guard.getUniqueID() + " assigned " + type
					+ " break (hunger=" + String.format("%.2f", guard.getHungerLevel())
					+ ", recreation=" + String.format("%.2f", guard.getRecreationLevel()) + ")");
			return true;
		}
	}

	public static JobSequence findBreakJob(GuardHumanMob guard, boolean ignoreRecreationJobs, Consumer finderMod) {
		if (!GuardDutySystem.usesNightSchedule(guard)) {
			return null;
		}

		if (!guard.getWorldEntity().isNight()) {
			releaseBreak(guard, false);
			return null;
		}

		if (guard.objectUser != null || guard.isHiding || guard.isVisitor() || guard.hasCommandOrders()) {
			releaseBreak(guard, false);
			return null;
		}

		NetworkSettlementData settlementNetworkData = guard.getSettlerSettlementNetworkData();
		if (settlementNetworkData != null) {
			if (settlementNetworkData.isRaidActive() || settlementNetworkData.isDisbanding() || !settlementNetworkData.hasOwner()) {
				releaseBreak(guard, false);
				return null;
			}
		}

		if (guard.attemptStartStrike(true)) {
			releaseBreak(guard, false);
			return null;
		}

		if (!shouldYieldPatrolForBreak(guard)) {
			return null;
		}

		BreakType breakType = getBreakType(guard);
		if (breakType == BreakType.NONE || breakType == BreakType.RECREATION && ignoreRecreationJobs) {
			return null;
		}

		JobTypeHandler handler = guard.getJobTypeHandler();
		long currentTime = guard.getTime();
		if (handler == null || handler.isOnGlobalCooldown(currentTime)) {
			return null;
		}

		JobFinder finder = new JobFinder(guard);
		if (finderMod != null) {
			finderMod.accept(finder);
		}

		Predicate<AbstractLevelJob> existingPredicate = finder.levelJobPredicate;
		finder.levelJobPredicate = job -> {
			if (existingPredicate != null && !existingPredicate.test(job)) {
				return false;
			}

			if (breakType == BreakType.HUNGER) {
				return job instanceof ConsumeFoodLevelJob;
			}

			return job.jobType.getID() == JobTypeRegistry.recreationID
					&& !(job instanceof ChatWithOtherLevelJob);
		};

		FoundJob first = finder.findJob(false);
		if (handler.resetPrioritizeNextJobIfFound) {
			handler.prioritizeNextJobID = -1;
		}

		if (first == null) {
			handler.lastPerformedJobID = -1;
			handler.prioritizeNextJobID = -1;
			deferBreakRetry(guard);
			releaseBreak(guard, false);
			return null;
		}

		first.startCooldown(currentTime);
		if (first.job.prioritizeForSameJobAgain()) {
			handler.lastPerformedJobID = first.job.getID();
		}
		else {
			handler.lastPerformedJobID = -1;
		}

		JobSequence sequence = first.getSequence();
		if (sequence == null) {
			deferBreakRetry(guard);
			releaseBreak(guard, false);
			return null;
		}

		confirmBreakSequence(guard, sequence);
		Logging.logMessage("NightGuard: guard " + guard.getUniqueID() + " started " + breakType + " break job");
		return sequence;
	}

	public static BreakType getBreakType(GuardHumanMob guard) {
		if (guard == null || guard.getLevel() == null) {
			return BreakType.NONE;
		}

		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		if (settlement == null) {
			return BreakType.NONE;
		}

		SettlementBreakState state = getState(guard.getLevel(), settlement.uniqueID);
		synchronized (state) {
			cleanupState(guard.getLevel(), settlement.uniqueID, state);
			return state.holderUniqueID == guard.getUniqueID() ? state.breakType : BreakType.NONE;
		}
	}

	public static boolean isOnBreak(GuardHumanMob guard) {
		return getBreakType(guard) != BreakType.NONE;
	}

	public static void interruptBreak(GuardHumanMob guard) {
		releaseBreak(guard, false);
		if (guard != null && guard.isServer()) {
			guard.cancelJob();
		}
	}

	public static void releaseBreak(GuardHumanMob guard, boolean recreationCompleted) {
		if (guard == null || guard.getLevel() == null) {
			return;
		}

		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		if (settlement == null) {
			return;
		}

		SettlementBreakState state = getState(guard.getLevel(), settlement.uniqueID);
		synchronized (state) {
			if (state.holderUniqueID != guard.getUniqueID()) {
				return;
			}

			if (recreationCompleted && state.breakType == BreakType.RECREATION) {
				state.recreationServed.add(guard.getUniqueID());
			}

			clearHolder(state);
		}
	}

	public static void releaseBreakForDutyChange(GuardHumanMob guard) {
		interruptBreak(guard);
	}

	public static boolean isInCombat(GuardHumanMob guard) {
		if (guard == null || guard.ai == null) {
			return false;
		}

		Mob currentTarget = (Mob)guard.ai.blackboard.getObject(Mob.class, "currentTarget");
		if (isLivingTargetForGuard(guard, currentTarget)) {
			return true;
		}

		Mob chaserTarget = (Mob)guard.ai.blackboard.getObject(Mob.class, "chaserTarget");
		return isLivingTargetForGuard(guard, chaserTarget);
	}

	private static void confirmBreakSequence(GuardHumanMob guard, JobSequence sequence) {
		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		if (settlement == null) {
			return;
		}

		SettlementBreakState state = getState(guard.getLevel(), settlement.uniqueID);
		synchronized (state) {
			if (state.holderUniqueID == guard.getUniqueID()) {
				state.sequence = sequence;
				state.reservedAt = guard.getLevel().getTime();
				state.nextAttemptTimes.remove(guard.getUniqueID());
			}
		}
	}

	private static void deferBreakRetry(GuardHumanMob guard) {
		ServerSettlementData settlement = guard.getSettlerSettlementServerData();
		if (settlement == null) {
			return;
		}

		SettlementBreakState state = getState(guard.getLevel(), settlement.uniqueID);
		synchronized (state) {
			state.nextAttemptTimes.put(guard.getUniqueID(), guard.getLevel().getTime() + failedBreakRetryMs);
		}
	}

	private static GuardHumanMob chooseHungriestGuard(List<GuardHumanMob> guards) {
		return guards.stream()
				.filter(guard -> !isInCombat(guard))
				.filter(guard -> guard.getHungerLevel() <= 0.25F)
				.min(Comparator.comparingDouble((GuardHumanMob guard) -> guard.getHungerLevel())
						.thenComparingInt(guard -> guard.getUniqueID()))
				.orElse(null);
	}

	private static GuardHumanMob chooseMostRecreationNeedyGuard(List<GuardHumanMob> guards, Set<Integer> served) {
		return guards.stream()
				.filter(guard -> !isInCombat(guard))
				.filter(guard -> guard.getRecreationLevel() <= 0.4F)
				.filter(guard -> !served.contains(guard.getUniqueID()))
				.min(Comparator.comparingDouble((GuardHumanMob guard) -> guard.getRecreationLevel())
						.thenComparingInt(guard -> guard.getUniqueID()))
				.orElse(null);
	}

	private static boolean allActiveGuardsServed(SettlementBreakState state, List<GuardHumanMob> activeGuards) {
		if (activeGuards.isEmpty()) {
			return false;
		}

		for (GuardHumanMob guard : activeGuards) {
			if (!state.recreationServed.contains(guard.getUniqueID())) {
				return false;
			}
		}

		return true;
	}

	private static void pruneRecreationServed(SettlementBreakState state, List<GuardHumanMob> activeGuards) {
		Set<Integer> activeIDs = new HashSet<>();
		for (GuardHumanMob guard : activeGuards) {
			activeIDs.add(guard.getUniqueID());
		}
		state.recreationServed.removeIf(id -> !activeIDs.contains(id));
		state.nextAttemptTimes.keySet().removeIf(id -> !activeIDs.contains(id));
	}

	private static List<GuardHumanMob> getActiveNightGuards(Level level, int settlementUniqueID) {
		List<GuardHumanMob> guards = new ArrayList<>();
		level.entityManager.mobs.stream()
				.filter(mob -> mob instanceof GuardHumanMob)
				.map(mob -> (GuardHumanMob)mob)
				.filter(GuardNeedsSystem::isActiveNightGuard)
				.filter(guard -> {
					ServerSettlementData settlement = guard.getSettlerSettlementServerData();
					return settlement != null && settlement.uniqueID == settlementUniqueID;
				})
				.forEach(guards::add);
		return guards;
	}

	private static boolean isActiveNightGuard(GuardHumanMob guard) {
		return guard != null
				&& !guard.removed()
				&& GuardDutySystem.usesNightSchedule(guard)
				&& guard.getWorldEntity().isNight()
				&& !guard.isHiding
				&& !guard.hasCommandOrders();
	}

	private static boolean isLivingTargetForGuard(GuardHumanMob guard, Mob target) {
		return target != null
				&& !target.removed()
				&& target.getHealth() > 0
				&& target.isSamePlace(guard);
	}

	private static SettlementBreakState getState(Level level, int settlementUniqueID) {
		synchronized (states) {
			Map<Integer, SettlementBreakState> levelStates = states.computeIfAbsent(level, ignored -> new HashMap<>());
			return levelStates.computeIfAbsent(settlementUniqueID, ignored -> new SettlementBreakState());
		}
	}

	private static void cleanupState(Level level, int settlementUniqueID, SettlementBreakState state) {
		if (state.holderUniqueID == -1) {
			return;
		}

		Mob mob = necesse.engine.util.GameUtils.getLevelMob(state.holderUniqueID, level);
		if (!(mob instanceof GuardHumanMob)) {
			clearHolder(state);
			return;
		}

		GuardHumanMob holder = (GuardHumanMob)mob;
		ServerSettlementData holderSettlement = holder.getSettlerSettlementServerData();
		if (!isActiveNightGuard(holder)
				|| holderSettlement == null
				|| holderSettlement.uniqueID != settlementUniqueID) {
			clearHolder(state);
			return;
		}

		if (state.sequence == null) {
			if (level.getTime() - state.reservedAt > reservationGraceMs) {
				clearHolder(state);
			}
			return;
		}

		if (holder.ai == null) {
			clearHolder(state);
			return;
		}

		JobSequence currentSequence = (JobSequence)holder.ai.blackboard.getObject(JobSequence.class, "currentJobSequence");
		if (currentSequence == state.sequence) {
			return;
		}

		if (level.getTime() - state.reservedAt <= reservationGraceMs) {
			return;
		}

		boolean recreationCompleted = state.breakType == BreakType.RECREATION
				&& state.recreationLevelAtStart >= 0.0F
				&& holder.getRecreationLevel() > state.recreationLevelAtStart + 0.001F;
		if (recreationCompleted) {
			state.recreationServed.add(holder.getUniqueID());
		}
		Logging.logMessage("NightGuard: guard " + holder.getUniqueID() + " finished " + state.breakType
				+ " break" + (recreationCompleted ? " (recreation satisfied)" : ""));
		clearHolder(state);
	}

	private static void clearHolder(SettlementBreakState state) {
		state.holderUniqueID = -1;
		state.breakType = BreakType.NONE;
		state.sequence = null;
		state.reservedAt = 0L;
		state.recreationLevelAtStart = -1.0F;
	}

	private static final class SettlementBreakState {
		private int holderUniqueID = -1;
		private BreakType breakType = BreakType.NONE;
		private JobSequence sequence;
		private long reservedAt;
		private float recreationLevelAtStart = -1.0F;
		private final Set<Integer> recreationServed = new HashSet<>();
		private final Map<Integer, Long> nextAttemptTimes = new HashMap<>();
	}
}
