package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.RaiderMobPhase;
import necesse.entity.mobs.ai.behaviourTree.util.AIMover;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = AIMover.class, name = "tick", arguments = {Mob.class})
public class RaiderPreparingMovementPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(0) Mob mob, @Advice.Local("result") boolean result) {
		if (!(mob instanceof ItemAttackerRaiderMob) || ((ItemAttackerRaiderMob)mob).phase != RaiderMobPhase.PREPARING) return false;
		mob.stopMoving();
		result = false;
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean skipped, @Advice.Local("result") boolean localResult, @Advice.Return(readOnly = false) boolean result) {
		if (skipped) result = localResult;
	}
}
