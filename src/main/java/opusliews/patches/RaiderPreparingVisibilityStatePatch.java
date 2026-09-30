package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.levelEvent.settlementRaidEvent.SettlementRaidLevelEvent;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.RaidPreparingVisibilitySystem;

public class RaiderPreparingVisibilityStatePatch {
	@ModMethodPatch(target = SettlementRaidLevelEvent.class, name = "addRaider", arguments = {ItemAttackerRaiderMob.class, int.class, int.class})
	public static class AddRaiderPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.Argument(0) ItemAttackerRaiderMob raider) {
			RaidPreparingVisibilitySystem.initializeRaider(raider);
		}
	}

	@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "serverTick", arguments = {})
	public static class ServerTickPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This ItemAttackerRaiderMob raider) {
			RaidPreparingVisibilitySystem.serverTick(raider);
		}
	}
}
