package opusliews.jobs;

import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.levelData.jobs.HaulFromLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementCrossLevelRoute;
import opusliews.multilevelsettlement.SettlementCrossLevelRouting;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelPosition;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementLevelZoneSystem;
import opusliews.multilevelsettlement.SettlementLadderSystem;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.stock.SettlementStockSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bridge for ordinary settlement-storage hauling between settlement levels.
 *
 * Vanilla still owns same-level hauling. This system only generates the missing
 * cross-level source -> destination HaulFromLevelJobs. The actual sequence keeps
 * vanilla pickup/drop-off reservations and inserts one designated-ladder transition
 * between the pickup and drop-off phases.
 */
public final class CrossLevelHaulingSystem {
	private static final Map<ServerSettlementData, List<HaulFromLevelJob>> generatedJobs = Collections.synchronizedMap(new WeakHashMap<>());

	private CrossLevelHaulingSystem() {
	}

	public static void refresh(ServerSettlementData settlement) {
		if (settlement == null || settlement.getLevel() == null || !settlement.getLevel().isServer()) return;
		removeGenerated(settlement);

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || SettlementLadderSystem.getValidLinks(domain, false).isEmpty()) return;

		ArrayList<SettlementInventory> storages = new ArrayList<>();
		for (SettlementInventory storage : SettlementLevelStorageManager.getStorage(settlement)) {
			if (storage == null || storage.level == null || !storage.isStorageValid() || storage.isAllAdjacentSolid()) continue;
			InventoryRange range = storage.getInventoryRange();
			if (range == null) continue;
			storages.add(storage);
		}
		if (storages.size() < 2) return;

		ArrayList<HaulFromLevelJob> added = new ArrayList<>();
		for (SettlementInventory source : storages) {
			InventoryRange sourceRange = source.getInventoryRange();
			if (sourceRange == null) continue;

			ArrayList<HaulFromLevelJob> sourceJobs = new ArrayList<>();
			for (int slot = sourceRange.startSlot; slot <= sourceRange.endSlot; slot++) {
				InventoryItem item = sourceRange.inventory.getItem(slot);
				if (item == null || item.getAmount() <= 0) continue;

				int sourceRemoveAmount = Math.max(0, source.getFilter().getRemoveAmount(source.level, item, sourceRange));
				int protectedRemoveAmount = SettlementStockSystem.getMaxRemovable(source, item);
				if (protectedRemoveAmount <= 0) {
					if (Logging.logEnabled) Logging.logMessage("[CrossLevelHaulingDebug] Skipped protected source item source="
							+ source.level.getIdentifier() + "@" + source.tileX + "," + source.tileY
							+ " item=" + item.item.getStringID()
							+ " amount=" + item.getAmount());
					continue;
				}
				HaulFromLevelJob job = null;

				for (SettlementInventory destination : storages) {
					if (destination == source || destination.level == source.level) continue;
					if (destination.level.getIdentifier().equals(source.level.getIdentifier())) continue;

					boolean higherPriority = destination.priority > source.priority;
					if (!higherPriority && sourceRemoveAmount <= 0) continue;

					InventoryRange destinationRange = destination.getInventoryRange();
					if (destinationRange == null) continue;
					int filterAmount = destination.getFilter().getAddAmount(destination.level, item, destinationRange, false);
					if (filterAmount <= 0) continue;
					int inventoryAmount = destinationRange.inventory.canAddItem(
							destination.level,
							(PlayerMob)null,
							item,
							destinationRange.startSlot,
							destinationRange.endSlot,
							"crosslevelhaul"
					);
					int futureAmount = destination.canAddFutureDropOff(item);
					int addAmount = Math.min(filterAmount, Math.min(inventoryAmount, futureAmount));
					if (addAmount <= 0) continue;

					int transferable = higherPriority
							? Math.min(item.getAmount(), protectedRemoveAmount)
							: Math.min(item.getAmount(), Math.min(sourceRemoveAmount, protectedRemoveAmount));
					addAmount = Math.min(addAmount, transferable);
					if (addAmount <= 0) continue;

					if (job == null) {
						job = findMatchingJob(sourceJobs, source, item);
						if (job == null) {
							job = new HaulFromLevelJob(source, item.copy(transferable));
							sourceJobs.add(job);
						}
					}
					job.dropOffPositions.add(new HaulFromLevelJob.HaulPosition(destination, destination.priority, addAmount));
				}
			}

			for (HaulFromLevelJob job : sourceJobs) {
				if (job.dropOffPositions.isEmpty()) continue;
				HaulFromLevelJob inserted = (HaulFromLevelJob)source.level.jobsLayer.addJob(job, false, true);
				if (inserted != null) {
					added.add(inserted);
					if (Logging.logEnabled) {
						StringBuilder destinations = new StringBuilder();
						for (Object value : inserted.dropOffPositions) {
							HaulFromLevelJob.HaulPosition pos = (HaulFromLevelJob.HaulPosition)value;
							if (destinations.length() > 0) destinations.append(";");
							destinations.append(pos.storage.level.getIdentifier()).append("@").append(pos.storage.tileX).append(",").append(pos.storage.tileY)
									.append(" priority=").append(pos.priority).append(" amount=").append(pos.amount);
						}
						Logging.logMessage("[CrossLevelHaulingDebug] Generated job source=" + source.level.getIdentifier() + "@" + source.tileX + "," + source.tileY
								+ " sourcePriority=" + source.priority
								+ " item=" + inserted.item.item.getStringID()
								+ " amount=" + inserted.item.getAmount()
								+ " destinations=[" + destinations + "]");
					}
				}
			}
		}

