package opusliews.patches;

import necesse.engine.Settings;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.ui.ControllerInteractTarget;
import necesse.gfx.ui.HUD;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.object.TrapdoorObject;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(
		target = PlayerMob.class,
		name = "getControllerInteractTarget",
		arguments = {boolean.class, int.class, GameCamera.class}
)
public class TrapdoorPlayerControllerInteractPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PlayerMob player,
			@Advice.Argument(0) boolean onlyItemInteract,
			@Advice.Argument(2) GameCamera camera,
			@Advice.Return(readOnly = false) ControllerInteractTarget result
	) {
		if (!TrapdoorSystem.isTrapdoorHidden(player)) return;
		result = getTrapdoorTarget(player, onlyItemInteract, camera);
	}

	public static ControllerInteractTarget getTrapdoorTarget(PlayerMob player, boolean onlyItemInteract, GameCamera camera) {
		if (onlyItemInteract || player == null || player.getLevel() == null) return null;

		int tileX = player.getTileX();
		int tileY = player.getTileY();
		if (!(player.getLevel().getObject(tileX, tileY) instanceof TrapdoorObject)) return null;

		LevelObject trapdoor = player.getLevel().getLevelObject(tileX, tileY);
		if (!trapdoor.isInInteractRange(player) || !trapdoor.canInteract(player)) return null;

		return new ControllerInteractTarget() {
			@Override
			public void runInteract() {
				TrapdoorPlayerInteractPatch.interactWithTrapdoor(
						player,
						trapdoor,
						tileX,
						tileY,
						tileX * 32 + 16,
						tileY * 32 + 16
				);
			}

			@Override
			public DrawOptions getDrawOptions() {
				return HUD.tileBoundOptions(
						camera,
						Settings.UI.controllerFocusBoundsColor,
						true,
						trapdoor.getMultiTile().getTileRectangle(tileX, tileY)
				);
			}

			@Override
			public void onCurrentlyFocused() {
			}
		};
	}
}
