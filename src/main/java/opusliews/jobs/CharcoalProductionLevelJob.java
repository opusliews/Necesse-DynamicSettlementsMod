package opusliews.jobs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.GlobalIngredientRegistry;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.FoundJob;
import necesse.entity.mobs.job.JobSequence;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.entity.mobs.job.LinkedListJobSequence;
import necesse.entity.mobs.job.activeJob.ActiveJob;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
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
import necesse.gfx.GameColor;
import necesse.level.maps.levelData.settlementData.storage.SettlementStorageRecordsRegionData;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.item.FirestarterItem;
import opusliews.logging.Logging;
import opusliews.network.PacketBuilderTilePlaceSound;
import opusliews.tile.CharcoalPitLevelData;
import opusliews.tile.CharcoalPitLevelData.StoredLog;
import opusliews.tile.CharcoalPitLevelData.ProductionStage;
import opusliews.tile.CharcoalPitTile;
import opusliews.tile.BurningCharcoalPitTile;
import opusliews.tile.CoveredCharcoalPitTile;
import opusliews.tile.ShallowHoleTile;

public class CharcoalProductionLevelJob extends TileLevelJob {
	public static final int requiredLogs = 32;
	private static final long grassRemovalTime = 2000L;
	private static final long holeDigTime = 2000L;
	private static final long logLoadingTime = 2000L;
	private static final long pitCoveringTime = 2000L;
	private static final long pitIgnitionTime = 2000L;

	private final CharcoalProductionZone zone;
	private final boolean reuseExistingHole;
	private final ProductionStage recoveryStage;

	public CharcoalProductionLevelJob(int tileX, int tileY, CharcoalProductionZone zone) {
		this(tileX, tileY, zone, false, null);
	}

	public CharcoalProductionLevelJob(int tileX, int tileY, CharcoalProductionZone zone, boolean reuseExistingHole) {
		this(tileX, tileY, zone, reuseExistingHole, null);
	}

	public CharcoalProductionLevelJob(
			int tileX,
			int tileY,
			CharcoalProductionZone zone,
			boolean reuseExistingHole,
			ProductionStage recoveryStage
	) {
		super(tileX, tileY);
		this.zone = zone;
		this.reuseExistingHole = reuseExistingHole;
		this.recoveryStage = recoveryStage;
	}

	public CharcoalProductionLevelJob(LoadData save) {
		super(save);
		this.zone = null;
		this.reuseExistingHole = false;
		this.recoveryStage = null;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid() || zone == null || zone.isRemoved() || !zone.containsTile(tileX, tileY)) {
			return false;
		}

		if (recoveryStage != null) {
			return isRecoveryStagePresent();
		}

