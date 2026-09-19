package opusliews.patches;

import java.util.List;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.deephole.DeepHoleSystem;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "addDrawables",
		arguments = {
				List.class,
				OrderableDrawables.class,
				OrderableDrawables.class,
				Level.class,
				int.class,
				int.class,
				TickManager.class,
				GameCamera.class,
				PlayerMob.class
		}
)
public class DeepHolePlayerDrawPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This PlayerMob player,
			@Advice.Argument(value = 5, readOnly = false) int drawY
	) {
		if (!DeepHoleSystem.hasDrawYOffset(player) || DeepHoleSystem.isFullyHidden(player)) return;
		drawY += DeepHoleSystem.getDrawYOffset(player);
	}
}
