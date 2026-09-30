package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.PathDoorOption;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "getPathDoorOption", arguments = {})
public class RaiderPathDoorOptionMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This ItemAttackerRaiderMob raider, @Advice.Return(readOnly = false) PathDoorOption result) {
		result = MultiLevelRaidSystem.overridePathDoorOption(raider, result);
	}
}
