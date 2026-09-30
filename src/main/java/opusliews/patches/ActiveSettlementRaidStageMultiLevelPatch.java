package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.ActiveSettlementRaidStage;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = ActiveSettlementRaidStage.class, name = "serverTick", arguments = {})
public class ActiveSettlementRaidStageMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ActiveSettlementRaidStage stage) {
		stage.shouldStopBuffer = MultiLevelRaidSystem.overrideActiveRaidStopBuffer(stage.event, stage.shouldStopBuffer);
	}
}
