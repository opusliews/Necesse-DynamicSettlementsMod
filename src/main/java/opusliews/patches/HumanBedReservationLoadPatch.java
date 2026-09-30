package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlerBedReservationSystem;

@ModMethodPatch(target = HumanMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class HumanBedReservationLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Argument(0) LoadData save) {
		SettlerBedReservationSystem.applyLoadData(human, save);
	}
}
