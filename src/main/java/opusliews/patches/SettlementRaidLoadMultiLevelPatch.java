package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "applyLoadData", arguments = {LoadData.class})
public class SettlementRaidLoadMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This SettlementRaidLevelEvent event, @Advice.Argument(0) LoadData save) { MultiLevelRaidSystem.applyRaidLoad(event, save); }
}
