package opusliews.guard;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.event.AIEvent;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import opusliews.logging.Logging;
import opusliews.network.PacketGuardFatigueUpdate;

public final class GuardFatigueSystem {
	public static final int maxFatigue = 10;
	private static final long restCombatAwakeGraceMs = 3000L;
	private static final Map<GuardHumanMob, State> states = Collections.synchronizedMap(new WeakHashMap<>());

	private GuardFatigueSystem() {
	}

	public static int getFatigue(GuardHumanMob guard) {
		if (guard == null) {
			return 0;
		}

		return getState(guard).fatigue;
	}

	public static boolean isFullyExhausted(GuardHumanMob guard) {
		return getFatigue(guard) >= maxFatigue;
	}

	public static float getOutgoingDamageMultiplier(GuardHumanMob guard) {
		return Math.max(0.0F, 1.0F - getFatigue(guard) * 0.1F);
	}

	public static float getSpeedMultiplier(GuardHumanMob guard) {
		return Math.max(0.0F, 1.0F - getFatigue(guard) * 0.1F);
	}

	public static float getIncomingDamageMultiplier(GuardHumanMob guard) {
		return 1.0F + getFatigue(guard) * 0.1F;
	}

	public static boolean usesDutySchedule(GuardHumanMob guard) {
		return guard != null
				&& guard.isSettlerOnCurrentLevel()
				&& !guard.adventureParty.isInAdventureParty();
	}

	public static boolean isScheduledRestPeriod(GuardHumanMob guard) {
		if (!usesDutySchedule(guard)) {
			return false;
		}

		boolean night = guard.getWorldEntity().isNight();
		return GuardDutySystem.isNightDuty(guard) ? !night : night;
	}

	public static boolean canRespondToEmergency(GuardHumanMob guard) {
		return guard != null && !isFullyExhausted(guard);
	}

	public static void tickGuard(GuardHumanMob guard) {
		if (guard == null || !guard.isServer()) {
			return;
		}

		State state = getState(guard);
		boolean rest = isScheduledRestPeriod(guard);

		if (!state.initialized) {
			state.initialized = true;
			state.restPeriodActive = rest;
			return;
		}

		if (rest && GuardNeedsSystem.isInCombat(guard)) {
			markRestCombat(guard, "combat");
		}

		if (rest && state.directRestAttackerUniqueID != -1) {
			Mob attacker = necesse.engine.util.GameUtils.getLevelMob(state.directRestAttackerUniqueID, guard.getLevel());
			if (attacker == null || attacker.removed() || attacker.getHealth() <= 0 || !attacker.isSamePlace(guard)) {
				Logging.logMessage("GuardFatigueDebug: guard " + guard.getUniqueID()
						+ " direct rest attacker became invalid, clearing id=" + state.directRestAttackerUniqueID);
				state.directRestAttackerUniqueID = -1;
			}
		}

		if (rest && state.restAwakenedByCombat) {
			long now = guard.getLevel().getTime();
			boolean inCombat = GuardNeedsSystem.isInCombat(guard);
			if (inCombat) {
				state.lastRestCombatTime = now;
			}

			if (now >= state.nextDebugLogTime) {
				state.nextDebugLogTime = now + 500L;
				Mob currentTarget = guard.ai == null ? null : guard.ai.blackboard.getObject(Mob.class, "currentTarget");
				Mob chaserTarget = guard.ai == null ? null : guard.ai.blackboard.getObject(Mob.class, "chaserTarget");
				Logging.logMessage("GuardFatigueDebug: awakened guard " + guard.getUniqueID()
						+ " rest=" + rest
						+ " inCombat=" + inCombat
						+ " directAttacker=" + describeMob(necesse.engine.util.GameUtils.getLevelMob(state.directRestAttackerUniqueID, guard.getLevel()))
						+ " currentTarget=" + describeMob(currentTarget)
						+ " chaserTarget=" + describeMob(chaserTarget));
			}

			if (!inCombat && now - state.lastRestCombatTime >= restCombatAwakeGraceMs) {
				Logging.logMessage("GuardFatigueDebug: guard " + guard.getUniqueID()
						+ " combat-awake grace expired, returning to normal rest targeting");
				state.restAwakenedByCombat = false;
				state.directRestAttackerUniqueID = -1;
			}
		}

		if (rest == state.restPeriodActive) {
			return;
		}

		if (rest) {
			state.restPeriodActive = true;
			state.restInterrupted = false;
			state.directRestAttackerUniqueID = -1;
			state.restAwakenedByCombat = false;
			state.lastRestCombatTime = 0L;
		}
		else {
			state.restPeriodActive = false;
			state.directRestAttackerUniqueID = -1;
			state.restAwakenedByCombat = false;
			state.lastRestCombatTime = 0L;
			if (!state.restInterrupted && state.fatigue > 0) {
				setFatigue(guard, state.fatigue - 1);
				Logging.logMessage("GuardFatigue: guard " + guard.getUniqueID()
						+ " completed a full rest period, fatigue=" + getFatigue(guard));
			}
			state.restInterrupted = false;
		}
	}

