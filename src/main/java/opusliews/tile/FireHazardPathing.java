package opusliews.tile;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.engine.util.MovedRectangle;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.levelEvent.mobAbilityLevelEvent.PhoenixFeatherGroundFireEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.behaviourTree.AINode;
import necesse.entity.mobs.ai.behaviourTree.leaves.HumanJobMoveToAINode;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.ai.path.TilePathfinding;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;

public final class FireHazardPathing {
	private static final Map<Level, GroundFireCache> groundFireCaches = Collections.synchronizedMap(new WeakHashMap<>());

	private FireHazardPathing() {
	}

	public static boolean shouldBlockFire(Level level, Mob mob, int tileX, int tileY) {
		if (level == null || !(mob instanceof HumanMob)) return false;
		return isFireHazard(level, tileX, tileY);
	}

	public static boolean pathTargetContainsFire(TilePathfinding finder, Point targetTile) {
		if (finder == null || finder.level == null || !(finder.mob instanceof HumanMob) || targetTile == null) return false;

		Rectangle offsets = finder.tileCollisionOffsets;
		for (int x = 0; x < offsets.width; x++) {
			int tileX = targetTile.x + offsets.x + x;
			for (int y = 0; y < offsets.height; y++) {
				int tileY = targetTile.y + offsets.y + y;
				if (isFireHazard(finder.level, tileX, tileY)) return true;
			}
		}

		return false;
	}

	public static boolean stopStalePathIfFireAppeared(AIMover mover, Mob mob) {
		if (mover == null || !(mob instanceof HumanMob) || !mover.isMoving()) return false;

		Point destination = mover.getCurrentDestination();
		if (destination == null || !isFireHazard(mob.getLevel(), destination.x, destination.y)) return false;

		AINode movingFor = mover.getMovingFor();
		mover.stopMoving(mob);
		if (movingFor instanceof HumanJobMoveToAINode) {
			((HumanJobMoveToAINode)movingFor).nextMoveTime = 0L;
		}
		return true;
	}

	public static boolean directMovementCrossesFire(Mob mob, int targetX, int targetY) {
		if (!(mob instanceof HumanMob)) return false;
		Level level = mob.getLevel();
		if (level == null) return false;

		MovedRectangle movement = new MovedRectangle(mob, targetX, targetY);
		Rectangle bounds = movement.getBounds();
		int minTileX = GameMath.getTileCoordinate(bounds.x);
		int minTileY = GameMath.getTileCoordinate(bounds.y);
		int maxTileX = GameMath.getTileCoordinate(bounds.x + Math.max(0, bounds.width - 1));
		int maxTileY = GameMath.getTileCoordinate(bounds.y + Math.max(0, bounds.height - 1));

		for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
			for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
				if (!isFireHazard(level, tileX, tileY)) continue;
				Rectangle fireBounds = new Rectangle(tileX * 32, tileY * 32, 32, 32);
				if (movement.intersects(fireBounds)) return true;
			}
		}

		return false;
	}

	public static boolean isFireHazard(Level level, int tileX, int tileY) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) return false;

		int tileID = level.getTileID(tileX, tileY);
		if (tileID == TileRegistry.getTileID(BurningCharcoalPitTile.stringID)
				|| tileID == TileRegistry.getTileID(BurningFiringPitTile.stringID)) {
			return true;
		}

		return getGroundFireCache(level).contains(tileX, tileY);
	}

	public static void invalidateGroundFireCache(Level level) {
		if (level == null) return;
		synchronized (groundFireCaches) {
			groundFireCaches.remove(level);
		}
	}

	private static GroundFireCache getGroundFireCache(Level level) {
		long levelTime = level.getTime();
		GroundFireCache cache;

		synchronized (groundFireCaches) {
			cache = groundFireCaches.get(level);
			if (cache != null && cache.levelTime == levelTime) return cache;

			cache = buildGroundFireCache(level, levelTime);
			groundFireCaches.put(level, cache);
		}

		return cache;
	}

	private static GroundFireCache buildGroundFireCache(Level level, long levelTime) {
		Set<Long> fireTiles = new HashSet<>();

		for (Object eventObject : level.entityManager.events) {
			if (!(eventObject instanceof PhoenixFeatherGroundFireEvent)) continue;

			LevelEvent event = (LevelEvent)eventObject;
			if (event.isOver()) continue;

			Shape hitBox = ((PhoenixFeatherGroundFireEvent)eventObject).getHitBox();
			if (hitBox == null) continue;

			Rectangle hitBounds = hitBox.getBounds();
			if (hitBounds.width <= 0 || hitBounds.height <= 0) continue;

			int minTileX = GameMath.getTileCoordinate(hitBounds.x);
			int minTileY = GameMath.getTileCoordinate(hitBounds.y);
			int maxTileX = GameMath.getTileCoordinate(hitBounds.x + hitBounds.width - 1);
			int maxTileY = GameMath.getTileCoordinate(hitBounds.y + hitBounds.height - 1);

			for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
				for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
					if (!level.isTileWithinBounds(tileX, tileY)) continue;
					Rectangle tileBounds = new Rectangle(tileX * 32, tileY * 32, 32, 32);
					if (hitBox.intersects(tileBounds)) fireTiles.add(getTileKey(tileX, tileY));
				}
			}
		}

		return new GroundFireCache(levelTime, fireTiles);
	}

	private static long getTileKey(int tileX, int tileY) {
		return ((long)tileX << 32) ^ (tileY & 0xFFFFFFFFL);
	}

	private static class GroundFireCache {
		private final long levelTime;
		private final Set<Long> fireTiles;

		private GroundFireCache(long levelTime, Set<Long> fireTiles) {
			this.levelTime = levelTime;
			this.fireTiles = fireTiles;
		}

		private boolean contains(int tileX, int tileY) {
			return fireTiles.contains(getTileKey(tileX, tileY));
		}
	}
}
