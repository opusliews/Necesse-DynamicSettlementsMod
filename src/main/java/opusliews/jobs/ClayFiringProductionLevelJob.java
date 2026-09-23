package opusliews.jobs;

import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.*;
import necesse.entity.mobs.job.activeJob.ActiveJob;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.gfx.GameColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.level.maps.levelData.jobs.HasStorageLevelJob;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementStoragePickupSlot;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageGlobalIngredientIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageItemIDIndex;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecords;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.clay.ClayPackageSystem;
import opusliews.clayfiring.ClayFiringAutomationLevelData;
import opusliews.clayfiring.ClayFiringCatalog;
import opusliews.clayfiring.ClayFiringZone;
import opusliews.item.FirestarterItem;
import opusliews.logging.Logging;
import opusliews.network.PacketBuilderTilePlaceSound;
import opusliews.tile.*;
import opusliews.tile.CharcoalPitLevelData.StoredFiringItem;
import opusliews.tile.CharcoalPitLevelData.StoredLog;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ClayFiringProductionLevelJob extends TileLevelJob {
	public static final int requiredLogs = 16;
	private static final long grassRemovalTime = 1200L;
	private static final long holeDigTime = 1600L;
	private static final long logLoadingTime = 1600L;
	private static final long ignitionTime = 1600L;

	private final ClayFiringZone zone;
	private final boolean reuseExistingHole;
	private final ArrayList<String> plannedUnfiredItems = new ArrayList<>();

	public ClayFiringProductionLevelJob(int tileX, int tileY, ClayFiringZone zone) {
		this(tileX, tileY, zone, false);
	}

	public ClayFiringProductionLevelJob(int tileX, int tileY, ClayFiringZone zone, boolean reuseExistingHole) {
		super(tileX, tileY);
		this.zone = zone;
		this.reuseExistingHole = reuseExistingHole;
	}

	public ClayFiringProductionLevelJob(LoadData save) {
		super(save);
		this.zone = null;
		this.reuseExistingHole = false;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid() || zone == null || zone.isRemoved() || !zone.containsTile(tileX, tileY)) return false;
		if (!zone.canProduce()) return false;
		return reuseExistingHole
				? ClayFiringZone.isValidReusableHole(getLevel(), tileX, tileY, this)
				: ClayFiringZone.isValidCandidate(getLevel(), tileX, tileY, this);
	}

	private TileActiveJob getActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
			private Point workTile;
			private boolean grassStarted;
			private long grassDone;
			private boolean digStarted = reuseExistingHole;
			private long digDone;
			private boolean clayPlaced;
			private boolean logsStarted;
			private long logsDone;
			private boolean ignitionStarted;
			private long ignitionDone;
			private boolean burnStarted;
			private boolean activeProductionClaimed;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				Point selectedWorkTile = getWorkTile();
				if (selectedWorkTile == null) {
					Logging.logMessage("[ClayFiring] Lost work tile at " + tileX + "," + tileY
							+ " clayPlaced=" + clayPlaced
							+ " batch=" + plannedUnfiredItems.size()
							+ " active=" + activeProductionClaimed);
					((HumanMob)worker.getMobWorker()).cancelJob();
					return null;
				}
				return new JobMoveToTile(selectedWorkTile.x, selectedWorkTile.y, false);
			}

			private Point getWorkTile() {
				HumanMob mob = (HumanMob)worker.getMobWorker();
				if (isValidWorkTile(mob, workTile, false)) return workTile;

				Point currentTile = new Point(mob.getTileX(), mob.getTileY());
				if (isValidWorkTile(mob, currentTile, false)) {
					workTile = currentTile;
					return workTile;
				}

				for (int[] offset : CharcoalProductionZone.cardinalOffsets) {
					Point candidate = new Point(tileX + offset[0], tileY + offset[1]);
					if (isValidWorkTile(mob, candidate, true)) {
						workTile = candidate;
						return workTile;
					}
				}

				workTile = null;
				return null;
			}

			private boolean isValidWorkTile(HumanMob mob, Point candidate, boolean checkReachable) {
				if (candidate == null || !getLevel().isTileWithinBounds(candidate.x, candidate.y)) return false;
				int dx = Math.abs(candidate.x - tileX);
				int dy = Math.abs(candidate.y - tileY);
				if (dx > 1 || dy > 1 || (dx == 0 && dy == 0)) return false;
				if (FireHazardPathing.isFireHazard(getLevel(), candidate.x, candidate.y)) return false;
				return !checkReachable || mob.estimateCanMoveTo(candidate.x, candidate.y, false);
			}

			@Override
			public int getCompleteRange() {
				return 8;
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				if (zone == null || zone.isRemoved() || !zone.containsTile(tileX, tileY)) return false;
				int tileID = getLevel().getTileID(tileX, tileY);
				boolean ownsPit = tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
						|| tileID == TileRegistry.getTileID(FiringPitLogTile.stringID)
						|| isFiringPitTile(tileID);
				if (ownsPit && getLevel().getObjectID(tileX, tileY) == 0) return true;
				if (ClayFiringProductionLevelJob.this.isRemoved()
						|| !ClayFiringProductionLevelJob.this.reservable.isAvailable(worker.getMobWorker())) return false;
				return reuseExistingHole
						? ClayFiringZone.isValidReusableHole(getLevel(), tileX, tileY, ClayFiringProductionLevelJob.this)
						: ClayFiringZone.isValidCandidate(getLevel(), tileX, tileY, ClayFiringProductionLevelJob.this);
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
				Logging.logMessage("[ClayFiring] Production cancelled at " + tileX + "," + tileY
						+ " invalid=" + becauseOfInvalid
						+ " current=" + isCurrent
						+ " moving=" + isMovingTo
						+ " clayPlaced=" + clayPlaced
						+ " batch=" + plannedUnfiredItems.size()
						+ " tile=" + getLevel().getTile(tileX, tileY).getStringID());
				if (!burnStarted) rollback(worker);
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				ClayFiringProductionLevelJob.this.reservable.reserve(worker.getMobWorker());
				if (!isCurrent || isMovingTo) return;

				Item animationItem = ItemRegistry.getItem("ironshovel");
				int tileID = getLevel().getTileID(tileX, tileY);
				boolean holeReady = tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
						|| tileID == TileRegistry.getTileID(FiringPitLogTile.stringID)
						|| isFiringPitTile(tileID);

				if (holeReady) {
					if (!clayPlaced && !plannedUnfiredItems.isEmpty()) {
						Item planned = ItemRegistry.getItem(plannedUnfiredItems.get(0));
						if (planned != null) animationItem = planned;
					} else if (logsStarted && !ignitionStarted) {
						InventoryItem log = getFirstWorkInventoryLog(worker);
						if (log != null) animationItem = log.item;
					} else if (ignitionStarted) {
						animationItem = ItemRegistry.getItem(FirestarterItem.stringID);
					}
				}

				worker.showWorkAnimation(tileX * 32 + 16, tileY * 32 + 16, animationItem, 700, true);
			}

			@Override
			public ActiveJobResult perform() {
				if (!isValid(true)) return ActiveJobResult.FAILED;
				ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(getLevel(), true);
				if (!activeProductionClaimed) {
					if (!automation.claimActiveProduction(tileX, tileY, zone.getUniqueID())) {
						Logging.logMessage("[ClayFiring] Active production claim rejected at " + tileX + "," + tileY);
						return ActiveJobResult.FAILED;
					}
					activeProductionClaimed = true;
					Logging.logMessage("[ClayFiring] Active production claimed at " + tileX + "," + tileY
							+ " batch=" + plannedUnfiredItems.size());
				}
				long now = getLevel().getTime();

				if (FiringGroundSystem.shouldDigToDirt(getLevel(), tileX, tileY)) {
					if (!grassStarted) {
						grassStarted = true;
						grassDone = now + grassRemovalTime;
						return ActiveJobResult.PERFORMING;
					}
					if (now < grassDone) return ActiveJobResult.PERFORMING;
					getLevel().setObject(tileX, tileY, 0);
					getLevel().setTile(tileX, tileY, TileRegistry.dirtID);
					getLevel().sendObjectUpdatePacket(tileX, tileY);
					getLevel().sendTileUpdatePacket(tileX, tileY);
				}

				if (!digStarted) {
					digStarted = true;
					digDone = now + holeDigTime;
					return ActiveJobResult.PERFORMING;
				}
				int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
				if (!clayPlaced && getLevel().getTileID(tileX, tileY) != shallowHoleID) {
					if (now < digDone) return ActiveJobResult.PERFORMING;
					if (!ClayFiringZone.isValidCandidate(getLevel(), tileX, tileY, ClayFiringProductionLevelJob.this)) return ActiveJobResult.FAILED;
					getLevel().setTile(tileX, tileY, shallowHoleID);
					getLevel().sendTileUpdatePacket(tileX, tileY);
					getLevel().getLevelTile(tileX, tileY).checkAround();
					getLevel().getLevelObject(tileX, tileY).checkAround();
					getLevel().getServer().network.sendToClientsWithTile(new PacketBuilderTilePlaceSound(shallowHoleID, tileX, tileY), getLevel(), tileX, tileY);
				}

				CharcoalPitLevelData pitData = CharcoalPitLevelData.get(getLevel(), true);
				if (!clayPlaced) {
					InventoryItem clayPackage = ClayPackageSystem.findPackage(worker.getWorkInventory().items());
					if (clayPackage == null) return ActiveJobResult.FAILED;

					LinkedHashMap<String, Integer> requiredClay = new LinkedHashMap<>();
					for (String itemStringID : plannedUnfiredItems) {
						requiredClay.put(itemStringID, requiredClay.getOrDefault(itemStringID, 0) + 1);
					}
					for (Map.Entry<String, Integer> entry : requiredClay.entrySet()) {
						if (ClayPackageSystem.getAmount(clayPackage, entry.getKey()) < entry.getValue()) return ActiveJobResult.FAILED;
					}

					ArrayList<InventoryItem> removedClay = new ArrayList<>();
					ArrayList<StoredFiringItem> firingItems = new ArrayList<>();
					for (String itemStringID : plannedUnfiredItems) {
						InventoryItem removed = ClayPackageSystem.remove(clayPackage, itemStringID, 1);
						if (removed == null) {
							for (InventoryItem restore : removedClay) ClayPackageSystem.add(clayPackage, restore);
							return ActiveJobResult.FAILED;
						}
						removedClay.add(removed);
						firingItems.add(new StoredFiringItem(itemStringID));
					}

					pitData.setFiringItems(tileX, tileY, firingItems);
					clayPlaced = true;
					if (ClayPackageSystem.isEmpty(clayPackage)) removePackage(worker, clayPackage);
					else worker.getWorkInventory().markDirty();
					getLevel().setTile(tileX, tileY, TileRegistry.getTileID(FiringPitTile.getStringID(firingItems.size())));
					getLevel().sendTileUpdatePacket(tileX, tileY);
					Logging.logMessage("[ClayFiring] Placed clay batch at " + tileX + "," + tileY
							+ " count=" + firingItems.size() + " items=" + plannedUnfiredItems);
					return ActiveJobResult.PERFORMING;
				}

				if (!logsStarted) {
					logsStarted = true;
					logsDone = now + logLoadingTime;
					return ActiveJobResult.PERFORMING;
				}
				if (getLevel().getTileID(tileX, tileY) != TileRegistry.getTileID(FiringPitLogTile.stringID)) {
					if (now < logsDone) return ActiveJobResult.PERFORMING;
					List<StoredLog> logs = removeWorkInventoryLogs(worker, requiredLogs);
					if (logs == null) return ActiveJobResult.FAILED;
					pitData.setLogs(tileX, tileY, logs);
					Logging.logMessage("[ClayFiring] Loaded " + requiredLogs + " logs at " + tileX + "," + tileY
							+ " after placing " + plannedUnfiredItems.size() + " clay item(s)");
					getLevel().setTile(tileX, tileY, TileRegistry.getTileID(FiringPitLogTile.stringID));
					getLevel().sendTileUpdatePacket(tileX, tileY);
				}

				if (!ignitionStarted) {
					ignitionStarted = true;
					ignitionDone = now + ignitionTime;
					return ActiveJobResult.PERFORMING;
				}
				if (now < ignitionDone) return ActiveJobResult.PERFORMING;
				if (getLevel().getTileID(tileX, tileY) != TileRegistry.getTileID(FiringPitLogTile.stringID)) return ActiveJobResult.FAILED;

				long fullDayDuration = (long)getLevel().getWorldEntity().getDayTimeMax() * 1000L;
				automation.releaseActiveProduction(tileX, tileY);
				activeProductionClaimed = false;
				automation.markAutomatedBurn(tileX, tileY, zone.getUniqueID());
				pitData.igniteFiringPit(tileX, tileY, getLevel().getWorldEntity().getWorldTime() + fullDayDuration);
				burnStarted = true;
				Logging.logMessage("[ClayFiring] Started automated burn at " + tileX + "," + tileY + " items=" + plannedUnfiredItems.size());
				ClayFiringProductionLevelJob.this.remove();
				return ActiveJobResult.FINISHED;
			}
		};
	}

	private void rollback(EntityJobWorker worker) {
		ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(getLevel(), true);
		automation.releaseActiveProduction(tileX, tileY);
		automation.clearAutomatedBurn(tileX, tileY);
		CharcoalPitLevelData pitData = CharcoalPitLevelData.get(getLevel(), false);
		if (pitData != null) {
			for (StoredFiringItem stored : pitData.removeFiringItems(tileX, tileY)) {
				ClayPackageSystem.addToWorkInventory(worker.getWorkInventory(), new InventoryItem(stored.itemStringID, 1));
			}
			for (StoredLog log : pitData.removeLogs(tileX, tileY)) {
				worker.getWorkInventory().add(new InventoryItem(log.itemStringID, log.amount));
			}
			pitData.removeFiringBurn(tileX, tileY);
		}
		int tileID = getLevel().getTileID(tileX, tileY);
		if (tileID == TileRegistry.getTileID(ShallowHoleTile.stringID)
				|| tileID == TileRegistry.getTileID(FiringPitLogTile.stringID)
				|| isFiringPitTile(tileID)) {
			getLevel().setTile(tileX, tileY, TileRegistry.dirtID);
			getLevel().sendTileUpdatePacket(tileX, tileY);
		}
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		ClayFiringProductionLevelJob job = (ClayFiringProductionLevelJob)foundJob.job;
		Logging.logMessage("[ClayFiring] Building job sequence tile=" + job.tileX + "," + job.tileY
				+ " worker=" + worker.getMobWorker().getUniqueID() + " valid=" + job.isValid());
		if (!job.isValid()) return null;

		List<ActiveJob> dropOffJobs = new ArrayList<>();
		if (!addNonPackageDropOffJobs(worker, foundJob.priority, dropOffJobs)) return null;

		ClayReservationPlan clayPlan = reserveClayItems(worker, job.zone);
		Logging.logMessage("[ClayFiring] Clay reservations tile=" + job.tileX + "," + job.tileY
				+ " count=" + clayPlan.reservations.size() + " items=" + clayPlan.itemStringIDs);
		if (clayPlan.reservations.isEmpty()) {
			cancelPlannedJobs(dropOffJobs);
			sendBlockedMessage(worker, "clayfiringmissingitems");
			return null;
		}
		List<SettlementStoragePickupSlot> logReservations = reserveLogs(worker);
		Logging.logMessage("[ClayFiring] Log reservations tile=" + job.tileX + "," + job.tileY
				+ " count=" + (logReservations == null ? 0 : logReservations.size()));
		if (logReservations == null) {
			releaseReservations(clayPlan.reservations);
			cancelPlannedJobs(dropOffJobs);
			sendBlockedMessage(worker, "clayfiringmissinglogs");
			return null;
		}

		ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(job.getLevel(), true);
		automation.setProductionRoundRobinCursor(clayPlan.nextCursor, clayPlan.catalogSize);

		job.plannedUnfiredItems.clear();
		job.plannedUnfiredItems.addAll(clayPlan.itemStringIDs);

		Logging.logMessage("[ClayFiring] Planned balanced batch tile=" + job.tileX + "," + job.tileY
				+ " items=" + job.plannedUnfiredItems + " nextCursor=" + clayPlan.nextCursor);
		LinkedListJobSequence sequence = new LinkedListJobSequence(new LocalMessage("activities", "clayfiringproduction"), false);
		sequence.addAll(dropOffJobs);
		for (SettlementStoragePickupSlot slot : clayPlan.reservations) {
			sequence.add(new PickupSettlementStorageActiveJob(worker, foundJob.priority, slot, new AtomicReference<>()));
		}
		for (SettlementStoragePickupSlot slot : logReservations) {
			sequence.add(new PickupSettlementStorageActiveJob(worker, foundJob.priority, slot, new AtomicReference<>()));
		}
		sequence.add(job.getActiveJob(worker, foundJob.priority));
		return sequence;
	}

	private static boolean addNonPackageDropOffJobs(EntityJobWorker worker, JobTypeHandler.TypePriority priority, List<ActiveJob> jobs) {
		ArrayList<InventoryItem> currentItems = new ArrayList<>();
		for (Object value : worker.getWorkInventory().items()) {
			if (value instanceof InventoryItem) currentItems.add(((InventoryItem)value).copy());
		}
		for (InventoryItem item : currentItems) {
			if (item == null || item.getAmount() <= 0 || ClayPackageSystem.isPackage(item)) continue;
			ArrayList<HasStorageLevelJob.DropOffFind> dropOffLocations = HasStorageLevelJob.findDropOffLocation(worker, item.copy());
			int capacity = 0;
			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) capacity += location.item.getAmount();
			if (capacity < item.getAmount()) {
				cancelPlannedJobs(jobs);
				return false;
			}
			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) jobs.add(location.getActiveJob(worker, priority, null, false));
		}
		return true;
	}

	private static ClayReservationPlan reserveClayItems(EntityJobWorker worker, ClayFiringZone zone) {
		ClayReservationPlan result = new ClayReservationPlan();
		SettlementStorageRecords records = PickupSettlementStorageActiveJob.getStorageRecords(worker);
		if (records == null || zone == null) return result;

		SettlementStorageItemIDIndex index = records.getIndex(SettlementStorageItemIDIndex.class);
		List<ClayFiringCatalog.Entry> entries = ClayFiringCatalog.getEntries();
		result.catalogSize = entries.size();
		if (entries.isEmpty()) return result;

		Map<String, Integer> remainingByUnfiredID = zone.getNeededUnfiredAmounts(FiringPitTile.maxItems);
		if (remainingByUnfiredID.isEmpty()) return result;

		ClayFiringAutomationLevelData automation = ClayFiringAutomationLevelData.get(worker.getMobWorker().getLevel(), true);
		int cursor = automation.getProductionRoundRobinCursor(entries.size());
		result.nextCursor = cursor;

		LinkedHashMap<String, Integer> locallyAvailable = new LinkedHashMap<>();
		for (ClayFiringCatalog.Entry entry : entries) {
			Item item = ItemRegistry.getItem(entry.unfiredItemStringID);
			locallyAvailable.put(entry.unfiredItemStringID, item == null ? 0 : index.getTotalItems(item));
		}

		int stalled = 0;
		while (result.itemStringIDs.size() < FiringPitTile.maxItems && stalled < entries.size()) {
			int entryIndex = cursor;
			ClayFiringCatalog.Entry entry = entries.get(entryIndex);
			cursor = (cursor + 1) % entries.size();

			int remainingForEntry = remainingByUnfiredID.getOrDefault(entry.unfiredItemStringID, 0);
			int availableForEntry = locallyAvailable.getOrDefault(entry.unfiredItemStringID, 0);
			if (remainingForEntry <= 0 || availableForEntry <= 0) {
				stalled++;
				continue;
			}

			Item item = ItemRegistry.getItem(entry.unfiredItemStringID);
			if (item == null) {
				locallyAvailable.put(entry.unfiredItemStringID, 0);
				stalled++;
				continue;
			}

			LinkedList<SettlementStoragePickupSlot> slots = index.findPickupSlots(item, worker, null, 1, 1);
			if (slots == null || slots.isEmpty()) {
				locallyAvailable.put(entry.unfiredItemStringID, 0);
				stalled++;
				continue;
			}

			SettlementStoragePickupSlot slot = slots.getFirst();
			result.reservations.add(slot);
			result.itemStringIDs.add(entry.unfiredItemStringID);
			remainingByUnfiredID.put(entry.unfiredItemStringID, remainingForEntry - 1);
			locallyAvailable.put(entry.unfiredItemStringID, availableForEntry - 1);
			result.nextCursor = cursor;
			stalled = 0;
		}

		return result;
	}

	private static class ClayReservationPlan {
		final ArrayList<SettlementStoragePickupSlot> reservations = new ArrayList<>();
		final ArrayList<String> itemStringIDs = new ArrayList<>();
		int nextCursor;
		int catalogSize;
	}

	private static List<SettlementStoragePickupSlot> reserveLogs(EntityJobWorker worker) {
		SettlementStorageRecords records = PickupSettlementStorageActiveJob.getStorageRecords(worker);
		if (records == null) return null;
		int anyLogID = GlobalIngredientRegistry.getGlobalIngredientID("anylog");
		SettlementStorageGlobalIngredientIDIndex globalIndex = records.getIndex(SettlementStorageGlobalIngredientIDIndex.class);
		SettlementStorageRecordsRegionData data = globalIndex.getGlobalIngredient(anyLogID);
		if (data == null || data.getTotalItems() < requiredLogs) return null;

		SettlementStorageItemIDIndex itemIndex = records.getIndex(SettlementStorageItemIDIndex.class);
		ArrayList<SettlementStoragePickupSlot> result = new ArrayList<>();
		int remaining = requiredLogs;
		for (Object value : ItemRegistry.getItems()) {
			Item item = (Item)value;
			if (!item.isGlobalIngredient("anylog")) continue;
			int available = itemIndex.getTotalItems(item);
			if (available <= 0) continue;
			LinkedList<SettlementStoragePickupSlot> slots = itemIndex.findPickupSlots(item, worker, null, 1, Math.min(remaining, available));
			if (slots == null) continue;
			for (SettlementStoragePickupSlot slot : slots) {
				result.add(slot);
				remaining -= slot.item.getAmount();
				if (remaining <= 0) return result;
			}
		}
		releaseReservations(result);
		return null;
	}

	private static void removePackage(EntityJobWorker worker, InventoryItem packageItem) {
		ListIterator iterator = worker.getWorkInventory().listIterator();
		while (iterator.hasNext()) {
			Object value = iterator.next();
			if (value == packageItem) {
				iterator.remove();
				worker.getWorkInventory().markDirty();
				return;
			}
		}
	}

	private static InventoryItem getFirstWorkInventoryLog(EntityJobWorker worker) {
		for (InventoryItem item : worker.getWorkInventory().items()) if (isLog(item)) return item;
		return null;
	}

	private static List<StoredLog> removeWorkInventoryLogs(EntityJobWorker worker, int amount) {
		int count = 0;
		for (InventoryItem item : worker.getWorkInventory().items()) if (isLog(item)) count += item.getAmount();
		if (count < amount) return null;
		LinkedHashMap<String, Integer> removed = new LinkedHashMap<>();
		int remaining = amount;
		ListIterator iterator = worker.getWorkInventory().listIterator();
		while (iterator.hasNext() && remaining > 0) {
			InventoryItem item = (InventoryItem)iterator.next();
			if (!isLog(item)) continue;
			int take = Math.min(remaining, item.getAmount());
			removed.put(item.item.getStringID(), removed.getOrDefault(item.item.getStringID(), 0) + take);
			remaining -= take;
			if (take >= item.getAmount()) iterator.remove();
			else item.setAmount(item.getAmount() - take);
		}
		worker.getWorkInventory().markDirty();
		ArrayList<StoredLog> result = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : removed.entrySet()) result.add(new StoredLog(entry.getKey(), entry.getValue()));
		return result;
	}

	private static boolean isLog(InventoryItem item) {
		return item != null && item.item.isGlobalIngredient("anylog");
	}

	private static boolean isFiringPitTile(int tileID) {
		for (int i = 1; i <= FiringPitTile.maxItems; i++) if (tileID == TileRegistry.getTileID(FiringPitTile.getStringID(i))) return true;
		return false;
	}

	private static void releaseReservations(List<SettlementStoragePickupSlot> slots) {
		for (SettlementStoragePickupSlot slot : slots) if (!slot.isRemoved()) slot.remove();
	}

	private static void cancelPlannedJobs(List<ActiveJob> jobs) {
		for (ActiveJob job : jobs) job.onCancelled(true, false, false);
		jobs.clear();
	}

	private static void sendBlockedMessage(EntityJobWorker worker, String translationKey) {
		if (!(worker.getMobWorker() instanceof SettlerMob)) return;
		SettlerMob settlerMob = (SettlerMob)worker.getMobWorker();
		ServerSettlementData settlement = settlerMob.getSettlerSettlementServerData();
		if (settlement == null) return;
		String message = GameColor.RED.getColorCode() + Localization.translate("jobs", translationKey);
		settlement.networkData.streamTeamMembers().forEach(client -> client.sendChatMessage(message));
		Logging.logMessage(Localization.translate("jobs", translationKey));
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof HumanMob)) return null;
		HumanMob human = (HumanMob)worker;
		return handler.setJobHandler(ClayFiringProductionLevelJob.class, foundJob -> getJobSequence(human, foundJob))
				.setPredicate(() -> !human.isOnStrike() && !human.hasCompletedMission()
						&& (!human.isSettler() || human.isSettlerWithinSettlement()));
	}
}
