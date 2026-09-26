package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = Mob.class, name = "interact", arguments = {PlayerMob.class})
public class WorldgenMimicInteractPatch {
	@Advice.OnMethodEnter
	public static void onEnter(@Advice.This Mob mob, @Advice.Argument(0) PlayerMob player) {
		WorldgenSpecialLootGatingSystem.handleMimicInteract(mob, player);
	}
}
