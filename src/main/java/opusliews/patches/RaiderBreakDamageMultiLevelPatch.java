package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.level.maps.LevelObject;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = Mob.class, name = "getPathBreakDownDamage", arguments = {LevelObject.class})
public class RaiderBreakDamageMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This Mob mob, @Advice.Argument(0) LevelObject lo, @Advice.Return(readOnly = false) int result) {
		result = MultiLevelRaidSystem.overrideBreakDamage(mob, lo, result);
	}
}