	public static void onDutyChanged(GuardHumanMob guard) {
		if (guard == null || !guard.isServer()) {
			return;
		}

		State state = getState(guard);
		state.initialized = true;
		state.restPeriodActive = isScheduledRestPeriod(guard);
		state.restInterrupted = false;
		state.directRestAttackerUniqueID = -1;
		state.restAwakenedByCombat = false;
		state.lastRestCombatTime = 0L;
	}

	public static void markRestCombat(GuardHumanMob guard, String reason) {
		if (guard == null || !guard.isServer() || !isScheduledRestPeriod(guard)) {
			return;
		}

		State state = getState(guard);
		state.initialized = true;
		state.restPeriodActive = true;
		if (state.restInterrupted) {
			return;
		}

		state.restInterrupted = true;
		if (state.fatigue < maxFatigue) {
			setFatigue(guard, state.fatigue + 1);
		}
		Logging.logMessage("GuardFatigue: guard " + guard.getUniqueID()
				+ " rest interrupted by " + reason + ", fatigue=" + getFatigue(guard));
	}

	public static void registerDirectRestAttacker(GuardHumanMob guard, Mob attacker) {
		if (guard == null || attacker == null || !guard.isServer() || !isScheduledRestPeriod(guard)) {
			return;
		}

		markRestCombat(guard, "direct attack");
		State state = getState(guard);
		state.directRestAttackerUniqueID = attacker.getUniqueID();
		state.restAwakenedByCombat = true;
		state.lastRestCombatTime = guard.getLevel().getTime();
		state.nextDebugLogTime = 0L;

		Logging.logMessage("GuardFatigueDebug: guard " + guard.getUniqueID()
				+ " directly attacked during rest by " + describeMob(attacker)
				+ ", fatigue=" + getFatigue(guard)
				+ ", setting combat-awake state");

		if (guard.ai != null) {
			guard.ai.blackboard.put("currentTarget", attacker);
			guard.ai.blackboard.put("chaserTarget", attacker);
			guard.ai.blackboard.submitEvent("resetPathTime", new AIEvent());
			Logging.logMessage("GuardFatigueDebug: guard " + guard.getUniqueID()
					+ " forced currentTarget/chaserTarget=" + describeMob(attacker));
		}
		else {
			Logging.logMessage("GuardFatigueDebug: guard " + guard.getUniqueID()
					+ " has no AI while registering direct rest attacker");
		}
	}

	private static String describeMob(Mob mob) {
		return mob == null ? "null" : mob.getStringID() + "#" + mob.getUniqueID()
				+ " hp=" + mob.getHealth() + " removed=" + mob.removed();
	}

	public static boolean isRestAwakenedByCombat(GuardHumanMob guard) {
		return guard != null
				&& isScheduledRestPeriod(guard)
				&& getState(guard).restAwakenedByCombat;
	}

	public static boolean isDirectRestAttacker(GuardHumanMob guard, Mob target) {
		if (guard == null || target == null || !isScheduledRestPeriod(guard)) {
			return false;
		}

		State state = getState(guard);
		return state.directRestAttackerUniqueID == target.getUniqueID()
				&& !target.removed()
				&& target.getHealth() > 0
				&& target.isSamePlace(guard);
	}

	public static void applyLoadedState(
			GuardHumanMob guard,
			int fatigue,
			boolean savedRestPeriodActive,
			boolean savedRestInterrupted
	) {
		if (guard == null) {
			return;
		}

		State state = getState(guard);
		state.fatigue = clampFatigue(fatigue);
		state.initialized = true;
		boolean currentRest = isScheduledRestPeriod(guard);
		state.restPeriodActive = currentRest;
		state.restInterrupted = currentRest && savedRestPeriodActive && savedRestInterrupted;
		state.directRestAttackerUniqueID = -1;
		state.restAwakenedByCombat = false;
		state.lastRestCombatTime = 0L;
	}

	public static void applyClientFatigue(GuardHumanMob guard, int fatigue) {
		if (guard != null) {
			getState(guard).fatigue = clampFatigue(fatigue);
		}
	}

	public static boolean isRestPeriodActive(GuardHumanMob guard) {
		return guard != null && getState(guard).restPeriodActive;
	}

	public static boolean wasRestInterrupted(GuardHumanMob guard) {
		return guard != null && getState(guard).restInterrupted;
	}

	private static void setFatigue(GuardHumanMob guard, int fatigue) {
		State state = getState(guard);
		int value = clampFatigue(fatigue);
		if (state.fatigue == value) {
			return;
		}

		state.fatigue = value;
		if (guard.isServer() && guard.getLevel() != null && guard.getLevel().getServer() != null) {
			guard.getLevel().getServer().network.sendToClientsWithEntity(
					new PacketGuardFatigueUpdate(guard.getUniqueID(), value),
					guard
			);
		}
	}

	private static int clampFatigue(int fatigue) {
		return Math.max(0, Math.min(maxFatigue, fatigue));
	}

	private static State getState(GuardHumanMob guard) {
		synchronized (states) {
			return states.computeIfAbsent(guard, ignored -> new State());
		}
	}

	private static final class State {
		private int fatigue;
		private boolean initialized;
		private boolean restPeriodActive;
		private boolean restInterrupted;
		private int directRestAttackerUniqueID = -1;
		private boolean restAwakenedByCombat;
		private long lastRestCombatTime;
		private long nextDebugLogTime;
	}
}
