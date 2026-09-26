package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.hostile.MimicMob;
import necesse.gfx.camera.GameCamera;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = MimicMob.class, name = "onMouseHover", arguments = {GameCamera.class, PlayerMob.class, GameBlackboard.class, boolean.class})
public class WorldgenMimicHoverPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This MimicMob mimic) {
		WorldgenSpecialLootGatingSystem.addMimicHoverTooltip(mimic);
	}
}
