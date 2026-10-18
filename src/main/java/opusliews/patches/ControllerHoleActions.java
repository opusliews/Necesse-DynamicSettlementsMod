package opusliews.patches;

import java.awt.Point;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.item.Item;
import necesse.level.maps.Level;
import opusliews.item.DirtPileItem;
import opusliews.item.FireableMatItem;
import opusliews.tile.CharcoalPitSystem;

/** Shared targeting helpers for the controller hole interaction patches. */
public class ControllerHoleActions {

	public static class Handler {
		public static boolean isPitMaterial(Item item) {
			return item instanceof DirtPileItem || item instanceof FireableMatItem || item.isGlobalIngredient("anylog");
		}

		public static Point findHole(Level level, PlayerMob player, float aimX, float aimY) {
			float length = (float)Math.sqrt(aimX * aimX + aimY * aimY);
			if (length < 0.001F) return null;
			float dx = aimX / length;
			float dy = aimY / length;
			Point best = null;
			int previousX = Integer.MIN_VALUE;
			int previousY = Integer.MIN_VALUE;
			// Match the existing pit interaction radius; stop at the first hole along the aim ray.
			for (int distance = 16; distance <= 100; distance += 4) {
				int tileX = (int)Math.floor((player.x + dx * distance) / 32.0F);
				int tileY = (int)Math.floor((player.y + dy * distance) / 32.0F);
				if (tileX == previousX && tileY == previousY) continue;
				previousX = tileX;
				previousY = tileY;
				if (!CharcoalPitSystem.isPitTile(level, tileX, tileY)) continue;
				if (player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0) > 100.0) continue;
				best = new Point(tileX * 32 + 16, tileY * 32 + 16);
				break;
			}
			return best;
		}

	}
}
