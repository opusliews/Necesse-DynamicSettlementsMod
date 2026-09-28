package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

/** Returns the storage index for the settler's actual settlement level. */
@ModMethodPatch(target = PickupSettlementStorageActiveJob.class, name = "getStorageRecords", arguments = {EntityJobWorker.class})
public class PickupSettlementStorageRecordsMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.Argument(0) EntityJobWorker worker, @Advice.Return(readOnly = false) SettlementStorageRecords result) {
		if (worker == null) return;
		Mob mob = worker.getMobWorker();
		if (!(mob instanceof SettlerMob) || worker.getLevel() == null) return;
		ServerSettlementData settlement = ((SettlerMob)mob).getSettlerSettlementServerData();
		if (settlement == null || settlement.getLevel() == null) return;
		if (worker.getLevel().getIdentifier().equals(settlement.getLevel().getIdentifier())) return;
		SettlementStorageRecords levelRecords = SettlementLevelStorageManager.getStorageRecords(settlement, worker.getLevel().getIdentifier());
		if (levelRecords != null) result = levelRecords;
	}
}
