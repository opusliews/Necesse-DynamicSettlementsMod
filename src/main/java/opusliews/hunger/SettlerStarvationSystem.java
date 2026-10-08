package opusliews.hunger;

import necesse.entity.mobs.ObjectUserActive;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.GameMessageBuilder;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.world.WorldSettings;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.consumableItem.food.FoodConsumableItem;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.gameObject.furniture.SettlerBedObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.entity.mobs.job.JobSequence;
import necesse.level.maps.levelData.jobs.ConsumeFoodLevelJob;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import opusliews.logging.Logging;
import opusliews.guard.GuardDutySystem;
import opusliews.multilevelsettlement.SettlementCrossLevelJobSystem;
import opusliews.multilevelsettlement.SettlementCrossLevelRouting;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.progression.GuideProgressionSystem;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class SettlerStarvationSystem {
	public static final float sleepHungerFloor = 60.0F / HumanMob.secondsToPassAtFullHunger;
	public static final float emergencyEatingHungerThreshold = sleepHungerFloor;
	public static final float emergencyEatingFullThreshold = 1.0F;
	public static final long emergencyFoodCheckInterval = 1000L;
	public static final long starvationDamageInterval = 1000L;
	public static final int starvationDamageTicksToDeath = 20;
	public static final long hungerDebugInterval = 1000L;

	private static final Map<HumanMob, StarvationState> starvationStates = Collections.synchronizedMap(new WeakHashMap<>());
	private static final ThreadLocal<StarvationChatContext> starvationChatContext = new ThreadLocal<>();

	private SettlerStarvationSystem() {
	}

	public static void serverTick(HumanMob human) {

		if (human == null || !human.isServer() || !human.isSettler() || human.removed()) return;

		tickSleepingHunger(human);
		debugCaveHunger(human);
		tickEmergencyEating(human);
		tickStarvation(human);

	}

	public static void resetTransientStateAfterDebugHungerChange(HumanMob human) {

		if (human == null) return;
		StarvationState removed = starvationStates.remove(human);
		if (Logging.logEnabled) {
			Logging.logMessage("[StarvationDebug] Cleared transient starvation/emergency-food state after dev hunger change settler="
					+ human.getStringID() + "#" + human.getUniqueID()
					+ " hunger=" + human.hungerLevel
					+ " hadState=" + (removed != null));
		}

	}

	public static boolean shouldBlockHealing(HumanMob human, int requestedHealth) {

		return human != null
				&& human.isServer()
				&& human.isSettler()
				&& human.hungerLevel <= 0.0F
				&& requestedHealth > human.getHealth();

	}

	public static void logBlockedHealing(HumanMob human, int requestedHealth) {

		if (human == null || !Logging.logEnabled) return;
		Logging.logMessage("[Starvation] Blocked healing while starving settler=" + human.getStringID() + "#" + human.getUniqueID()
				+ " currentHealth=" + human.getHealth() + " requestedHealth=" + requestedHealth);

	}

	public static boolean isSleepingInBed(HumanMob human) {

		if (human == null) return false;
		ObjectUserActive user = human.getUsingObject();
		return user != null && user.object instanceof SettlerBedObject;

	}

	public static boolean hasAccessibleFood(HumanMob human) {

		if (human == null) return false;
		return hasWorkInventoryFood(human) || hasReachableStorageFood(human);

	}

	public static boolean hasAccessibleFoodAnyLevel(HumanMob human) {

		if (human == null) return false;
		if (hasAccessibleFood(human)) return true;
		return SettlementCrossLevelJobSystem.hasReachableFoodOnOtherLevel(human);

	}

	public static boolean isEmergencyEating(HumanMob human) {

		if (human == null) return false;
		StarvationState state = starvationStates.get(human);
		return state != null && state.emergencyEating && human.hungerLevel < emergencyEatingFullThreshold;

	}

	public static boolean shouldConsumeFood(HumanMob human) {

		return human != null && (human.hungerLevel <= 0.25F || isEmergencyEating(human));

	}

	public static boolean hasWorkInventoryFood(HumanMob human) {

		if (human == null) return false;
		ItemCategoriesFilter dietFilter = human.levelSettler == null ? null : human.levelSettler.dietFilter;
		Predicate<InventoryItem> foodFilter = item -> isEdibleFood(item, dietFilter);
		return human.getWorkInventory().stream().anyMatch(foodFilter);

	}

	public static boolean hasReachableStorageFood(HumanMob human) {

		if (human == null) return false;

		ItemCategoriesFilter dietFilter = human.levelSettler == null ? null : human.levelSettler.dietFilter;
		Predicate<InventoryItem> foodFilter = item -> isEdibleFood(item, dietFilter);
		try {
			ServerSettlementData settlement = human.getSettlerSettlementServerData();
			if (settlement != null && human.getLevel() != null
					&& !SettlementFoodAvailabilityCache.levelMayContainEdibleFood(settlement, human.getLevel(), dietFilter)) {
				return false;
			}

			// Keep vanilla's live job/restrict-zone/movement/reservation semantics. The cache only filters
			// out storages that are confidently known not to contain compatible food, so a cache miss or
			// uncertainty always falls through to the original live checks.
			Iterator<?> storageJobs = HasStorageLevelJob.streamStorageJobs(human, null).iterator();
			while (storageJobs.hasNext()) {
				Object next = storageJobs.next();
				if (!(next instanceof HasStorageLevelJob)) continue;
				HasStorageLevelJob storageJob = (HasStorageLevelJob)next;
				if (!SettlementFoodAvailabilityCache.storageMayContainEdibleFood(settlement, storageJob.settlementInventory, dietFilter)) continue;
				if (!storageJob.settlementInventory.estimateCanMoveTo(human)) continue;
				if (storageJob.settlementInventory.findFutureUnreservedSlots().filter(candidate -> foodFilter.test(candidate.item)).findAny().isPresent()) {
					return true;
				}
			}
			return false;
		}
		catch (Throwable error) {
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Non-mutating reachable-food lookup failed settler="
						+ human.getStringID() + "#" + human.getUniqueID()
						+ " level=" + (human.getLevel() == null ? "null" : human.getLevel().getIdentifier())
						+ " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
			}
			return false;
		}

	}

	public static boolean hasSurfaceAccess(HumanMob human) {

		if (human == null || human.getLevel() == null) return false;
		if (human.getSettlerSettlementServerData() == null) return false;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(human.getSettlerSettlementServerData());
		if (domain == null) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Settlement domain unavailable while checking surface access settler=" + human.getStringID() + "#" + human.getUniqueID());
			return false;
		}

		SettlementLevelType currentType = domain.getLevelType(human.getLevel().getIdentifier());
		if (currentType != SettlementLevelType.CAVE) return true;
		if (domain.getLevelIdentifier(SettlementLevelType.SURFACE) == null) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Cave settlement has no surface level registered settler=" + human.getStringID() + "#" + human.getUniqueID());
			return false;
		}

		return SettlementCrossLevelRouting.findBestTransitionRouteQuiet(human, domain, SettlementLevelType.SURFACE) != null;

	}

	public static boolean isStrandedWithoutFood(HumanMob human) {

		if (human == null || human.getLevel() == null || human.getSettlerSettlementServerData() == null) return false;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(human.getSettlerSettlementServerData());
		if (domain == null) return false;
		if (domain.getLevelType(human.getLevel().getIdentifier()) != SettlementLevelType.CAVE) return false;
		if (hasAccessibleFood(human)) return false;
		return !hasSurfaceAccess(human);

	}

	private static void tickEmergencyEating(HumanMob human) {

		long now = human.getWorldEntity().getTime();
		StarvationState state = starvationStates.get(human);
		if (state == null) {
			state = new StarvationState(now + starvationDamageInterval, Math.max(1, human.getHealth()));
			starvationStates.put(human, state);
		}

		if (human.hungerLevel >= emergencyEatingFullThreshold) {
			if (state.emergencyEating) {
				state.emergencyEating = false;
				state.noFoodAtCriticalLogged = false;
				if (Logging.logEnabled) {
					Logging.logMessage("[EmergencyFood] Settler reached full hunger; emergency feeding ended settler="
							+ human.getStringID() + "#" + human.getUniqueID() + " hunger=" + human.hungerLevel);
				}
			}
			return;
		}

		if (!state.emergencyEating && human.hungerLevel > emergencyEatingHungerThreshold) {
			state.noFoodAtCriticalLogged = false;
			return;
		}

		if (now < state.nextEmergencyFoodCheckTime) return;
		state.nextEmergencyFoodCheckTime = now + emergencyFoodCheckInterval;

		// A storage food item is reserved as soon as vanilla creates its ConsumeFood sequence. While
		// that sequence is in flight it intentionally disappears from the unreserved-food preview, so
		// keep the emergency alive until the current bite actually finishes or is cancelled.
		boolean currentFoodSequence = state.emergencyEating && isCurrentFoodSequence(human);
		boolean localFoodAvailable = hasAccessibleFood(human);
		boolean remoteFoodAvailable = !localFoodAvailable && SettlementCrossLevelJobSystem.hasReachableFoodOnOtherLevel(human);
		boolean foodAvailable = currentFoodSequence || localFoodAvailable || remoteFoodAvailable;
		if (!foodAvailable) {
			if (state.emergencyEating) {
				state.emergencyEating = false;
				if (Logging.logEnabled) {
					Logging.logMessage("[EmergencyFood] Emergency feeding ended because no edible reachable food remains on any settlement level settler="
							+ human.getStringID() + "#" + human.getUniqueID() + " hunger=" + human.hungerLevel);
				}
			}
			else if (!state.noFoodAtCriticalLogged) {
				state.noFoodAtCriticalLogged = true;
				if (Logging.logEnabled) {
					Logging.logMessage("[EmergencyFood] Critical hunger reached but no edible reachable food is available on any settlement level; leaving current behavior unchanged settler="
							+ human.getStringID() + "#" + human.getUniqueID() + " hunger=" + human.hungerLevel
							+ " level=" + (human.getLevel() == null ? "null" : human.getLevel().getIdentifier()));
				}
			}
			return;
		}

		state.noFoodAtCriticalLogged = false;
		if (state.emergencyEating) {
			human.setPrioritizeNextJob(ConsumeFoodLevelJob.class, true);
			if (human.hasActiveJob() && !isCurrentFoodSequence(human)) {
				human.cancelJob();
				if (Logging.logEnabled) {
					Logging.logMessage("[EmergencyFood] Re-preempted non-food job while emergency feeding settler="
							+ human.getStringID() + "#" + human.getUniqueID() + " hunger=" + human.hungerLevel);
				}
			}
			return;
		}

		state.emergencyEating = true;
		human.setPrioritizeNextJob(ConsumeFoodLevelJob.class, true);
		boolean cancelledActiveJob = human.hasActiveJob() && !isCurrentFoodSequence(human);
		if (cancelledActiveJob) human.cancelJob();

		if (Logging.logEnabled) {
			Logging.logMessage("[EmergencyFood] Critical hunger preemption started settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " hunger=" + human.hungerLevel
					+ " threshold=" + emergencyEatingHungerThreshold
					+ " cancelledActiveJob=" + cancelledActiveJob
					+ " workInventoryFood=" + hasWorkInventoryFood(human)
					+ " localStorageFood=" + hasReachableStorageFood(human)
					+ " remoteLevelFood=" + remoteFoodAvailable
					+ " level=" + (human.getLevel() == null ? "null" : human.getLevel().getIdentifier()));
		}

	}

	private static boolean isCurrentFoodSequence(HumanMob human) {

		if (human == null || human.ai == null) return false;
		JobSequence sequence = (JobSequence)human.ai.blackboard.getObject(JobSequence.class, "currentJobSequence");
		if (sequence == null) return false;
		GameMessage activity = sequence.getActivityDescription();
		if (!(activity instanceof LocalMessage)) return false;
		LocalMessage local = (LocalMessage)activity;
		return "activities".equals(local.category) && "consuming".equals(local.key);

	}

	private static void debugCaveHunger(HumanMob human) {

		if (!Logging.logEnabled || human == null || human.getLevel() == null || human.getSettlerSettlementServerData() == null) return;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(human.getSettlerSettlementServerData());
		if (domain == null || domain.getLevelType(human.getLevel().getIdentifier()) != SettlementLevelType.CAVE) return;

		long now = human.getWorldEntity().getTime();
		StarvationState state = starvationStates.get(human);
		if (state == null) {
			state = new StarvationState(now + starvationDamageInterval, Math.max(1, human.getHealth()));
			state.starving = false;
			starvationStates.put(human, state);
		}
		if (now < state.nextDebugTime) return;
		state.nextDebugTime = now + hungerDebugInterval;

		boolean sleeping = isSleepingInBed(human);
		boolean usesNightSchedule = human instanceof GuardHumanMob && GuardDutySystem.usesNightSchedule((GuardHumanMob)human);
		boolean night = human.getWorldEntity().isNight();
		boolean vanillaOrGuardDrainActive = usesNightSchedule ? night : !night;
		boolean workInventoryFood = hasWorkInventoryFood(human);
		boolean storageFood = hasReachableStorageFood(human);
		boolean remoteFood = !workInventoryFood && !storageFood && SettlementCrossLevelJobSystem.hasReachableFoodOnOtherLevel(human);
		boolean surfaceAccess = hasSurfaceAccess(human);
		boolean stranded = !workInventoryFood && !storageFood && !surfaceAccess;
		float delta = Float.isNaN(state.lastDebugHunger) ? 0.0F : human.hungerLevel - state.lastDebugHunger;

		Logging.logMessage("[StarvationDebug] Cave hunger settler=" + human.getStringID() + "#" + human.getUniqueID()
				+ " hunger=" + human.hungerLevel
				+ " delta=" + delta
				+ " health=" + human.getHealth() + "/" + human.getMaxHealth()
				+ " sleeping=" + sleeping
				+ " night=" + night
				+ " nightSchedule=" + usesNightSchedule
				+ " normalDrainActive=" + vanillaOrGuardDrainActive
				+ " sleepFloor=" + sleepHungerFloor
				+ " workInventoryFood=" + workInventoryFood
				+ " reachableCaveStorageFood=" + storageFood
				+ " reachableOtherLevelFood=" + remoteFood
				+ " surfaceAccess=" + surfaceAccess
				+ " stranded=" + stranded);
		state.lastDebugHunger = human.hungerLevel;

	}

	private static void tickSleepingHunger(HumanMob human) {

		StarvationState state = starvationStates.get(human);
		if (state == null) {
			state = new StarvationState(human.getWorldEntity().getTime() + starvationDamageInterval, Math.max(1, human.getHealth()));
			starvationStates.put(human, state);
		}

		boolean sleeping = isSleepingInBed(human);
		if (!sleeping) {
			state.wasSleeping = false;
			state.sleepMinimumHunger = Float.NaN;
			return;
		}

		if (!state.wasSleeping) {
			state.wasSleeping = true;
			state.sleepMinimumHunger = Math.min(human.hungerLevel, sleepHungerFloor);
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Settler began sleeping hunger=" + human.hungerLevel
						+ " sleepMinimum=" + state.sleepMinimumHunger
						+ " settler=" + human.getStringID() + "#" + human.getUniqueID());
			}
		}

		// HumanMob.serverTick has already run before this method. If vanilla (or the night-guard
		// patch) drained hunger while the settler was asleep, restore only the amount that crossed
		// the sleep minimum. This makes the one-minute reserve hold through accelerated sleep and
		// the night-to-day transition without ever increasing hunger above what the settler had when
		// they went to bed.
		if (human.hungerLevel < state.sleepMinimumHunger) {
			float before = human.hungerLevel;
			human.hungerLevel = state.sleepMinimumHunger;
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Restored sleeping hunger minimum after normal drain settler="
						+ human.getStringID() + "#" + human.getUniqueID()
						+ " before=" + before + " restored=" + human.hungerLevel);
			}
		}

		// Vanilla already drains ordinary settlers during the day, while the custom night-guard
		// hunger patch drains night-duty guards during the night. Only fill the sleeping gap.
		boolean usesNightSchedule = human instanceof GuardHumanMob && GuardDutySystem.usesNightSchedule((GuardHumanMob)human);
		boolean hungerAlreadyDrains = usesNightSchedule ? human.getWorldEntity().isNight() : !human.getWorldEntity().isNight();
		if (hungerAlreadyDrains) return;
		if (human.hungerLevel <= state.sleepMinimumHunger) return;

		float usedHunger = (float)(50.0 / (1000.0 * (double)HumanMob.secondsToPassAtFullHunger));
		float available = human.hungerLevel - state.sleepMinimumHunger;
		float actualUse = Math.min(usedHunger, available);
		if (actualUse <= 0.0F) return;

		human.useHunger(actualUse, false);
		if (human.hungerLevel < state.sleepMinimumHunger) human.hungerLevel = state.sleepMinimumHunger;

		if (Logging.logEnabled && human.hungerLevel <= state.sleepMinimumHunger) {
			Logging.logMessage("[Starvation] Sleeping hunger reached safety floor settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " hunger=" + human.hungerLevel + " sleepMinimum=" + state.sleepMinimumHunger);
		}

	}

	private static void tickStarvation(HumanMob human) {

		if (human.hungerLevel > 0.0F) {
			StarvationState state = starvationStates.get(human);
			if (state != null && state.starving) {
				state.starving = false;
				state.damageTicks = 0;
				if (Logging.logEnabled) {
					Logging.logMessage("[Starvation] Starvation ended after settler ate settler=" + human.getStringID() + "#" + human.getUniqueID()
							+ " hunger=" + human.hungerLevel + " health=" + human.getHealth());
				}
			}
			return;
		}

		long now = human.getWorldEntity().getTime();
		StarvationState state = starvationStates.get(human);
		if (state == null) {
			state = new StarvationState(now + starvationDamageInterval, Math.max(1, human.getHealth()));
			starvationStates.put(human, state);
		}
		if (!state.starving) {
			state.starving = true;
			state.nextDamageTime = now + starvationDamageInterval;
			state.startHealth = Math.max(1, human.getHealth());
			state.damageTicks = 0;
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Starvation started settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " health=" + human.getHealth() + " level=" + (human.getLevel() == null ? "null" : human.getLevel().getIdentifier()));
		}

		if (now < state.nextDamageTime) return;
		while (state.nextDamageTime <= now) state.nextDamageTime += starvationDamageInterval;

		if (isStrandedWithoutFood(human)) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Instantly downing stranded starving settler settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " level=" + human.getLevel().getIdentifier() + " health=" + human.getHealth());
			starvationStates.remove(human);
			revealStarvationJournal(human);
			forceDownedByStarvation(human);
			return;
		}

		state.damageTicks++;
		if (state.damageTicks >= starvationDamageTicksToDeath) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Starvation downing settler settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " elapsedDamageTicks=" + state.damageTicks + " startHealth=" + state.startHealth);
			starvationStates.remove(human);
			revealStarvationJournal(human);
			forceDownedByStarvation(human);
			return;
		}

		int remainingTicks = starvationDamageTicksToDeath - state.damageTicks;
		int targetHealth = (int)Math.ceil((double)state.startHealth * (double)remainingTicks / (double)starvationDamageTicksToDeath);
		int newHealth = Math.min(human.getHealth(), Math.max(1, targetHealth));
		if (newHealth < human.getHealth()) {
			int beforeHealth = human.getHealth();
			human.setHealth(newHealth);
			int actualDamage = beforeHealth - human.getHealth();
			if (actualDamage > 0) {
				revealStarvationJournal(human);
				if (Logging.logEnabled) Logging.logMessage("[Starvation] Applied starvation damage settler=" + human.getStringID() + "#" + human.getUniqueID()
						+ " damage=" + actualDamage + " health=" + human.getHealth() + " tick=" + state.damageTicks + "/" + starvationDamageTicksToDeath);
			}
		}

	}

	private static void revealStarvationJournal(HumanMob human) {

		if (human == null) return;
		if (human.getSettlerSettlementServerData() == null) {
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Could not reveal starvation Journal because settler has no settlement data settler="
						+ human.getStringID() + "#" + human.getUniqueID());
			}
			return;
		}

		GuideProgressionSystem.revealForSettlement(human.getSettlerSettlementServerData(), "starvation");

	}

	public static GameMessage replaceStarvationDeathMessage(GameMessage message) {

		StarvationChatContext context = starvationChatContext.get();
		if (context == null || message == null || context.expectedVanillaMessage == null || context.replacementMessage == null) return message;

		if (context.expectedVanillaMessage.isSame(message)) {
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Replaced vanilla settler downed message with starvation death message settler="
						+ context.settlerStringID + "#" + context.settlerUniqueID);
			}
			return context.replacementMessage;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[Starvation] Chat sent during starvation downing did not match vanilla downed message; leaving unchanged settler="
					+ context.settlerStringID + "#" + context.settlerUniqueID
					+ " message=" + message.translate());
		}
		return message;

	}

	private static StarvationChatContext buildStarvationChatContext(HumanMob human) {

		if (human == null) return null;
		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		if (settlement == null) return null;

		GameMessage displayName = new LocalMessage(
				"deaths",
				"settlerfrom",
				new Object[]{"victim", human.getLocalization(), "settlement", settlement.networkData.getSettlementName()}
		);
		GameMessage expected = new GameMessageBuilder()
				.append("§9")
				.append(new LocalMessage("deaths", "settlerdowned", "settler", displayName));
		GameMessage replacement = new GameMessageBuilder()
				.append("§9")
				.append(new LocalMessage("misc", "settlerstarved", "settler", human.getLocalization()));

		return new StarvationChatContext(
				expected,
				replacement,
				human.getStringID(),
				human.getUniqueID()
		);

	}

	private static void forceDownedByStarvation(HumanMob human) {

		if (human == null || !human.isServer() || !human.isSettler() || human.isDowned()) return;

		WorldSettings settings = human.getWorldSettings();
		if (settings == null) {
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Could not down starving settler because world settings were unavailable settler="
						+ human.getStringID() + "#" + human.getUniqueID());
			}
			return;
		}

		boolean previousCanSettlersDie = settings.canSettlersDie;
		StarvationChatContext previousChatContext = starvationChatContext.get();
		try {
			// Route starvation through vanilla's own non-permanent settler death branch so all
			// downed/revival bookkeeping is preserved even in worlds where ordinary combat deaths
			// are configured to be permanent. Restore the world option immediately afterwards.
			settings.canSettlersDie = false;
			StarvationChatContext context = buildStarvationChatContext(human);
			if (context != null) starvationChatContext.set(context);
			human.setHealth(0);
		}
		catch (Throwable error) {
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Failed to down starving settler settler=" + human.getStringID() + "#" + human.getUniqueID()
						+ " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
			}
		}
		finally {
			if (previousChatContext == null) starvationChatContext.remove();
			else starvationChatContext.set(previousChatContext);
			settings.canSettlersDie = previousCanSettlersDie;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[Starvation] Starvation downing result settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " downed=" + human.isDowned() + " health=" + human.getHealth()
					+ " restoredCanSettlersDie=" + settings.canSettlersDie);
		}

	}

	private static boolean isEdibleFood(InventoryItem item, ItemCategoriesFilter dietFilter) {

		if (item == null || !item.item.isFoodItem()) return false;
		if (dietFilter != null && !dietFilter.isItemAllowed(item.item)) return false;
		FoodConsumableItem food = (FoodConsumableItem)item.item;
		return food.nutrition > 0 && food.quality != null;

	}

	private static final class StarvationChatContext {
		final GameMessage expectedVanillaMessage;
		final GameMessage replacementMessage;
		final String settlerStringID;
		final int settlerUniqueID;

		StarvationChatContext(GameMessage expectedVanillaMessage, GameMessage replacementMessage, String settlerStringID, int settlerUniqueID) {
			this.expectedVanillaMessage = expectedVanillaMessage;
			this.replacementMessage = replacementMessage;
			this.settlerStringID = settlerStringID;
			this.settlerUniqueID = settlerUniqueID;
		}
	}

	private static final class StarvationState {
		long nextDamageTime;
		long nextDebugTime;
		int startHealth;
		int damageTicks;
		boolean starving;
		boolean wasSleeping;
		float sleepMinimumHunger = Float.NaN;
		float lastDebugHunger = Float.NaN;
		long nextEmergencyFoodCheckTime;
		boolean emergencyEating;
		boolean noFoodAtCriticalLogged;

		StarvationState(long nextDamageTime, int startHealth) {
			this.nextDamageTime = nextDamageTime;
			this.startHealth = startHealth;
		}
	}
}
