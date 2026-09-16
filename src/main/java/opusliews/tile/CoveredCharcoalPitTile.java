package opusliews.tile;

import java.awt.Color;
import necesse.entity.mobs.Mob;

public class CoveredCharcoalPitTile extends ShallowHoleTile {
	public static final String stringID = "coveredcharcoalpittile";

	public CoveredCharcoalPitTile() {
		super();
		mapColor = new Color(101, 76, 57);
	}

	@Override
	protected String getHoleTexturePath() {
		return "tiles/coveredcharcoalpit";
	}

	@Override
	public boolean isMobInSinkingArea(Mob mob) {
		return false;
	}
}
