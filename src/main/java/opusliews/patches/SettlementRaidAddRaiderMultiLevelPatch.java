package opusliews.patches;

import java.util.LinkedList;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "addRaider", arguments = {ItemAttackerRaiderMob.class, int.class, int.class})
public class SettlementRaidAddRaiderMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementRaidLevelEvent event,
			@Advice.Argument(0) ItemAttackerRaiderMob raider,
			@Advice.FieldValue("raiders") LinkedList<ItemAttackerRaiderMob> raiders) {
		MultiLevelRaidSystem.onRaiderAdded(event, raider, raiders);
	}
}
