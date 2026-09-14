package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.SleepWarningSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "serverTick", arguments = {})
public class SettlementRaidServerTickPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This SettlementRaidLevelEvent event,
			@Advice.FieldValue("settlementUniqueID") int settlementUniqueID
	) {
		SleepWarningSystem.beginRaidPreventSleepScope();
		SleepWarningSystem.onRaidTick(event.getLevel(), settlementUniqueID);
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit() {
		SleepWarningSystem.endRaidPreventSleepScope();
	}
}
