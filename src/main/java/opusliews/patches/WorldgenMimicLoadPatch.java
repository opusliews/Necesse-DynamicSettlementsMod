package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.hostile.MimicMob;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = MimicMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class WorldgenMimicLoadPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This MimicMob mimic, @Advice.Argument(0) LoadData save) {
		WorldgenSpecialLootGatingSystem.applyMimicLoadData(mimic, save);
	}
}
