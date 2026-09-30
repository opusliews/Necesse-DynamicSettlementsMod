package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlerBedReservationSystem;

@ModMethodPatch(target = HumanMob.class, name = "addSaveData", arguments = {SaveData.class})
public class HumanBedReservationSavePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This HumanMob human, @Advice.Argument(0) SaveData save) {
		SettlerBedReservationSystem.addSaveData(human, save);
	}
}
