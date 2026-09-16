package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.MaskShaderOptions;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.tile.ShallowHoleTile;

@ModMethodPatch(target = Mob.class, name = "getSwimMaskShaderOptions", arguments = {float.class})
public class ShallowHolePlayerMaskPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This Mob mob,
			@Advice.Return(readOnly = false) MaskShaderOptions result
	) {
		if (!(mob instanceof PlayerMob)) {
			return;
		}

		PlayerMob player = (PlayerMob)mob;
		Level level = player.getLevel();
		if (level == null) {
			return;
		}

		if (level.getTile(player.getTileX(), player.getTileY()) instanceof ShallowHoleTile) {
			ShallowHoleTile holeTile = (ShallowHoleTile)level.getTile(player.getTileX(), player.getTileY());

			if (holeTile.isPlayerInSinkingArea(player)) {
				result = holeTile.getPlayerMaskOptions(player);
			}
		}
	}
}
