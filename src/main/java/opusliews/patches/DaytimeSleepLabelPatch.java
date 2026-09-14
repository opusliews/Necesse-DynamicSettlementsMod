package opusliews.patches;

import java.awt.Rectangle;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.presets.containerComponent.SleepContainerForm;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.DaytimeSleepLabelSystem;

@ModMethodPatch(
		target = SleepContainerForm.class,
		name = "draw",
		arguments = {TickManager.class, PlayerMob.class, Rectangle.class}
)
public class DaytimeSleepLabelPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This SleepContainerForm form,
			@Advice.Argument(1) PlayerMob perspective
	) {
		DaytimeSleepLabelSystem.beginDraw(form, perspective);
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit() {
		DaytimeSleepLabelSystem.endDraw();
	}
}
