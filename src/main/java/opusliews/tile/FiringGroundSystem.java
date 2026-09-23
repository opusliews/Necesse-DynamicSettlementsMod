package opusliews.tile;

import necesse.engine.registries.TileRegistry;
import necesse.level.maps.Level;

public final class FiringGroundSystem {
	private FiringGroundSystem() {
	}

	public static boolean isSupportedGround(Level level, int tileX, int tileY) {
		if (level == null || !level.isTileWithinBounds(tileX, tileY)) return false;

		String tileStringID = level.getTile(tileX, tileY).getStringID();
		switch (tileStringID) {
			case "dirttile":
			case "graniterocktile":
			case "grasstile":
			case "overgrowngrasstile":
			case "graveltile":
			case "mudtile":
			case "plainsgrasstile":
			case "overgrownplainsgrasstile":
			case "sandtile":
			case "snowtile":
			case "swampgrasstile":
			case "overgrownswampgrasstile":
				return true;
			default:
				return false;
		}
	}

	public static boolean shouldDigToDirt(Level level, int tileX, int tileY) {
		return isSupportedGround(level, tileX, tileY) && level.getTileID(tileX, tileY) != TileRegistry.dirtID;
	}
}
