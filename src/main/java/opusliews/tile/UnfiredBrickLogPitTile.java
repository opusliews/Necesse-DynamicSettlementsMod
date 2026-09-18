package opusliews.tile;

import necesse.entity.mobs.Mob;

import java.awt.Color;

public class UnfiredBrickLogPitTile extends ShallowHoleTile {
	public static final String stringID = "unfiredbricklogpittile";

	public UnfiredBrickLogPitTile() {
		super();
		mapColor = new Color(92, 82, 72);
	}

	@Override
	public boolean isMobInSinkingArea(Mob mob) {
		return false;
	}

	@Override
	protected String getHoleTexturePath() {
		return "tiles/unfiredbricklogpit";
	}
}
