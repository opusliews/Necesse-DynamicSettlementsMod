package opusliews.object;

import necesse.level.maps.Level;

public class CrudeAnvilObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "crudeanvil";

	public CrudeAnvilObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public boolean supportsForgeLinks() {
		return true;
	}
}
