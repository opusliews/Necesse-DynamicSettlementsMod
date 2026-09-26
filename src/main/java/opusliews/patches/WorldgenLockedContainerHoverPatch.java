package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(target = GameObject.class, name = "onMouseHover", arguments = {Level.class, int.class, int.class, GameCamera.class, PlayerMob.class, boolean.class})
public class WorldgenLockedContainerHoverPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY
	) {
		WorldgenLockedContainerSystem.addHoverTooltip(level, 0, tileX, tileY);
	}
}
