package opusliews.tile;

import java.awt.Point;
import java.awt.Rectangle;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.engine.util.MovedRectangle;
import necesse.entity.levelEvent.LevelEvent;
import necesse.entity.levelEvent.mobAbilityLevelEvent.PhoenixFeatherGroundFireEvent;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.ai.path.TilePathfinding;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;

public final class FireHazardPathing {
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

		Rectangle tileBounds = new Rectangle(tileX * 32, tileY * 32, 32, 32);
		for (Object eventObject : level.entityManager.events) {
			if (!(eventObject instanceof PhoenixFeatherGroundFireEvent)) continue;
			LevelEvent event = (LevelEvent)eventObject;
			if (event.isOver()) continue;
			if (((PhoenixFeatherGroundFireEvent)eventObject).getHitBox().intersects(tileBounds)) return true;
		}

		return false;
	}
}
