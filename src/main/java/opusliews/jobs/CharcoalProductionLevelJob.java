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
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.PickupSettlementStorageActiveJob;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
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
import opusliews.logging.Logging;
import opusliews.network.PacketBuilderTilePlaceSound;
import opusliews.tile.CharcoalPitLevelData;
import opusliews.tile.CharcoalPitLevelData.StoredLog;
import opusliews.tile.CharcoalPitTile;
import opusliews.tile.ShallowHoleTile;

public class CharcoalProductionLevelJob extends TileLevelJob {
	public static final int requiredLogs = 32;
	private static final long grassRemovalTime = 2000L;
	private static final long holeDigTime = 2000L;
	private static final long logLoadingTime = 2000L;

	private final CharcoalProductionZone zone;

	public CharcoalProductionLevelJob(int tileX, int tileY, CharcoalProductionZone zone) {
		super(tileX, tileY);
		this.zone = zone;
	}

	public CharcoalProductionLevelJob(LoadData save) {
		super(save);
		this.zone = null;
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	@Override
	public boolean isValid() {
		return super.isValid()
				&& zone != null
				&& !zone.isRemoved()
				&& zone.containsTile(tileX, tileY)
				&& zone.canProduce()
				&& CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, this);
	}

	private TileActiveJob getActiveJob(EntityJobWorker worker, JobTypeHandler.TypePriority priority) {
		return new TileActiveJob(worker, priority, tileX, tileY) {
			private boolean grassRemovalStarted;
			private long grassRemovalCompleteTime;
			private boolean holeDigStarted;
			private long holeDigCompleteTime;
			private boolean logLoadingStarted;
			private long logLoadingCompleteTime;
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

				if (logLoadingStarted) {
					InventoryItem log = getFirstWorkInventoryLog(worker);
					if (log != null) {
						worker.showWorkAnimation(tileX * 32 + 16, tileY * 32 + 16, log.item, 1000, true);
					}
				} else if (hasGrassTile() || holeDigStarted) {
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
				if (zone == null
						|| zone.isRemoved()
						|| !zone.containsTile(tileX, tileY)
						|| !zone.canProduce()) {
					return false;
				}

				if (isCurrent && countWorkInventoryLogs(worker) < requiredLogs) {
					sendBlockedMessage(worker, "charcoalworkerlogsmissing");
					return false;
				}

				int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
				boolean ownsDugPit = holeDigStarted && getLevel().getTileID(tileX, tileY) == shallowHoleID;
				if (ownsDugPit) {
					return true;
				}

				if (CharcoalProductionLevelJob.this.isRemoved()
						|| !CharcoalProductionLevelJob.this.reservable.isAvailable(worker.getMobWorker())) {
					return false;
				}

				return CharcoalProductionZone.isValidCandidate(getLevel(), tileX, tileY, CharcoalProductionLevelJob.this);
			}

			@Override
			public ActiveJobResult perform() {
				long currentTime = getLevel().getTime();

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
				if (getLevel().getTileID(tileX, tileY) != shallowHoleID) {
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
				int charcoalPitID = TileRegistry.getTileID(CharcoalPitTile.stringID);
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

				CharcoalProductionLevelJob.this.remove();
				return ActiveJobResult.FINISHED;
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

		List<SettlementStoragePickupSlot> logReservations = reserveLogs(worker);
		if (logReservations == null) {
			if (getAvailableLogCount(worker) < requiredLogs) {
				sendBlockedMessage(worker, "charcoalmissinglogs");
			} else {
				sendBlockedMessage(worker, "charcoallogsunavailable");
			}
			return null;
		}

		if (!job.isValid()) {
			releaseReservations(logReservations);
			return null;
		}

		LinkedListJobSequence sequence = new LinkedListJobSequence(
				new LocalMessage("activities", "charcoalproduction"),
				false
		);

		Logging.logMessage("[CharcoalPickup] Building sequence: worker=" + worker.getMobWorker().getUniqueID()
				+ ", reservations=" + logReservations.size()
				+ ", workLogsBefore=" + countWorkInventoryLogs(worker)
				+ ", pit=" + job.tileX + "," + job.tileY);

		for (SettlementStoragePickupSlot slot : logReservations) {
			sequence.add(new DebugPickupJob(worker, foundJob.priority, slot));
		}

		sequence.add(job.getActiveJob(worker, foundJob.priority));
		return sequence;
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
