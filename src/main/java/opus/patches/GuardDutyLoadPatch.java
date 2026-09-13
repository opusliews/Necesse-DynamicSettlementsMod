package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opus.guard.GuardDuty;
import opus.guard.GuardDutySystem;

@ModMethodPatch(target = HumanMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class GuardDutyLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob mob, @Advice.Argument(0) LoadData save) {
		if (mob instanceof GuardHumanMob) {
			boolean nightDuty = save.getBoolean("settlementBuildersNightGuardDuty", false, false);
			GuardDutySystem.setDuty((GuardHumanMob)mob, nightDuty ? GuardDuty.NIGHT : GuardDuty.DAY);
		}
	}
}
