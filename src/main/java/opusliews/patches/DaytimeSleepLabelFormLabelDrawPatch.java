package opusliews.patches;

import java.awt.Rectangle;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormLabel;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.DaytimeSleepLabelSystem;

@ModMethodPatch(
		target = FormLabel.class,
		name = "draw",
		arguments = {TickManager.class, PlayerMob.class, Rectangle.class}
)
public class DaytimeSleepLabelFormLabelDrawPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This FormLabel label) {
		DaytimeSleepLabelSystem.beforeLabelDraw(label);
	}
}
