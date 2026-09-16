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
		name = "canInteract",
		arguments = {Level.class, int.class, int.class, PlayerMob.class}
)
public class SmallRockInteractPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This GameObject object,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (object instanceof SingleRockSmall) {
			result = EarlyGameStoneSystem.isHarvestableSmallRock(object, level, tileX, tileY);
		}
	}
}