package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.RaidPreparingProtectionSystem;

public class RaiderPreparingProtectionStatePatch {
	@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "addRaider", arguments = {ItemAttackerRaiderMob.class, int.class, int.class})
	public static class AddRaiderPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.Argument(0) ItemAttackerRaiderMob raider) {
			RaidPreparingProtectionSystem.initializeRaider(raider);
		}
	}

	@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "serverTick", arguments = {})
	public static class ServerTickPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This ItemAttackerRaiderMob raider) {
			RaidPreparingProtectionSystem.serverTick(raider);
		}
	}
}
