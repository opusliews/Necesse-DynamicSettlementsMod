package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import necesse.level.maps.levelData.settlementData.SettlementWorkstationRecipe;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = SettlementWorkstationRecipe.class, name = "onCrafted", arguments = {SettlementWorkstation.class, int.class})
public class SettlementWorkstationRecipePersistencePatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) SettlementWorkstation workstation) {
		if (workstation != null) SettlementLevelStorageManager.persistWorkstationRecipes(workstation.data, workstation);
	}
}
