package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(target = LevelObject.class, name = "interact", arguments = {PlayerMob.class})
public class WorldgenLockedContainerInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This LevelObject levelObject, @Advice.Argument(0) PlayerMob player) {
		return WorldgenLockedContainerSystem.handleInteract(levelObject, player);
	}
}
