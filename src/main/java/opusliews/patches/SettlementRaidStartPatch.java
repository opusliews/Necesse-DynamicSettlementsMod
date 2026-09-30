package opusliews.patches;

import java.util.LinkedList;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;
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
			@Advice.FieldValue("settlementUniqueID") int settlementUniqueID,
			@Advice.FieldValue("raiders") LinkedList<ItemAttackerRaiderMob> raiders
	) {
		if (!wasStarted && started) {
			MultiLevelRaidSystem.onRaidStarted(event, raiders);
			SleepWarningSystem.onRaidStarted(event.getLevel(), settlementUniqueID);
		}
	}
}
