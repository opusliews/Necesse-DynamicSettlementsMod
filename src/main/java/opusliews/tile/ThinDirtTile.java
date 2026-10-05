package opusliews.tile;

import necesse.level.gameTile.DirtTile;
import necesse.level.maps.Level;
import necesse.level.maps.regionSystem.SimulatePriorityList;

public class ThinDirtTile extends DirtTile {
	public static final String stringID = "thindirt";

	public ThinDirtTile() {
		// Use Necesse's native TerrainSplatterTile renderer. The supplied
		// tiles/thin_dirt_splat.png is a terrain splat sheet, not a single tile texture.
		this.terrainTextureName = "thin_dirt";
	}

	@Override
	public int getTerrainPriority() {
		// Keep the visual transition contained primarily inside the thin-dirt tile:
		// normal dirt (priority 0) splats over thin dirt (-1) at their shared edge.
		return -1;
	}

	@Override
	public void tick(Level level, int x, int y) {
		// Density-1 dirt must remain thin dirt until its explicit recovery timer expires.
	}

	@Override
	public void addSimulateLogic(Level level, int x, int y, long ticks, SimulatePriorityList list, boolean sendChanges) {
		// Recovery is world-time based in DirtDensityLevelData, so unloaded simulation does not alter it.
	}
}
