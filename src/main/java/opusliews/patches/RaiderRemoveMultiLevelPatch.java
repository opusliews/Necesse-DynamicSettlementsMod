package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = Mob.class, name = "remove", arguments = {})
public class RaiderRemoveMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This Mob mob) { if (mob instanceof ItemAttackerRaiderMob) MultiLevelRaidSystem.onRaiderRemoved((ItemAttackerRaiderMob)mob); }
}
