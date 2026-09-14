package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import net.bytebuddy.asm.Advice;
import opusliews.sleep.SleepWarningSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "startRaid", arguments = {boolean.class})
public class SettlementRaidStartPatch {
	@Advice.OnMethodEnter
	public static boolean onEnter(@Advice.FieldValue("started") boolean started) {
		return started;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementRaidLevelEvent event,
			@Advice.Enter boolean wasStarted,
			@Advice.FieldValue("started") boolean started,
			@Advice.FieldValue("settlementUniqueID") int settlementUniqueID
	) {
		if (!wasStarted && started) {
			SleepWarningSystem.onRaidStarted(event.getLevel(), settlementUniqueID);
		}
	}
}
