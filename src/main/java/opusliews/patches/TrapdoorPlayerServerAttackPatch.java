package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketPlayerAttack;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;
import opusliews.deephole.DeepHoleSystem;

@ModMethodPatch(target = PlayerMob.class, name = "runServerAttack", arguments = {PacketPlayerAttack.class})
public class TrapdoorPlayerServerAttackPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This PlayerMob player) {
		return TrapdoorSystem.isHidden(player) || DeepHoleSystem.isTransitioning(player);
	}
}
