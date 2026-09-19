package opusliews.tile;

import java.awt.Color;
import necesse.engine.registries.ObjectRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import opusliews.deephole.DeepHoleSystem;
import opusliews.object.HoleCaveLadderObject;

public class DeepHoleTile extends ShallowHoleTile {
	public static final String stringID = "deepholetile";

	public DeepHoleTile() {
		super();
		mapColor = new Color(60, 45, 39);
	}

	@Override
	public boolean shouldMobSinkVisually(Mob mob) {
		Level level = mob.getLevel();
		if (level == null || !isMobInSinkingArea(mob)) return false;

		boolean hasLadder = level.getObjectID(mob.getTileX(), mob.getTileY())
				== ObjectRegistry.getObjectID(HoleCaveLadderObject.stringID);
		if (!hasLadder) return true;

		return mob instanceof PlayerMob && DeepHoleSystem.isTransitioning((PlayerMob)mob);
	}
}
