package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SingleRockSmall;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.EarlyGameStoneSystem;

@ModMethodPatch(
		target = GameObject.class,
		name = "interact",
		arguments = {Level.class, int.class, int.class, PlayerMob.class}
)
public class SmallRockHarvestPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This GameObject object,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) PlayerMob player
	) {
		if (!(object instanceof SingleRockSmall)) {
			return false;
		}

		EarlyGameStoneSystem.harvestSmallRock(object, level, tileX, tileY, player);
		return true;
	}
}
