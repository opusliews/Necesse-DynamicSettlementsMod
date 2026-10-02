package opusliews.patches;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.GameObjectReservable;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.SingleJobSequence;
import necesse.entity.mobs.job.activeJob.DropOffSettlementStorageActiveJob;
import necesse.inventory.InventoryItem;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import net.bytebuddy.asm.Advice;
import opusliews.clay.ClayPackageSystem;
import opusliews.logging.Logging;

@ModMethodPatch(
		target = HasStorageLevelJob.class,
		name = "getJobSequence",
		arguments = {EntityJobWorker.class, FoundJob.class}
)
public class ClayPackageHasStoragePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) EntityJobWorker worker,
			@Advice.Argument(1) FoundJob foundJob,
			@Advice.Local("clayPackageStorageResult") JobSequence clayPackageStorageResult
	) {
		if (worker == null || foundJob == null || !(foundJob.job instanceof HasStorageLevelJob)) return false;
		HasStorageLevelJob storageJob = (HasStorageLevelJob)foundJob.job;

		boolean foundPackage = false;
		for (Object value : worker.getWorkInventory().items()) {
			if (!(value instanceof InventoryItem)) continue;
			InventoryItem clayPackage = (InventoryItem)value;
			if (!ClayPackageSystem.isPackage(clayPackage)) continue;
			foundPackage = true;

			for (InventoryItem contained : ClayPackageSystem.getContents(clayPackage)) {
				int addAmount = storageJob.settlementInventory.canAddFutureDropOff(contained);
				if (addAmount <= 0) continue;

				InventoryItem addItem = contained.copy(Math.min(contained.getAmount(), addAmount));
				if (Logging.logEnabled) Logging.logMessage("[ClayPackage] HasStorageLevelJob treating package content as carried item="
						+ contained.item.getStringID() + " amount=" + addItem.getAmount()
						+ " worker=" + worker.getMobWorker().getUniqueID());

				clayPackageStorageResult = new SingleJobSequence(
						new DropOffSettlementStorageActiveJob(
								worker,
								foundJob.priority,
								storageJob.settlementInventory,
								(GameObjectReservable)null,
								false,
								() -> addItem
						),
						new LocalMessage("activities", "droppingoffinv")
				).withPerformedLevelJob(foundJob.job);
				return true;
			}
		}

		if (foundPackage && Logging.logEnabled) Logging.logMessage("[ClayPackage] HasStorageLevelJob found package contents but no valid drop-off capacity"
				+ " storage=" + storageJob.tileX + "," + storageJob.tileY
				+ " worker=" + worker.getMobWorker().getUniqueID());
		return false;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean handled,
			@Advice.Local("clayPackageStorageResult") JobSequence clayPackageStorageResult,
			@Advice.Return(readOnly = false) JobSequence result
	) {
		if (handled) result = clayPackageStorageResult;
	}
}
