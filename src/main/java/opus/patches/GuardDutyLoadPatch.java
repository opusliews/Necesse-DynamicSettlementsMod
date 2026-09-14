package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDuty;
import opus.guard.GuardDutySystem;
import opus.guard.GuardFatigueSystem;

@ModMethodPatch(target = HumanMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class GuardDutyLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) LoadData save) {
		if (mob instanceof GuardHumanMob) {
			GuardHumanMob guard = (GuardHumanMob)mob;
			boolean nightDuty = save.getBoolean("dynamicSettlementsNightGuardDuty", false, false);
			GuardDutySystem.setDuty(guard, nightDuty ? GuardDuty.NIGHT : GuardDuty.DAY);
			GuardFatigueSystem.applyLoadedState(
					guard,
					save.getInt("dynamicSettlementsGuardFatigue", 0, false),
					save.getBoolean("dynamicSettlementsGuardRestActive", false, false),
					save.getBoolean("dynamicSettlementsGuardRestInterrupted", false, false)
			);
		}
	}
}