		if (!added.isEmpty()) {
			generatedJobs.put(settlement, added);
			if (Logging.logEnabled) Logging.logMessage("[CrossLevelHauling] Generated " + added.size() + " cross-level hauling jobs settlement=" + settlement.uniqueID);
		}
	}

	public static SettlementCrossLevelRoute findRoute(EntityJobWorker worker, SettlementInventory source, SettlementInventory destination) {
		if (worker == null || source == null || destination == null) return null;
		if (!(worker.getMobWorker() instanceof HumanMob)) return null;
		HumanMob human = (HumanMob)worker.getMobWorker();
		if (human.getLevel() == null || source.level == null || destination.level == null) return null;
		if (!human.getLevel().getIdentifier().equals(source.level.getIdentifier())) return null;
		if (source.level.getIdentifier().equals(destination.level.getIdentifier())) return SettlementCrossLevelRoute.sameLevel(
				new SettlementLevelPosition(source.level.getIdentifier(), source.tileX, source.tileY),
				new SettlementLevelPosition(destination.level.getIdentifier(), destination.tileX, destination.tileY),
				0
		);

		ServerSettlementData settlement = human.getSettlerSettlementServerData();
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return null;
		SettlementLevelPosition target = new SettlementLevelPosition(destination.level.getIdentifier(), destination.tileX, destination.tileY);
		return SettlementCrossLevelRouting.findBestRouteQuiet(human, domain, target, true, SettlementLevelZoneSystem.getRouteRestriction(human));
	}

	public static boolean isCrossLevel(SettlementInventory source, SettlementInventory destination) {
		return source != null && destination != null && source.level != null && destination.level != null
				&& !source.level.getIdentifier().equals(destination.level.getIdentifier());
	}

	private static HaulFromLevelJob findMatchingJob(List<HaulFromLevelJob> jobs, SettlementInventory source, InventoryItem item) {
		for (HaulFromLevelJob job : jobs) {
			if (job.storage != source || job.item == null) continue;
			if (job.item.equals(source.level, item, true, false, "crosslevelhaul")) return job;
		}
		return null;
	}

	private static void removeGenerated(ServerSettlementData settlement) {
		List<HaulFromLevelJob> jobs = generatedJobs.remove(settlement);
		if (jobs == null) return;
		for (HaulFromLevelJob job : jobs) {
			if (job != null && !job.isRemoved()) job.remove();
		}
	}
}
