package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.objectEntity.TrialEntranceObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

import java.util.List;

@ModMethodPatch(target = TrialEntranceObjectEntity.class, name = "addLootList", arguments = {List.class})
public class WorldgenTrialEntranceLootTierPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This TrialEntranceObjectEntity entity) {
		WorldgenSpecialLootGatingSystem.refreshTrialEntrance(entity);
	}
}
