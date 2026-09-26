package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.objectEntity.TrialEntranceObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = TrialEntranceObjectEntity.class, name = "applyLoadData", arguments = {LoadData.class})
public class WorldgenTrialEntranceLoadTierPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This TrialEntranceObjectEntity entity) {
		WorldgenSpecialLootGatingSystem.refreshTrialEntrance(entity);
	}
}
