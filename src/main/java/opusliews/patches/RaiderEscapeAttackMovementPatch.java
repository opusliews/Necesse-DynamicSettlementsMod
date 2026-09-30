package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = ItemAttackerMob.class, name = "getAttackingMovementModifier", arguments = {})
public class RaiderEscapeAttackMovementPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ItemAttackerMob mob, @Advice.Return(readOnly = false) float result) {
		if (mob instanceof ItemAttackerRaiderMob && MultiLevelRaidSystem.isEscapeRetaliationAttack((ItemAttackerRaiderMob)mob)) result = 1.0F;
	}
}
