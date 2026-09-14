package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.SleepWarningSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "clientTick", arguments = {})
public class SettlementRaidClientTickPatch {
	@Advice.OnMethodEnter
	public static void onEnter() {
		SleepWarningSystem.beginRaidPreventSleepScope();
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit() {
		SleepWarningSystem.endRaidPreventSleepScope();
	}
}
