package opus.guard;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.entity.mobs.friendly.human.GuardHumanMob;
import opus.logging.Logging;
import opus.network.PacketGuardFatigueUpdate;

public final class GuardFatigueSystem {
	public static final int maxFatigue = 10;
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

		if (rest == state.restPeriodActive) {
			return;
		}

		if (rest) {
			state.restPeriodActive = true;
			state.restInterrupted = false;
		}
		else {
			state.restPeriodActive = false;
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
	}
}