		return zone.canProduce()
				&& (reuseExistingHole
						? CharcoalProductionZone.isValidReusableHole(getLevel(), tileX, tileY, this)
						: CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, this));
	}

	private boolean isRecoveryStagePresent() {
		int tileID = getLevel().getTileID(tileX, tileY);
		if (recoveryStage == ProductionStage.COVERED) {
			return tileID == TileRegistry.getTileID(CoveredCharcoalPitTile.stringID)
					&& !CharcoalPitLevelData.get(getLevel(), true).getLogs(tileX, tileY).isEmpty();
		}
		if (recoveryStage == ProductionStage.LOADED) {
			return tileID == TileRegistry.getTileID(CharcoalPitTile.stringID)
					&& !CharcoalPitLevelData.get(getLevel(), true).getLogs(tileX, tileY).isEmpty();
		}
		return tileID == TileRegistry.getTileID(ShallowHoleTile.stringID);
	}

	private TileActiveJob getActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
			private boolean grassRemovalStarted;
			private long grassRemovalCompleteTime;
			private boolean holeDigStarted = reuseExistingHole || recoveryStage != null;
			private long holeDigCompleteTime;
			private boolean logLoadingStarted = recoveryStage == ProductionStage.LOADED || recoveryStage == ProductionStage.COVERED;
			private long logLoadingCompleteTime;
			private boolean logsLoaded = recoveryStage == ProductionStage.LOADED || recoveryStage == ProductionStage.COVERED;
			private boolean pitCoveringStarted = recoveryStage == ProductionStage.COVERED;
			private long pitCoveringCompleteTime;
			private boolean pitCovered = recoveryStage == ProductionStage.COVERED;
			private boolean pitIgnitionStarted;
			private long pitIgnitionCompleteTime;
			private boolean burnStarted;
			private boolean loggedPitActionStart;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				return new JobMoveToTile(tileX, tileY, true);
			}

			@Override
			public int getCompleteRange() {
				return 8;
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
				rollbackPit();
			}

			private void rollbackPit() {
				int burningCharcoalPitID = TileRegistry.getTileID(BurningCharcoalPitTile.stringID);
				if (burnStarted || getLevel().getTileID(tileX, tileY) == burningCharcoalPitID) {
					return;
				}

				CharcoalPitLevelData pitData = CharcoalPitLevelData.get(getLevel(), false);
				if (pitData != null) {
					pitData.removeBurn(tileX, tileY);
					pitData.clearProductionRecoveryState(tileX, tileY);
					List<StoredLog> storedLogs = pitData.removeLogs(tileX, tileY);
					for (StoredLog log : storedLogs) {
						if (log.amount > 0 && ItemRegistry.getItem(log.itemStringID) != null) {
							worker.getWorkInventory().add(new InventoryItem(log.itemStringID, log.amount));
						}
					}
				}

				int currentTileID = getLevel().getTileID(tileX, tileY);
				int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
				int charcoalPitID = TileRegistry.getTileID(CharcoalPitTile.stringID);
				int coveredCharcoalPitID = TileRegistry.getTileID(CoveredCharcoalPitTile.stringID);
				if (currentTileID == shallowHoleID || currentTileID == charcoalPitID || currentTileID == coveredCharcoalPitID) {
					getLevel().setTile(tileX, tileY, TileRegistry.dirtID);
					getLevel().sendTileUpdatePacket(tileX, tileY);
					getLevel().getLevelTile(tileX, tileY).checkAround();
					getLevel().getLevelObject(tileX, tileY).checkAround();
				}
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				CharcoalProductionLevelJob.this.reservable.reserve(worker.getMobWorker());

				if (!isCurrent || isMovingTo) {
					return;
				}

				if (!loggedPitActionStart) {
					loggedPitActionStart = true;
					Logging.logMessage("[CharcoalPickup] Entered pit action: worker=" + worker.getMobWorker().getUniqueID()
							+ ", pit=" + tileX + "," + tileY
							+ ", workLogs=" + countWorkInventoryLogs(worker));
				}

				if (pitIgnitionStarted) {
					worker.showWorkAnimation(
							tileX * 32 + 16,
							tileY * 32 + 16,
							ItemRegistry.getItem(FirestarterItem.stringID),
							1000,
							true
					);
				} else if (pitCoveringStarted) {
					worker.showWorkAnimation(
							tileX * 32 + 16,
							tileY * 32 + 16,
							ItemRegistry.getItem("ironshovel"),
							1000,
							true
					);
				} else if (logLoadingStarted) {
					InventoryItem log = getFirstWorkInventoryLog(worker);
					if (log != null) {
						worker.showWorkAnimation(tileX * 32 + 16, tileY * 32 + 16, log.item, 1000, true);
					}
				} else if (!reuseExistingHole && (hasGrassTile() || holeDigStarted)) {
					worker.showWorkAnimation(
							tileX * 32 + 16,
							tileY * 32 + 16,
							ItemRegistry.getItem("ironshovel"),
							1000,
							true
					);
				}
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				if (zone == null || zone.isRemoved() || !zone.containsTile(tileX, tileY)) {
					return false;
				}

				if (!zone.canProduce() && recoveryStage == null) {
					return false;
				}

				if (isCurrent && zone.canProduce() && !logsLoaded && countWorkInventoryLogs(worker) < requiredLogs) {
					sendBlockedMessage(worker, "charcoalworkerlogsmissing");
					return false;
				}

				int currentTileID = getLevel().getTileID(tileX, tileY);
				int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
				int charcoalPitID = TileRegistry.getTileID(CharcoalPitTile.stringID);
				int coveredCharcoalPitID = TileRegistry.getTileID(CoveredCharcoalPitTile.stringID);

				boolean ownsDugPit = holeDigStarted && currentTileID == shallowHoleID;
				boolean ownsLoadedPit = logsLoaded
						&& (currentTileID == charcoalPitID || currentTileID == coveredCharcoalPitID);
				if (ownsDugPit || ownsLoadedPit) {
					return true;
				}

				if (CharcoalProductionLevelJob.this.isRemoved()
						|| !CharcoalProductionLevelJob.this.reservable.isAvailable(worker.getMobWorker())) {
					return false;
				}

				return reuseExistingHole
						? CharcoalProductionZone.isValidReusableHole(getLevel(), tileX, tileY, CharcoalProductionLevelJob.this)
						: CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, CharcoalProductionLevelJob.this);
			}

			@Override
			public ActiveJobResult perform() {
				long currentTime = getLevel().getTime();

				if (recoveryStage != null && !zone.canProduce()) {
					rollbackPit();
					CharcoalProductionLevelJob.this.remove();
					return ActiveJobResult.FINISHED;
				}

				if (hasGrassTile()) {
					if (!grassRemovalStarted) {
						grassRemovalStarted = true;
						grassRemovalCompleteTime = currentTime + grassRemovalTime;
						return ActiveJobResult.PERFORMING;
					}

					if (currentTime < grassRemovalCompleteTime) {
						return ActiveJobResult.PERFORMING;
					}

					if (hasGrassTile()) {
						getLevel().setObject(tileX, tileY, 0);
						getLevel().setTile(tileX, tileY, TileRegistry.dirtID);
						getLevel().sendObjectUpdatePacket(tileX, tileY);
						getLevel().sendTileUpdatePacket(tileX, tileY);
						getLevel().getServer().network.sendToClientsWithTile(
								new PacketBuilderTilePlaceSound(TileRegistry.dirtID, tileX, tileY),
								getLevel(),
								tileX,
								tileY
						);
					}
				}

				if (!holeDigStarted) {
					holeDigStarted = true;
					holeDigCompleteTime = currentTime + holeDigTime;
					return ActiveJobResult.PERFORMING;
				}

				int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
				if (!logsLoaded && getLevel().getTileID(tileX, tileY) != shallowHoleID) {
					if (currentTime < holeDigCompleteTime) {
						return ActiveJobResult.PERFORMING;
					}

					if (!CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, CharcoalProductionLevelJob.this)) {
						sendBlockedMessage(worker, "charcoalinvalidsite");
						return ActiveJobResult.FAILED;
					}

					getLevel().setTile(tileX, tileY, shallowHoleID);
					getLevel().sendTileUpdatePacket(tileX, tileY);
					getLevel().getLevelTile(tileX, tileY).checkAround();
					getLevel().getLevelObject(tileX, tileY).checkAround();
					CharcoalPitLevelData.get(getLevel(), true).setProductionRecoveryState(
							tileX, tileY, ProductionStage.DUG, zone.getUniqueID());
					getLevel().getServer().network.sendToClientsWithTile(
							new PacketBuilderTilePlaceSound(shallowHoleID, tileX, tileY),
							getLevel(),
							tileX,
							tileY
					);
				}

				if (!logLoadingStarted) {
					logLoadingStarted = true;
					logLoadingCompleteTime = currentTime + logLoadingTime;
					return ActiveJobResult.PERFORMING;
				}

				int charcoalPitID = TileRegistry.getTileID(CharcoalPitTile.stringID);
				if (!logsLoaded) {
					if (currentTime < logLoadingCompleteTime) {
						return ActiveJobResult.PERFORMING;
					}

					if (getLevel().getTileID(tileX, tileY) != shallowHoleID) {
						sendBlockedMessage(worker, "charcoalinvalidsite");
						return ActiveJobResult.FAILED;
					}

					List<StoredLog> logs = removeWorkInventoryLogs(worker, requiredLogs);
					if (logs == null) {
						sendBlockedMessage(worker, "charcoalworkerlogsmissing");
						return ActiveJobResult.FAILED;
					}

					CharcoalPitLevelData.get(getLevel(), true).setLogs(tileX, tileY, logs);
					getLevel().setTile(tileX, tileY, charcoalPitID);
					getLevel().sendTileUpdatePacket(tileX, tileY);
					getLevel().getLevelTile(tileX, tileY).checkAround();
					getLevel().getLevelObject(tileX, tileY).checkAround();
					getLevel().getServer().network.sendToClientsWithTile(
							new PacketBuilderTilePlaceSound(charcoalPitID, tileX, tileY),
							getLevel(),
							tileX,
							tileY
					);
					logsLoaded = true;
					pitDataForRecovery().setProductionRecoveryState(
							tileX, tileY, ProductionStage.LOADED, zone.getUniqueID());
				}

				if (!pitCoveringStarted) {
					pitCoveringStarted = true;
					pitCoveringCompleteTime = currentTime + pitCoveringTime;
					return ActiveJobResult.PERFORMING;
				}

				if (currentTime < pitCoveringCompleteTime) {
					return ActiveJobResult.PERFORMING;
				}

				int coveredCharcoalPitID = TileRegistry.getTileID(CoveredCharcoalPitTile.stringID);
				CharcoalPitLevelData pitData = CharcoalPitLevelData.get(getLevel(), false);
				if (pitData == null || pitData.getLogs(tileX, tileY).isEmpty()) {
					sendBlockedMessage(worker, "charcoalinvalidsite");
					return ActiveJobResult.FAILED;
				}

				if (!pitCovered) {
					int currentTileID = getLevel().getTileID(tileX, tileY);
					if (currentTileID == charcoalPitID) {
						getLevel().setTile(tileX, tileY, coveredCharcoalPitID);
						getLevel().sendTileUpdatePacket(tileX, tileY);
						getLevel().getLevelTile(tileX, tileY).checkAround();
						getLevel().getLevelObject(tileX, tileY).checkAround();
						getLevel().getServer().network.sendToClientsWithTile(
								new PacketBuilderTilePlaceSound(coveredCharcoalPitID, tileX, tileY),
								getLevel(),
								tileX,
								tileY
						);
					} else if (currentTileID != coveredCharcoalPitID) {
						sendBlockedMessage(worker, "charcoalinvalidsite");
						return ActiveJobResult.FAILED;
					}
					pitCovered = true;
					pitData.setProductionRecoveryState(tileX, tileY, ProductionStage.COVERED, zone.getUniqueID());
				}

				if (!pitIgnitionStarted) {
					pitIgnitionStarted = true;
					pitIgnitionCompleteTime = currentTime + pitIgnitionTime;
					return ActiveJobResult.PERFORMING;
				}

				if (currentTime < pitIgnitionCompleteTime) {
					return ActiveJobResult.PERFORMING;
				}

				if (getLevel().getTileID(tileX, tileY) != coveredCharcoalPitID) {
					sendBlockedMessage(worker, "charcoalinvalidsite");
					return ActiveJobResult.FAILED;
				}

				long fullDayDuration = (long)getLevel().getWorldEntity().getDayTimeMax() * 1000L;
				long burnEndWorldTime = getLevel().getWorldEntity().getWorldTime() + fullDayDuration;
				pitData.startBurn(tileX, tileY, burnEndWorldTime);
				pitData.clearProductionRecoveryState(tileX, tileY);
				burnStarted = true;

				int burningCharcoalPitID = TileRegistry.getTileID(BurningCharcoalPitTile.stringID);
				getLevel().setTile(tileX, tileY, burningCharcoalPitID);
				getLevel().sendTileUpdatePacket(tileX, tileY);
				getLevel().getLevelTile(tileX, tileY).checkAround();
				getLevel().getLevelObject(tileX, tileY).checkAround();
				getLevel().getServer().network.sendToClientsWithTile(
						new PacketBuilderTilePlaceSound(burningCharcoalPitID, tileX, tileY),
						getLevel(),
						tileX,
						tileY
				);

				CharcoalProductionLevelJob.this.remove();
				return ActiveJobResult.FINISHED;
			}

			private CharcoalPitLevelData pitDataForRecovery() {
				return CharcoalPitLevelData.get(getLevel(), true);
			}

			private boolean hasGrassTile() {
				return getLevel().getTileID(tileX, tileY) == TileRegistry.grassID;
			}
		};
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		CharcoalProductionLevelJob job = (CharcoalProductionLevelJob)foundJob.job;
		if (!job.isValid()) {
			return null;
		}

		if (job.recoveryStage != null && !job.zone.canProduce()) {
			LinkedListJobSequence recoveryCancelSequence = new LinkedListJobSequence(
					new LocalMessage("activities", "charcoalproduction"),
					false
			);
			recoveryCancelSequence.add(job.getActiveJob(worker, foundJob.priority));
			return recoveryCancelSequence;
		}

		List<ActiveJob> inventoryDropOffJobs = new ArrayList<>();
		if (!addCurrentInventoryDropOffJobs(worker, foundJob.priority, inventoryDropOffJobs)) {
			Logging.logMessage("[FiringInventory] Production job waiting because current work inventory cannot be fully deposited: worker="
					+ worker.getMobWorker().getUniqueID());
			return null;
		}

		List<SettlementStoragePickupSlot> logReservations = new ArrayList<>();
		boolean needsLogs = job.recoveryStage != ProductionStage.LOADED
				&& job.recoveryStage != ProductionStage.COVERED;
		if (needsLogs) {
			List<SettlementStoragePickupSlot> reserved = reserveLogs(worker);
			if (reserved == null) {
				cancelPlannedJobs(inventoryDropOffJobs);
				if (getAvailableLogCount(worker) < requiredLogs) {
					sendBlockedMessage(worker, "charcoalmissinglogs");
				} else {
					sendBlockedMessage(worker, "charcoallogsunavailable");
				}
				return null;
			}
			logReservations.addAll(reserved);
		}

		if (!job.isValid()) {
			releaseReservations(logReservations);
			cancelPlannedJobs(inventoryDropOffJobs);
			return null;
		}

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "charcoalproduction"),
				false
		);

		Logging.logMessage("[CharcoalPickup] Building sequence: worker=" + worker.getMobWorker().getUniqueID()
				+ ", inventoryDropOffJobs=" + inventoryDropOffJobs.size()
				+ ", reservations=" + logReservations.size()
				+ ", workLogsBefore=" + countWorkInventoryLogs(worker)
				+ ", pit=" + job.tileX + "," + job.tileY);

		sequence.addAll(inventoryDropOffJobs);

		for (SettlementStoragePickupSlot slot : logReservations) {
			sequence.add(new DebugPickupJob(worker, foundJob.priority, slot));
		}

		sequence.add(job.getActiveJob(worker, foundJob.priority));
		return sequence;
	}


	private static boolean addCurrentInventoryDropOffJobs(
			EntityJobWorker worker,
			JobTypeHandler.TypePriority priority,
			List<ActiveJob> jobs
	) {
		List<InventoryItem> currentItems = new ArrayList<>();

		for (InventoryItem item : worker.getWorkInventory().items()) {
			if (item != null && item.getAmount() > 0) {
				currentItems.add(item.copy());
			}
		}

		if (!currentItems.isEmpty()) {
			Logging.logMessage("[FiringInventory] Planning deposit of " + currentItems.size()
					+ " work-inventory stack(s) before charcoal production for worker="
					+ worker.getMobWorker().getUniqueID());
		}

		for (InventoryItem item : currentItems) {
			ArrayList<HasStorageLevelJob.DropOffFind> dropOffLocations =
					HasStorageLevelJob.findDropOffLocation(worker, item);
			int dropOffCapacity = 0;

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				dropOffCapacity += location.item.getAmount();
			}

			if (dropOffCapacity < item.getAmount()) {
				Logging.logMessage("[FiringInventory] Cannot fully deposit " + item.item.getStringID()
						+ " x" + item.getAmount() + " before charcoal production; capacity=" + dropOffCapacity);
				cancelPlannedJobs(jobs);
				return false;
			}

			for (HasStorageLevelJob.DropOffFind location : dropOffLocations) {
				jobs.add(location.getActiveJob(worker, priority, null, false));
			}
		}

		return true;
	}

	private static void cancelPlannedJobs(List<ActiveJob> jobs) {
		for (ActiveJob job : jobs) {
			job.onCancelled(true, false, false);
		}
		jobs.clear();
	}


	private static class DebugPickupJob extends PickupSettlementStorageActiveJob {
		private boolean loggedStart;
		private Boolean lastMovingState;
		private boolean loggedInvalid;

		DebugPickupJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority, SettlementStoragePickupSlot slot) {
			super(worker, priority, slot, new AtomicReference<>());
		}

		@Override
		public void tick(boolean isCurrent, boolean isMovingTo) {
			super.tick(isCurrent, isMovingTo);

			if (!loggedStart) {
				loggedStart = true;
				Logging.logMessage(
						"[CharcoalPickup] Starting pickup: worker=" + worker.getMobWorker().getUniqueID()
								+ ", item=" + slot.item.item.getStringID()
								+ ", amount=" + slot.item.getAmount()
								+ ", storage=" + slot.storage.tileX + "," + slot.storage.tileY
								+ ", slot=" + slot.slot
								+ ", workLogs=" + countWorkInventoryLogs(worker)
				);
			}

			if (lastMovingState == null || lastMovingState != isMovingTo) {
				lastMovingState = isMovingTo;
				Logging.logMessage(
						"[CharcoalPickup] Movement state: worker=" + worker.getMobWorker().getUniqueID()
								+ ", item=" + slot.item.item.getStringID()
								+ ", storage=" + slot.storage.tileX + "," + slot.storage.tileY
								+ ", isCurrent=" + isCurrent
								+ ", isMovingTo=" + isMovingTo
								+ ", workerTile=" + worker.getMobWorker().getTileX() + "," + worker.getMobWorker().getTileY()
				);
			}
		}

		@Override
		public boolean isValid(boolean isCurrent) {
			boolean valid = super.isValid(isCurrent);
			if (!valid && !loggedInvalid) {
				loggedInvalid = true;
				Logging.logMessage(
						"[CharcoalPickup] Pickup became invalid: worker=" + worker.getMobWorker().getUniqueID()
								+ ", item=" + slot.item.item.getStringID()
								+ ", amount=" + slot.item.getAmount()
								+ ", storage=" + slot.storage.tileX + "," + slot.storage.tileY
								+ ", slot=" + slot.slot
								+ ", slotRemoved=" + slot.isRemoved()
								+ ", workLogs=" + countWorkInventoryLogs(worker)
				);
			}
			return valid;
		}

		@Override
		public ActiveJobResult perform() {
			Logging.logMessage(
					"[CharcoalPickup] perform() before: worker=" + worker.getMobWorker().getUniqueID()
							+ ", item=" + slot.item.item.getStringID()
							+ ", amount=" + slot.item.getAmount()
							+ ", storage=" + slot.storage.tileX + "," + slot.storage.tileY
							+ ", slot=" + slot.slot
							+ ", slotValid=" + slot.isValid()
							+ ", workLogs=" + countWorkInventoryLogs(worker)
			);

			ActiveJobResult result = super.perform();

			Logging.logMessage(
					"[CharcoalPickup] perform() after: worker=" + worker.getMobWorker().getUniqueID()
							+ ", result=" + result
							+ ", picked=" + (pickedUpItemRef.get() == null
									? "null"
									: pickedUpItemRef.get().item.getStringID() + " x" + pickedUpItemRef.get().getAmount())
							+ ", slotRemoved=" + slot.isRemoved()
							+ ", workLogs=" + countWorkInventoryLogs(worker)
			);

			return result;
		}

		@Override
		public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
			Logging.logMessage(
					"[CharcoalPickup] Cancelled: worker=" + worker.getMobWorker().getUniqueID()
							+ ", item=" + slot.item.item.getStringID()
							+ ", amount=" + slot.item.getAmount()
							+ ", storage=" + slot.storage.tileX + "," + slot.storage.tileY
							+ ", becauseOfInvalid=" + becauseOfInvalid
							+ ", isCurrent=" + isCurrent
							+ ", isMovingTo=" + isMovingTo
							+ ", workLogs=" + countWorkInventoryLogs(worker)
			);
			super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
		}
	}

	private static int getAvailableLogCount(EntityJobWorker worker) {
		SettlementStorageRecords records = PickupSettlementStorageActiveJob.getStorageRecords(worker);
		if (records == null) {
			return 0;
		}

		int anyLogID = GlobalIngredientRegistry.getGlobalIngredientID("anylog");
		SettlementStorageGlobalIngredientIDIndex index = records.getIndex(SettlementStorageGlobalIngredientIDIndex.class);
		SettlementStorageRecordsRegionData data = index.getGlobalIngredient(anyLogID);
		return data == null ? 0 : data.getTotalItems();
	}

	private static void sendBlockedMessage(EntityJobWorker worker, String translationKey) {
		if (!(worker.getMobWorker() instanceof SettlerMob)) {
			return;
		}

		SettlerMob settlerMob = (SettlerMob)worker.getMobWorker();
		ServerSettlementData settlement = settlerMob.getSettlerSettlementServerData();
		if (settlement == null) {
			return;
		}

		CharcoalPitLevelData data = CharcoalPitLevelData.get(worker.getMobWorker().getLevel(), true);
		if (!data.shouldSendProductionBlockedReason(translationKey, worker.getMobWorker().getLevel().getTime())) {
			return;
		}

		String message = GameColor.RED.getColorCode() + Localization.translate("jobs", translationKey);
		settlement.networkData.streamTeamMembers().forEach(client -> client.sendChatMessage(message));
		Logging.logMessage(Localization.translate("jobs", translationKey));
	}

	private static List<SettlementStoragePickupSlot> reserveLogs(EntityJobWorker worker) {
		SettlementStorageRecords records = PickupSettlementStorageActiveJob.getStorageRecords(worker);
		if (records == null) {
			return null;
		}

		SettlementStorageItemIDIndex itemIndex = records.getIndex(SettlementStorageItemIDIndex.class);
		Logging.logMessage("[CharcoalPickup] reserveLogs start: worker=" + worker.getMobWorker().getUniqueID()
				+ ", currentlyCarriedLogs=" + countWorkInventoryLogs(worker));
		List<SettlementStoragePickupSlot> reserved = new ArrayList<>();
		int remaining = requiredLogs;

		for (Object value : ItemRegistry.getItems()) {
			Item item = (Item)value;
			if (!item.isGlobalIngredient("anylog")) {
				continue;
			}

			int available = itemIndex.getTotalItems(item);
			if (available <= 0) {
				continue;
			}

			int wanted = Math.min(remaining, available);
			LinkedList<SettlementStoragePickupSlot> slots = itemIndex.findPickupSlots(
					item, worker, null, 1, wanted);
			if (slots == null) {
				Logging.logMessage("[CharcoalPickup] No pickup slots returned for " + item.getStringID()
						+ ": available=" + available + ", wanted=" + wanted + ", remaining=" + remaining);
				continue;
			}

			Logging.logMessage("[CharcoalPickup] Pickup slots found for " + item.getStringID()
					+ ": available=" + available + ", wanted=" + wanted + ", slots=" + slots.size());

			for (SettlementStoragePickupSlot slot : slots) {
				reserved.add(slot);
				remaining -= slot.item.getAmount();
				Logging.logMessage("[CharcoalPickup] Reserved " + slot.item.item.getStringID() + " x" + slot.item.getAmount()
						+ " from storage=" + slot.storage.tileX + "," + slot.storage.tileY
						+ ", slot=" + slot.slot + ", remaining=" + remaining);
				if (remaining <= 0) {
					return reserved;
				}
			}
		}

		Logging.logMessage("[CharcoalPickup] Reservation failed: reservedSlots=" + reserved.size()
				+ ", remaining=" + remaining);
		releaseReservations(reserved);
		return null;
	}

	private static int countWorkInventoryLogs(EntityJobWorker worker) {
		int amount = 0;
		for (Object value : worker.getWorkInventory().items()) {
			InventoryItem item = (InventoryItem)value;
			if (isLog(item)) {
				amount += item.getAmount();
			}
		}
		return amount;
	}

	private static InventoryItem getFirstWorkInventoryLog(EntityJobWorker worker) {
		for (Object value : worker.getWorkInventory().items()) {
			InventoryItem item = (InventoryItem)value;
			if (isLog(item)) {
				return item;
			}
		}
		return null;
	}

	private static List<StoredLog> removeWorkInventoryLogs(EntityJobWorker worker, int amount) {
		if (countWorkInventoryLogs(worker) < amount) {
			return null;
		}

		Map<String, Integer> removedByType = new LinkedHashMap<>();
		int remaining = amount;
		ListIterator iterator = worker.getWorkInventory().listIterator();

		while (iterator.hasNext() && remaining > 0) {
			InventoryItem item = (InventoryItem)iterator.next();
			if (!isLog(item)) {
				continue;
			}

			int take = Math.min(remaining, item.getAmount());
			String itemStringID = item.item.getStringID();
			removedByType.put(itemStringID, removedByType.getOrDefault(itemStringID, 0) + take);
			remaining -= take;

			if (take >= item.getAmount()) {
				iterator.remove();
			} else {
				item.setAmount(item.getAmount() - take);
			}
		}

		if (remaining > 0) {
			return null;
		}

		worker.getWorkInventory().markDirty();
		List<StoredLog> logs = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : removedByType.entrySet()) {
			logs.add(new StoredLog(entry.getKey(), entry.getValue()));
		}
		return logs;
	}

	private static boolean isLog(InventoryItem item) {
		return item != null && item.item.isGlobalIngredient("anylog");
	}

	private static void releaseReservations(List<SettlementStoragePickupSlot> reservations) {
		for (SettlementStoragePickupSlot slot : reservations) {
			if (!slot.isRemoved()) {
				slot.remove();
			}
		}
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof HumanMob)) {
			return null;
		}

		HumanMob human = (HumanMob)worker;
		return handler
				.setJobHandler(CharcoalProductionLevelJob.class, foundJob -> getJobSequence(human, foundJob))
				.setPredicate(
						() -> !human.isOnStrike()
								&& !human.hasCompletedMission()
								&& (!human.isSettler() || human.isSettlerWithinSettlement())
				);
	}
}
