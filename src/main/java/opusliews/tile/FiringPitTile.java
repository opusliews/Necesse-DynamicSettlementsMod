package opusliews.tile;

import java.awt.Color;
import necesse.entity.mobs.Mob;

public class FiringPitTile extends ShallowHoleTile {
	public static final int maxItems = 8;
	private final int itemCount;

	public FiringPitTile(int itemCount) {
		super();
		if (itemCount < 1 || itemCount > maxItems) {
			throw new IllegalArgumentException("itemCount must be between 1 and " + maxItems);
		}
		this.itemCount = itemCount;
		mapColor = new Color(92, 82, 72);
	}

	public int getItemCount() {
		return itemCount;
	}

	public static String getStringID(int itemCount) {
		return "firingpittile" + itemCount;
	}

	@Override
	public boolean isMobInSinkingArea(Mob mob) {
		return false;
	}

	@Override
	protected String getHoleTexturePath() {
		return "tiles/firingpit" + itemCount;
	}
}
