package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.world.OneWorldMigration;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.settlement.SettlementPlayerBedSystem;

public class SettlementPlayerBedPersistencePatch {
	@ModMethodPatch(target = ServerSettlementData.class, name = "addSaveData", arguments = {SaveData.class})
	public static class SavePatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This ServerSettlementData settlement, @Advice.Argument(0) SaveData save) {
			SettlementPlayerBedSystem.addSaveData(settlement, save);
		}
	}

	@ModMethodPatch(target = ServerSettlementData.class, name = "applyLoadData", arguments = {LoadData.class, OneWorldMigration.class, int.class, int.class})
	public static class LoadPatch {
		@Advice.OnMethodExit
		public static void onExit(@Advice.This ServerSettlementData settlement, @Advice.Argument(0) LoadData save,
				@Advice.Argument(2) int tileXOffset, @Advice.Argument(3) int tileYOffset) {
			SettlementPlayerBedSystem.applyLoadData(settlement, save, tileXOffset, tileYOffset);
		}
	}
}
