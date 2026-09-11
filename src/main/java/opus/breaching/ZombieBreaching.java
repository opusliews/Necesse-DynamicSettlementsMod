package opus.breaching;

import java.awt.Point;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.entity.mobs.BasicPathDoorOption;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.ai.path.SubRegionPathResult;
import necesse.level.gameObject.DoorObject;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.regionSystem.RegionType;
import necesse.level.maps.regionSystem.SubRegion;
import opus.damage.MaterialWeatheringClassifier;
import opus.damage.WeatheringMaterialTier;

public final class ZombieBreaching {
	public static final String passiveTargetKey = "settlementBuildersZombiePassiveTarget";
	private static final int breachCooldownMs = 1000;
	private static final Map<Mob, State> states = Collections.synchronizedMap(new WeakHashMap<>());

	private ZombieBreaching() {
	}

	public static boolean isZombie(Mob mob) {
		if (mob == null || !mob.isHostile) {
			return false;
		}

		String stringID = mob.getStringID();
		return stringID != null && stringID.toLowerCase(Locale.ROOT).contains("zombie");
	}

	public static State getState(Mob mob) {
		synchronized (states) {
			return states.computeIfAbsent(mob, State::new);
		}
	}

	public static void serverTick(Mob zombie) {
		if (!isZombie(zombie) || zombie.ai == null || zombie.getLevel() == null) {
			return;
		}

		State state = getState(zombie);
		Mob playerTarget = zombie.ai.blackboard.getObject(Mob.class, "currentTarget");
		Mob passiveTarget = zombie.ai.blackboard.getObject(Mob.class, passiveTargetKey);
		Mob target = playerTarget != null ? playerTarget : passiveTarget;

		if (target != state.currentTarget) {
			state.currentTarget = target;
			if (target != null) {
				state.rememberedDoor = null;
			}
			state.clearActiveBreach();
			state.resetBreachDecision();
		}

		if (state.activeBreachTile != null) {
			GameObject breachObject = zombie.getLevel().getObject(state.activeBreachTile.x, state.activeBreachTile.y);
			if (!breachObject.isFence || !isBreakableTier(breachObject)) {
				state.clearActiveBreach();
				state.resetBreachDecision();
			}
		}

		if (state.rememberedDoor != null) {
			GameObject remembered = zombie.getLevel().getObject(state.rememberedDoor.x, state.rememberedDoor.y);
			if (!(remembered instanceof DoorObject) || remembered.isFence || !isBreakableTier(remembered)) {
				state.rememberedDoor = null;
			}
		}

		if (target == null || target.removed() || !target.isSamePlace(zombie)) {
			return;
		}

		// Decide normal-route-vs-breach on the server thread, never from the
		// tile pathfinder. Keep checking while normal movement is still possible;
		// PathDoorOption caches region paths and invalidates them when topology changes.
		// Once breaching is required, keep that decision latched until the chosen
		// barrier is gone or the target changes.
		if (!state.breachRequired && state.activeBreachTile == null) {
			Point targetTile = new Point(target.getTileX(), target.getTileY());
			boolean normalRoute = state.normalPathDoorOption.canMoveToTile(
					zombie.getTileX(), zombie.getTileY(), targetTile.x, targetTile.y, false);
			state.setBreachDecision(target, targetTile, !normalRoute);
		}

		GameObject targetObject = zombie.getLevel().getObject(target.getTileX(), target.getTileY());
		if (targetObject instanceof DoorObject && !targetObject.isFence && isBreakableTier(targetObject)) {
			state.rememberedDoor = new Point(target.getTileX(), target.getTileY());
		}
	}

	public static boolean isBreakableTier(GameObject object) {
		WeatheringMaterialTier tier = getTier(object);
		return tier != null && tier.getTier() < 4;
	}

