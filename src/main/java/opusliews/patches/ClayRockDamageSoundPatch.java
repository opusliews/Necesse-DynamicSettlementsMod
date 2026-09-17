package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.ObjectRegistry;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(
		target = GameObject.class,
		name = "playDamageSound",
		arguments = {Level.class, int.class, int.class, boolean.class}
)
public class ClayRockDamageSoundPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This GameObject object,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) boolean damageDone
	) {
		if (object.getID() != ObjectRegistry.getObjectID("clayrock")) {
			return false;
		}

		return true;
	}
}