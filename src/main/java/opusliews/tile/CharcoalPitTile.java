package opusliews.tile;

import necesse.entity.mobs.Mob;

import java.awt.Color;

public class CharcoalPitTile extends ShallowHoleTile {
	public static final String stringID = "charcoalpittile";

	public CharcoalPitTile() {
		super();
		mapColor = new Color(73, 55, 45);
	}

	@Override
	public boolean isMobInSinkingArea(Mob mob) {
		return false;
	}

	@Override
	protected String getHoleTexturePath() {
		return "tiles/charcoalpit";
	}
}