	public static WeatheringMaterialTier getTier(GameObject object) {
		WeatheringMaterialTier tier = MaterialWeatheringClassifier.getObjectTier(object);
		if (tier != null || object == null || !object.isFence) {
			return tier;
		}

		String stringID = object.getStringID();
		if (stringID == null) {
			return null;
		}

		if (stringID.startsWith("wood")
				|| stringID.startsWith("bandit")
				|| stringID.startsWith("foresthedge")
				|| stringID.startsWith("snowhedge")
				|| stringID.startsWith("plainshedge")
				|| stringID.startsWith("swamphedge")) {
			return WeatheringMaterialTier.WOOD;
		}

		if (stringID.startsWith("stone")) {
			return WeatheringMaterialTier.SURFACE_MASONRY;
		}

		if (stringID.startsWith("iron")
				|| stringID.startsWith("jail")
				|| stringID.startsWith("crypt")) {
			return WeatheringMaterialTier.REINFORCED;
		}

		return null;
	}

	public static boolean isMetalObject(GameObject object) {
		if (object == null || object.getStringID() == null) {
			return false;
		}

		String stringID = object.getStringID();
		return stringID.startsWith("iron") || stringID.startsWith("jail");
	}

	public static boolean isBreachingObject(Mob zombie, LevelObject lo) {
		if (!isZombie(zombie) || lo == null || lo.object == null || !isBreakableTier(lo.object)) {
			return false;
		}

		State state = getState(zombie);
		if (state.rememberedDoor != null && state.rememberedDoor.x == lo.tileX && state.rememberedDoor.y == lo.tileY) {
			return lo.object instanceof DoorObject && !lo.object.isFence;
		}

		if (!lo.object.isFence) {
			return false;
		}

		Mob target = state.currentTarget;
		if (target == null || target.removed() || !target.isSamePlace(zombie)) {
			return false;
		}

		if (state.activeBreachTile != null) {
			return state.isActiveBreach(lo.tileX, lo.tileY, target);
		}

		return state.breachDecisionTarget == target && state.breachRequired;
	}

	public static double getBreakPathCost(Level level, int tileX, GameObject object) {
		WeatheringMaterialTier tier = getTier(object);
		if (tier == null || tier.getTier() >= 4) {
			return -1.0;
		}

		// Keep path selection proportional to actual breach time. A normal route is
		// still preferred by canBreakDown(), so this mostly chooses the cheapest
		// barrier when several breach points are possible.
		return object.getBreakDownPathCost(level, tileX) * (double)(tier.getTier() + 1);
	}

	public static int getBreakDamageAndConfigureCooldown(Mob zombie, LevelObject lo) {
		trackActiveBreach(zombie, lo);

		WeatheringMaterialTier tier = getTier(lo.object);
		if (tier == null || tier.getTier() >= 4) {
			return 0;
		}

		int health = Math.max(1, lo.object.objectHealth);
		int desiredHits = 30 * (tier.getTier() + 1);
		zombie.pathBreakCooldown = breachCooldownMs;
		return Math.max(1, (int)Math.ceil(health / (double)desiredHits));
	}

	public static int getProjectileBreakDamage(Mob zombie, LevelObject lo) {
		trackActiveBreach(zombie, lo);

		WeatheringMaterialTier tier = getTier(lo.object);
		if (tier == null || tier.getTier() >= 4) {
			return 0;
		}

		int health = Math.max(1, lo.object.objectHealth);
		int desiredHits = 30 * (tier.getTier() + 1);
		return Math.max(1, (int)Math.ceil(health / (double)desiredHits));
	}

	private static void trackActiveBreach(Mob zombie, LevelObject lo) {
		State state = getState(zombie);
		if (lo != null && lo.object != null && lo.object.isFence && state.currentTarget != null) {
			state.setActiveBreach(lo.tileX, lo.tileY, state.currentTarget);
		}
	}

