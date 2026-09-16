package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameMath;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawables.OrderableDrawables;
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
		if (!(mob instanceof PlayerMob) || level == null) {
			return false;
		}

		int tileX = GameMath.getTileCoordinate(x);
		int tileY = GameMath.getTileCoordinate(y);
		return level.getTile(tileX, tileY) instanceof ShallowHoleTile;
	}
}
