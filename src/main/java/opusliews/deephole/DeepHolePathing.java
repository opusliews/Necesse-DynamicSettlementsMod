package opusliews.deephole;

import java.awt.Point;
import java.awt.Rectangle;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.util.GameMath;
import necesse.engine.util.MovedRectangle;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.ai.path.TilePathfinding;
import necesse.level.maps.Level;
import opusliews.tile.DeepHoleTile;

public final class DeepHolePathing {
	private DeepHolePathing() {
	}

	public static boolean shouldBlockDeepHole(Level level, Mob mob, int tileX, int tileY) {
		if (level == null || mob == null || mob instanceof PlayerMob || mob.isBoss()) return false;
		return isDangerousDeepHole(level, tileX, tileY);
	}

	public static boolean pathTargetContainsDeepHole(TilePathfinding finder, Point targetTile) {
		if (finder == null || finder.level == null || finder.mob == null || targetTile == null) return false;
		if (finder.mob instanceof PlayerMob || finder.mob.isBoss()) return false;

		Rectangle offsets = finder.tileCollisionOffsets;
		for (int x = 0; x < offsets.width; x++) {
			int tileX = targetTile.x + offsets.x + x;
			for (int y = 0; y < offsets.height; y++) {
				int tileY = targetTile.y + offsets.y + y;
				if (isDangerousDeepHole(finder.level, tileX, tileY)) return true;
			}
		}

		return false;
	}

	public static boolean canPassDeepHoleDiagonal(TilePathfinding finder, Point fromTile, Point offsetTile) {
		if (finder == null || finder.level == null || finder.mob == null) return false;
		if (finder.mob instanceof PlayerMob || finder.mob.isBoss()) return false;
		if (offsetTile == null || offsetTile.x == 0 || offsetTile.y == 0) return false;

		Rectangle offsets = finder.tileCollisionOffsets;
		boolean isLeft = offsetTile.x < 0;
		int xCheck = isLeft
				? fromTile.x + offsets.x + offsetTile.x
				: fromTile.x + offsets.x + offsetTile.x + offsets.width - 1;

		boolean isUp = offsetTile.y < 0;
		int yCheck = isUp
				? fromTile.y + offsets.y + offsetTile.y
				: fromTile.y + offsets.y + offsetTile.y + offsets.height - 1;

		return isDangerousDeepHole(finder.level, xCheck, fromTile.y)
				&& isDangerousDeepHole(finder.level, fromTile.x, yCheck);
	}

	public static boolean directMovementCrossesDeepHole(Mob mob, int targetX, int targetY) {
		if (mob == null || mob instanceof PlayerMob || mob.isBoss()) return false;
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
				if (!isDangerousDeepHole(level, tileX, tileY)) continue;
				Rectangle holeBounds = new Rectangle(tileX * 32, tileY * 32, 32, 32);
				if (movement.intersects(holeBounds)) return true;
			}
		}

		return false;
	}

	private static boolean isDangerousDeepHole(Level level, int tileX, int tileY) {
		if (!level.isTileWithinBounds(tileX, tileY)) return false;
		if (level.getTileID(tileX, tileY) != TileRegistry.getTileID(DeepHoleTile.stringID)) return false;

		int objectID = level.getObjectID(tileX, tileY);
		return objectID != ObjectRegistry.getObjectID("holecaveladder")
				&& objectID != ObjectRegistry.getObjectID("ladderdown");
	}
}