	public static final class State {
		public final ZombiePathDoorOption pathDoorOption;
		public final BasicPathDoorOption normalPathDoorOption;
		public volatile Mob currentTarget;
		public volatile Point rememberedDoor;
		public volatile Point activeBreachTile;
		public volatile Mob activeBreachTarget;
		public volatile Mob breachDecisionTarget;
		public volatile Point breachDecisionTargetTile;
		public volatile boolean breachRequired;

		private State(Mob zombie) {
			this.pathDoorOption = new ZombiePathDoorOption(zombie);
			this.normalPathDoorOption = new BasicPathDoorOption(
					"SETTLEMENT_BUILDERS_ZOMBIE_NORMAL", zombie.getLevel(), false, false);
		}

		public void setActiveBreach(int tileX, int tileY, Mob target) {
			activeBreachTile = new Point(tileX, tileY);
			activeBreachTarget = target;
			breachRequired = true;
		}

		public void clearActiveBreach() {
			activeBreachTile = null;
			activeBreachTarget = null;
		}

		public void setBreachDecision(Mob target, Point targetTile, boolean required) {
			breachDecisionTarget = target;
			breachDecisionTargetTile = new Point(targetTile);
			breachRequired = required;
		}

		public void resetBreachDecision() {
			breachDecisionTarget = null;
			breachDecisionTargetTile = null;
			breachRequired = false;
			normalPathDoorOption.invalidateCache();
		}

		public boolean isActiveBreach(int tileX, int tileY, Mob target) {
			Point tile = activeBreachTile;
			return tile != null && tile.x == tileX && tile.y == tileY && activeBreachTarget == target;
		}
	}

	public static final class ZombiePathDoorOption extends PathDoorOption {
		private final WeakReference<Mob> zombieRef;

		private ZombiePathDoorOption(Mob zombie) {
			super("SETTLEMENT_BUILDERS_ZOMBIE", zombie.getLevel());
			this.zombieRef = new WeakReference<>(zombie);
		}

		@Override
		public SubRegionPathResult canPathThrough(SubRegion subregion) {
			RegionType type = subregion.getType();
			if (type == RegionType.FENCE || type == RegionType.FENCE_GATE || type == RegionType.DOOR) {
				return SubRegionPathResult.CHECK_EACH_TILE;
			}
			return type.isSolid ? SubRegionPathResult.INVALID : SubRegionPathResult.VALID;
		}

		@Override
		public boolean canPathThroughCheckTile(SubRegion subregion, int tileX, int tileY) {
			GameObject object = level.getObject(tileX, tileY);

			// Region-level reachability must stay stable and cacheable. Treat every
			// breakable fence/gate as potentially traversable here. Actual damage
			// permission is handled separately by isBreachingObject().
			if (object.isFence && isBreakableTier(object)) {
				return true;
			}

			if (canBreakDown(tileX, tileY)) {
				return true;
			}

			return object instanceof DoorObject
					&& ((DoorObject)object).isOpen(level, tileX, tileY, level.getObjectRotation(tileX, tileY));
		}

		@Override
		public boolean canBreakDown(int tileX, int tileY) {
			Mob zombie = zombieRef.get();
			if (zombie == null) {
				return false;
			}

			GameObject object = level.getObject(tileX, tileY);
			if (!isBreakableTier(object)) {
				return false;
			}

			State state = getState(zombie);
			if (state.rememberedDoor != null && state.rememberedDoor.x == tileX && state.rememberedDoor.y == tileY) {
				return object instanceof DoorObject && !object.isFence;
			}

			// Pathfinding must always be allowed to plan through eligible fences/gates.
			// Whether the zombie may actually damage the barrier is decided separately
			// by isBreachingObject(), after the normal-route check has completed.
			return object.isFence;
		}

		@Override
		public boolean canOpen(int tileX, int tileY) {
			return false;
		}

		@Override
		public boolean canClose(int tileX, int tileY) {
			return false;
		}

		@Override
		public boolean doorChangeInvalidatesCache(DoorObject lastDoor, DoorObject newDoor, int tileX, int tileY) {
			return true;
		}
	}
}
