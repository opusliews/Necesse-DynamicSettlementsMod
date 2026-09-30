package opusliews.patches;

import java.util.LinkedList;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;
import opusliews.sleep.SleepWarningSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "serverTick", arguments = {})
public class SettlementRaidServerTickPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This SettlementRaidLevelEvent event,
			@Advice.FieldValue("settlementUniqueID") int settlementUniqueID,
			@Advice.FieldValue("raiders") LinkedList<ItemAttackerRaiderMob> raiders,
			@Advice.FieldValue("loadedRaiderUniqueIDs") int[] loadedRaiderUniqueIDs
	) {
		MultiLevelRaidSystem.recoverLoadedRaiders(event, raiders, loadedRaiderUniqueIDs);
		MultiLevelRaidSystem.ensureActiveCrossLevelRaiders(event, raiders);
		SleepWarningSystem.beginRaidPreventSleepScope();
		SleepWarningSystem.onRaidTick(event.getLevel(), settlementUniqueID);
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void onExit() {
		SleepWarningSystem.endRaidPreventSleepScope();
	}
}
