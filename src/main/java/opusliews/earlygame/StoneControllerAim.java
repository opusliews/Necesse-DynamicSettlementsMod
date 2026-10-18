package opusliews.earlygame;

import necesse.engine.util.GameMath;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.inventory.item.Item;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SurfaceGrassObject;
import necesse.level.maps.Level;

import java.awt.*;
import java.awt.geom.Point2D;

/** Controller targeting only; normal mouse attack and placement behavior is unchanged. */
public final class StoneControllerAim {
	private StoneControllerAim() {}

	public static Point getTarget(Level level, float aimX, float aimY, PlayerMob player, boolean sharpened) {
		float length = (float)Math.sqrt(aimX * aimX + aimY * aimY);
		if (length < 0.001F) return new Point((int)player.x, (int)player.y);
		float dirX = aimX / length;
		float dirY = aimY / length;
		// Prefer the first valid object along the stick direction rather than a gravel placement tile.
		for (int distance = 16; distance <= 100; distance += 4) {
			int x = (int)(player.x + dirX * distance);
			int y = (int)(player.y + dirY * distance);
			int tileX = GameMath.getTileCoordinate(x);
			int tileY = GameMath.getTileCoordinate(y);
			if (!level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) continue;
			GameObject object = level.getObject(tileX, tileY);
			boolean valid = sharpened ? (object != null && (object.isTree || object instanceof SurfaceGrassObject)) : EarlyGameStoneSystem.isRockObject(object);
			if (valid) {
				int centerX = tileX * 32 + 16;
				int centerY = tileY * 32 + 16;
				if (player.getPositionPoint().distance(centerX, centerY) <= 100.0) return new Point(centerX, centerY);
			}
		}
		return new Point((int)(player.x + dirX * 88), (int)(player.y + dirY * 88));
	}

	public static void drawAim(Item item, GameCamera camera, Level level, PlayerMob player, boolean sharpened) {
		Point2D.Float aim = player.getControllerAimDir();
		float x = aim.x;
		float y = aim.y;
		if (x == 0.0F && y == 0.0F) return;
		Point target = getTarget(level, x, y, player, sharpened);
		item.drawControllerAimInsideWindow(level.getWorldEntity().getLocalTime(), camera.getDrawX(target.x), camera.getDrawY(target.y), camera.getDrawX(player.x), camera.getDrawY(player.y));
	}
}
