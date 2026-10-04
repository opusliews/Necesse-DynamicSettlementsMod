package opusliews.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.MainGameFormManager;
import net.bytebuddy.asm.Advice;
import opusliews.earlygame.FirstNightDeathSkipSystem;

@ModMethodPatch(
		target = MainGameFormManager.class,
		name = "draw",
		arguments = {TickManager.class, PlayerMob.class}
)
public class MainGameFirstNightDeathFadePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This MainGameFormManager manager,
			@Advice.Argument(0) TickManager tickManager,
			@Advice.Argument(1) PlayerMob perspective
	) {
		return FirstNightDeathSkipSystem.drawDeathBlackout(manager, tickManager, perspective);
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.Enter boolean skipped) {
		if (!skipped) {
			FirstNightDeathSkipSystem.drawRespawnFade();
		}
	}
}
