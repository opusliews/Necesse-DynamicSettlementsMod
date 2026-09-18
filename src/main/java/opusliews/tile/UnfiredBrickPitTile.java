package opusliews.tile;

import necesse.entity.mobs.Mob;

import java.awt.Color;

public class UnfiredBrickPitTile extends ShallowHoleTile {
	public static final String stringID = "unfiredbrickpittile";

	public UnfiredBrickPitTile() {
		super();
		mapColor = new Color(92, 82, 72);
	}

	@Override
	public boolean isMobInSinkingArea(Mob mob) {
		return false;
	}

	@Override
	protected String getHoleTexturePath() {
		return "tiles/unfiredbrickpit";
	}
}
