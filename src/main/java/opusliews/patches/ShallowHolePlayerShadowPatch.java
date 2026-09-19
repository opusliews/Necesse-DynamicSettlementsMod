package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.OrderableDrawables;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.light.GameLight;
import net.bytebuddy.asm.Advice;
import opusliews.tile.ShallowHoleTile;

@ModMethodPatch(
		target = Mob.class,
		name = "addShadowDrawables",
		arguments = {
				OrderableDrawables.class,
				Level.class,
				int.class,
				int.class,
				GameLight.class,
				GameCamera.class
		}
)
public class ShallowHolePlayerShadowPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This Mob mob,
			@Advice.Argument(1) Level level,
			@Advice.Argument(2) int x,
			@Advice.Argument(3) int y
	) {
		if (level == null) {
			return false;
		}

		int tileX = mob.getTileX();
		int tileY = mob.getTileY();

		GameTile tile = level.getTile(tileX, tileY);

		if (tile instanceof ShallowHoleTile) {
			return ((ShallowHoleTile)tile).shouldMobSinkVisually(mob);
		}

		return false;
	}
}
