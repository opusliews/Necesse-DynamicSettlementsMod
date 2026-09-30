package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "getRaidEvent", arguments = {})
public class RaiderGetRaidEventMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This ItemAttackerRaiderMob raider, @Advice.Return(readOnly = false) SettlementRaidLevelEvent result) { result = MultiLevelRaidSystem.resolveRaidEvent(raider, result); }
}
