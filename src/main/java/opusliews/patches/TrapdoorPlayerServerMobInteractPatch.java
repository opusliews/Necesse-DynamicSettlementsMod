package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketPlayerItemMobInteract;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(target = PlayerMob.class, name = "runServerItemMobInteract", arguments = {PacketPlayerItemMobInteract.class, Mob.class})
public class TrapdoorPlayerServerMobInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This PlayerMob player) {
		return TrapdoorSystem.isHidden(player);
	}
}
