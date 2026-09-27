package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLadderSystem;

@ModMethodPatch(target = LevelObject.class, name = "interact", arguments = {PlayerMob.class})
public class SettlementLadderInteractPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This LevelObject levelObject, @Advice.Argument(0) PlayerMob player) {
		return SettlementLadderSystem.handlePlayerInteract(levelObject, player);
	}
}
