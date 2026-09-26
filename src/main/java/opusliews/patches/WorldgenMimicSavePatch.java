package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.hostile.MimicMob;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = MimicMob.class, name = "addSaveData", arguments = {SaveData.class})
public class WorldgenMimicSavePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This MimicMob mimic, @Advice.Argument(0) SaveData save) {
		WorldgenSpecialLootGatingSystem.addMimicSaveData(mimic, save);
	}
}
