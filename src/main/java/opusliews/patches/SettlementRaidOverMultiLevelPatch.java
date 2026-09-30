package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "over", arguments = {})
public class SettlementRaidOverMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This SettlementRaidLevelEvent event) { MultiLevelRaidSystem.onRaidOver(event); }
}
