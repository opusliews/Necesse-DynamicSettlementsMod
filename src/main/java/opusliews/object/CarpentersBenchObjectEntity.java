package opusliews.object;

import necesse.level.maps.Level;

public class CarpentersBenchObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamiccarpentersbench";

	public CarpentersBenchObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "carpenter";
	}
}
