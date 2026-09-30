package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.DynamicRaiderAI;

@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "updateAIAndLook", arguments = {})
public class RaiderAIReplacementPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ItemAttackerRaiderMob raider) {
		if (raider.isServer() && raider.raidEventUniqueID != 0) raider.ai = DynamicRaiderAI.create(raider);
	}
}
