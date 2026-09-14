package opusliews.patches;

import java.awt.Rectangle;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.chat.FormChatLog;
import necesse.inventory.container.BedContainer;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = FormContentBox.class,
		name = "draw",
		arguments = {TickManager.class, PlayerMob.class, Rectangle.class}
)
public class SleepChatHudPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This FormContentBox box,
			@Advice.Argument(1) PlayerMob perspective
	) {
		if (!(box instanceof FormChatLog) || perspective == null || !perspective.isClientClient()) {
			return false;
		}

		return perspective.getClientClient().getClient().getContainer() instanceof BedContainer;
	}
}
