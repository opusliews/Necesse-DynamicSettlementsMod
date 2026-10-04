package opusliews.hunger;

import necesse.entity.mobs.ObjectUserActive;
import necesse.engine.world.WorldSettings;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.placeableItem.consumableItem.food.FoodConsumableItem;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.gameObject.furniture.SettlerBedObject;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import necesse.level.maps.levelData.settlementData.settler.FoodQuality;
import necesse.level.maps.levelData.settlementData.settler.Settler;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageFoodQualityIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import opusliews.logging.Logging;
import opusliews.guard.GuardDutySystem;
import opusliews.multilevelsettlement.SettlementCrossLevelRouting;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class SettlerStarvationSystem {
	public static final float sleepHungerFloor = 60.0F / HumanMob.secondsToPassAtFullHunger;
	public static final long starvationDamageInterval = 1000L;
	public static final int starvationDamageTicksToDeath = 20;
	public static final long hungerDebugInterval = 1000L;

	private static final Map<HumanMob, StarvationState> starvationStates = Collections.synchronizedMap(new WeakHashMap<>());

	private SettlerStarvationSystem() {
	}

	public static void serverTick(HumanMob human) {
		if (human == null || !human.isServer() || !human.isSettler() || human.removed()) return;

		tickSleepingHunger(human);
		debugCaveHunger(human);
		tickStarvation(human);
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

		SettlementStorageRecords storageRecords;
		try {
			storageRecords = PickupSettlementStorageActiveJob.getStorageRecords(human);
		}
		catch (Throwable error) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Failed to obtain settlement storage records settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
			return false;
		}
		if (storageRecords == null) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] No current-level storage records available while checking food settler=" + human.getStringID() + "#" + human.getUniqueID());
			return false;
		}

		SettlementStorageFoodQualityIndex foodIndex = (SettlementStorageFoodQualityIndex)storageRecords.getIndex(SettlementStorageFoodQualityIndex.class);
		if (foodIndex == null) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Settlement storage has no food quality index settler=" + human.getStringID() + "#" + human.getUniqueID());
			return false;
		}

		for (FoodQuality quality : Settler.foodQualities.descendingSet()) {
			SettlementStorageRecordsRegionData data = foodIndex.getFoodQuality(quality);
			if (data == null) continue;
			try {
				SettlementStoragePickupSlot slot = data.startFinder(human).findFirstItemPickup(foodFilter);
				if (slot != null) return true;
			}
			catch (Throwable error) {
				if (Logging.logEnabled) Logging.logMessage("[Starvation] Reachable-food lookup failed settler=" + human.getStringID() + "#" + human.getUniqueID()
						+ " quality=" + quality + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
			}
		}
		return false;
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
			forceDownedByStarvation(human);
			return;
		}

		state.damageTicks++;
		if (state.damageTicks >= starvationDamageTicksToDeath) {
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Starvation downing settler settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " elapsedDamageTicks=" + state.damageTicks + " startHealth=" + state.startHealth);
			starvationStates.remove(human);
			forceDownedByStarvation(human);
			return;
		}

		int remainingTicks = starvationDamageTicksToDeath - state.damageTicks;
		int targetHealth = (int)Math.ceil((double)state.startHealth * (double)remainingTicks / (double)starvationDamageTicksToDeath);
		int newHealth = Math.min(human.getHealth(), Math.max(1, targetHealth));
		if (newHealth < human.getHealth()) {
			int damage = human.getHealth() - newHealth;
			human.setHealth(newHealth);
			if (Logging.logEnabled) Logging.logMessage("[Starvation] Applied starvation damage settler=" + human.getStringID() + "#" + human.getUniqueID()
					+ " damage=" + damage + " health=" + human.getHealth() + " tick=" + state.damageTicks + "/" + starvationDamageTicksToDeath);
		}
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
		try {
			// Route starvation through vanilla's own non-permanent settler death branch so all
			// downed/revival bookkeeping is preserved even in worlds where ordinary combat deaths
			// are configured to be permanent. Restore the world option immediately afterwards.
			settings.canSettlersDie = false;
			human.setHealth(0);
		}
		catch (Throwable error) {
			if (Logging.logEnabled) {
				Logging.logMessage("[Starvation] Failed to down starving settler settler=" + human.getStringID() + "#" + human.getUniqueID()
						+ " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
			}
		}
		finally {
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

	private static final class StarvationState {
		long nextDamageTime;
		long nextDebugTime;
		int startHealth;
		int damageTicks;
		boolean starving;
		boolean wasSleeping;
		float sleepMinimumHunger = Float.NaN;
		float lastDebugHunger = Float.NaN;

		StarvationState(long nextDamageTime, int startHealth) {
			this.nextDamageTime = nextDamageTime;
			this.startHealth = startHealth;
		}
	}
}
