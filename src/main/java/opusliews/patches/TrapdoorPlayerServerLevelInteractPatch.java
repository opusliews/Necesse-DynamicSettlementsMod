package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.packet.PacketPlayerItemInteract;
import necesse.entity.mobs.PlayerMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;
import opusliews.deephole.DeepHoleSystem;

@ModMethodPatch(target = PlayerMob.class, name = "runServerItemLevelInteract", arguments = {PacketPlayerItemInteract.class})
public class TrapdoorPlayerServerLevelInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This PlayerMob player) {
		return TrapdoorSystem.isHidden(player) || DeepHoleSystem.isDigging(player);
	}
}
