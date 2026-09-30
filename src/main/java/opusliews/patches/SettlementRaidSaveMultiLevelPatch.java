package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "addSaveData", arguments = {SaveData.class})
public class SettlementRaidSaveMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This SettlementRaidLevelEvent event, @Advice.Argument(0) SaveData save) { MultiLevelRaidSystem.addRaidSave(event, save); }
}
