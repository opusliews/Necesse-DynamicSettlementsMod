package opusliews.object;

import necesse.level.maps.Level;

public class WorkstationObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamicworkstation";

	public WorkstationObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "workstation";
	}
}
