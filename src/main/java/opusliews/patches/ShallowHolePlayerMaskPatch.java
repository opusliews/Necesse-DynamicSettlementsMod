package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.MaskShaderOptions;
import necesse.entity.mobs.Mob;
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
		Level level = mob.getLevel();
		if (level == null) {
			return;
		}

		if (level.getTile(mob.getTileX(), mob.getTileY()) instanceof ShallowHoleTile) {
			ShallowHoleTile holeTile = (ShallowHoleTile)level.getTile(mob.getTileX(), mob.getTileY());

			if (holeTile.isMobInSinkingArea(mob)) {
				result = holeTile.getMobMaskOptions(mob);
			}
		}
	}
}
