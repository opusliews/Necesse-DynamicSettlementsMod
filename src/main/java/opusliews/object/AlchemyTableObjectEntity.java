package opusliews.object;

import necesse.level.maps.Level;

public class AlchemyTableObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamicalchemytable";

	public AlchemyTableObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "alchemy";
	}
}
