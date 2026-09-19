package opusliews.tile;

import java.awt.Color;
import necesse.entity.mobs.Mob;

public class FiringPitLogTile extends ShallowHoleTile {
	public static final String stringID = "firingpitlogtile";

	public FiringPitLogTile() {
		super();
		mapColor = new Color(92, 82, 72);
	}

	@Override
	public boolean isMobInSinkingArea(Mob mob) {
		return false;
	}

	@Override
	protected String getHoleTexturePath() {
		return "tiles/firingpitlog";
	}
}
