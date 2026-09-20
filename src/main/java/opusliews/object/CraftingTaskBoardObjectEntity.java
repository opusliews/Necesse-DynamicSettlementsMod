package opusliews.object;

import necesse.engine.localization.Localization;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketObjectEntity;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.Mob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.crafting.CraftingTaskLogic;
import opusliews.crafting.CraftingTask;
import opusliews.jobs.CraftingStationLevelJob;
import opusliews.logging.Logging;

public class CraftingTaskBoardObjectEntity extends ObjectEntity {
	public static final String TYPE = "craftingtaskboard";
	private static final long STATUS_REFRESH_MS = 500L;

	private Point linkedStation;
	private final ArrayList<CraftingTask> tasks = new ArrayList<>();
	private int taskRevision;

	private transient CraftingStationLevelJob craftingJob;
	private transient int assignedWorkerID = -1;
	private transient int assignedDay = -1;
	private transient int nextTaskIndex;
	private transient long nextStatusRefreshTime;

	public CraftingTaskBoardObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		if (linkedStation != null) {
			SaveData link = new SaveData("linkedStation");
			link.addInt("x", linkedStation.x);
			link.addInt("y", linkedStation.y);
			save.addSaveData(link);
		}

		SaveData tasksData = new SaveData("tasks");
		for (CraftingTask task : tasks) {
			SaveData taskData = new SaveData("task");
			task.addSaveData(taskData);
			tasksData.addSaveData(taskData);
		}
		save.addSaveData(tasksData);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		LoadData link = save.getFirstLoadDataByName("linkedStation");
		linkedStation = link == null ? null : new Point(link.getInt("x", 0), link.getInt("y", 0));

		tasks.clear();
		LoadData tasksData = save.getFirstLoadDataByName("tasks");
		if (tasksData != null) {
			for (LoadData taskData : tasksData.getLoadDataByName("task")) {
				CraftingTask task = new CraftingTask(taskData);
				if (task.itemID >= 0) {
					tasks.add(task);
				}
			}
		}
		nextStatusRefreshTime = 0L;
	}

	@Override
	public void setupContentPacket(PacketWriter writer) {
		super.setupContentPacket(writer);
		writer.putNextBoolean(linkedStation != null);
		if (linkedStation != null) {
			writer.putNextInt(linkedStation.x);
			writer.putNextInt(linkedStation.y);
		}

		writer.putNextInt(taskRevision);
		writer.putNextShortUnsigned(tasks.size());
		for (CraftingTask task : tasks) {
			task.writePacket(writer);
		}
	}

	@Override
	public void applyContentPacket(PacketReader reader) {
		super.applyContentPacket(reader);
		linkedStation = reader.getNextBoolean() ? new Point(reader.getNextInt(), reader.getNextInt()) : null;
		taskRevision = reader.getNextInt();
		tasks.clear();
		int count = reader.getNextShortUnsigned();
		for (int i = 0; i < count; i++) {
			tasks.add(new CraftingTask(reader));
		}
	}

	@Override
	public void serverTick() {
		super.serverTick();
		validateLinkedStation();
		updateDailyAssignment();
		ensureCraftingJob();

		long now = getLevel().getTime();
		if (now >= nextStatusRefreshTime) {
			nextStatusRefreshTime = now + STATUS_REFRESH_MS;
			refreshTaskStatuses(false);
		}
	}

	@Override
	public void remove() {
		if (craftingJob != null) {
			craftingJob.remove();
			craftingJob = null;
		}
		releaseWorker();
		super.remove();
	}

	private void validateLinkedStation() {
		if (linkedStation == null) return;

		ObjectEntity objectEntity = getLevel().entityManager.getObjectEntity(linkedStation.x, linkedStation.y);
		if (!(objectEntity instanceof DynamicCraftingStationObjectEntity)) {
			setLinkedStationInternal(null, true);
			return;
		}

		Point taskBoard = ((DynamicCraftingStationObjectEntity)objectEntity).getTaskBoard();
		if (taskBoard == null || taskBoard.x != tileX || taskBoard.y != tileY) {
			setLinkedStationInternal(null, true);
		}
	}

	private void updateDailyAssignment() {
		if (!getLevel().isServer()) {
			return;
		}

		int day = getLevel().getWorldEntity().getDay();
		if (getLevel().getWorldEntity().isNight()) {
			releaseWorker();
			assignedDay = day;
			return;
		}

		if (assignedDay != -1 && assignedDay != day) {
			releaseWorker();
		}
		assignedDay = day;

		if (assignedWorkerID != -1) {
			Mob mob = (Mob)getLevel().entityManager.mobs.get(assignedWorkerID, false);
			if (mob == null || mob.removed()) {
				releaseWorker();
			}
		}
	}

	private void ensureCraftingJob() {
		if (!getLevel().isServer()) return;

		DynamicCraftingStationObjectEntity stationEntity = getLinkedStationEntity();
		if (stationEntity == null || !stationEntity.supportsSettlerCraftingTasks()) {
			if (craftingJob != null) {
				craftingJob.remove();
				craftingJob = null;
			}
			releaseWorker();
			return;
		}

		if (linkedStation == null || tasks.isEmpty()) {
			if (craftingJob != null) {
				Logging.logMessage("[CraftingJob] Board " + tileX + "," + tileY
						+ " removing job: " + (linkedStation == null ? "no linked station" : "no tasks"));
				craftingJob.remove();
				craftingJob = null;
			}
			if (tasks.isEmpty()) releaseWorker();
			return;
		}

		if (craftingJob != null && !craftingJob.isRemoved()
				&& craftingJob.tileX == linkedStation.x && craftingJob.tileY == linkedStation.y) {
			return;
		}

		if (craftingJob != null) {
			Logging.logMessage("[CraftingJob] Board " + tileX + "," + tileY + " replacing stale/moved job");
			craftingJob.remove();
		}

		Logging.logMessage("[CraftingJob] Board " + tileX + "," + tileY + " adding job at station "
				+ linkedStation.x + "," + linkedStation.y + " tasks=" + tasks.size());
		CraftingStationLevelJob requested = new CraftingStationLevelJob(linkedStation.x, linkedStation.y, tileX, tileY);
		TileLevelJob added = getLevel().jobsLayer.addJob(requested, false, false);
		craftingJob = added instanceof CraftingStationLevelJob ? (CraftingStationLevelJob)added : null;
		Logging.logMessage("[CraftingJob] Board " + tileX + "," + tileY + " addJob result="
				+ (added == null ? "null" : added.getClass().getSimpleName())
				+ " valid=" + (craftingJob != null && craftingJob.isValid()));
	}

	public Point getLinkedStation() {
		return linkedStation == null ? null : new Point(linkedStation);
	}

	public DynamicCraftingStationObjectEntity getLinkedStationEntity() {
		if (linkedStation == null) return null;
		ObjectEntity entity = getLevel().entityManager.getObjectEntity(linkedStation.x, linkedStation.y);
		if (!(entity instanceof DynamicCraftingStationObjectEntity)) return null;

		DynamicCraftingStationObjectEntity station = (DynamicCraftingStationObjectEntity)entity;
		Point board = station.getTaskBoard();
		if (board == null || board.x != tileX || board.y != tileY) return null;
		return station;
	}

	public LevelObject getValidLinkedStationObject() {
		DynamicCraftingStationObjectEntity station = getLinkedStationEntity();
		if (station == null) return null;

		int claimCount = 0;
		for (Object object : getLevel().entityManager.objectEntities) {
			if (!(object instanceof DynamicCraftingStationObjectEntity)) continue;
			Point board = ((DynamicCraftingStationObjectEntity)object).getTaskBoard();
			if (board != null && board.x == tileX && board.y == tileY) claimCount++;
		}
		return claimCount > 1 ? null : getLevel().getLevelObject(linkedStation.x, linkedStation.y);
	}

	public String getLinkedStationTextureKey() {
		DynamicCraftingStationObjectEntity station = getLinkedStationEntity();
		return station == null ? "unlinked" : station.getTaskBoardTextureKey();
	}

	public boolean supportsSettlerCraftingTasks() {
		DynamicCraftingStationObjectEntity station = getLinkedStationEntity();
		return station != null && station.supportsSettlerCraftingTasks();
	}

	public List<CraftingTask> getTasks() {
		ArrayList<CraftingTask> copy = new ArrayList<>();
		for (CraftingTask task : tasks) {
			copy.add(task.copy());
		}
		return copy;
	}

	public int getTaskCount() {
		return tasks.size();
	}

	public CraftingTask getTask(int index) {
		return index >= 0 && index < tasks.size() ? tasks.get(index) : null;
	}

	public int getTaskRevision() {
		return taskRevision;
	}

	public boolean addTask(int expectedRevision, int itemID) {
		if (expectedRevision != taskRevision) return false;
		refreshTaskStatusesNow();
		tasks.add(new CraftingTask(itemID, CraftingTask.CONDITION_CRAFT_UNITS, 0));
		commitTaskEdit();
		return true;
	}

	public boolean removeTask(int expectedRevision, int index) {
		if (expectedRevision != taskRevision || index < 0 || index >= tasks.size()) return false;
		tasks.remove(index);
		if (nextTaskIndex >= tasks.size()) nextTaskIndex = 0;
		commitTaskEdit();
		return true;
	}

	public boolean moveTask(int expectedRevision, int from, int to) {
		if (expectedRevision != taskRevision || from < 0 || from >= tasks.size() || to < 0 || to >= tasks.size() || from == to) {
			return false;
		}

		CraftingTask task = tasks.remove(from);
		tasks.add(to, task);
		commitTaskEdit();
		return true;
	}

	public boolean updateTask(int expectedRevision, int index, int conditionType, int amount) {
		if (expectedRevision != taskRevision) return false;
		refreshTaskStatusesNow();
		CraftingTask task = getTask(index);
		if (task == null) return false;

		task.conditionType = conditionType == CraftingTask.CONDITION_KEEP_STOCKED
				? CraftingTask.CONDITION_KEEP_STOCKED
				: CraftingTask.CONDITION_CRAFT_UNITS;
		task.amount = Math.max(0, Math.min(65535, amount));
		commitTaskEdit();
		return true;
	}

	public boolean setTaskPaused(int expectedRevision, int index, boolean paused) {
		if (expectedRevision != taskRevision) return false;
		refreshTaskStatusesNow();
		CraftingTask task = getTask(index);
		if (task == null || task.paused == paused) return false;
		task.paused = paused;
		commitTaskEdit();
		return true;
	}

	public void decrementCraftAmount(int index, int produced) {
		CraftingTask task = getTask(index);
		if (task == null || task.conditionType != CraftingTask.CONDITION_CRAFT_UNITS) return;
		task.amount = Math.max(0, task.amount - Math.max(0, produced));
		commitTaskEdit();
	}

	public void setLinkedStationInternal(Point point, boolean sync) {
		Point old = linkedStation == null ? null : new Point(linkedStation);
		linkedStation = point == null ? null : new Point(point);
		if (old == null ? linkedStation != null : !old.equals(linkedStation)) {
			releaseWorker();
			if (craftingJob != null) {
				craftingJob.remove();
				craftingJob = null;
			}
		}
		markDirty();
		nextStatusRefreshTime = 0L;
		if (sync) {
			syncContent();
		}
	}

	private boolean isWorkerAssignedElsewhere(int uniqueID) {
		for (Object object : getLevel().entityManager.objectEntities) {
			if (object instanceof CraftingTaskBoardObjectEntity && object != this) {
				CraftingTaskBoardObjectEntity other = (CraftingTaskBoardObjectEntity)object;
				if (other.isAssignedTo(uniqueID)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean canBeClaimedBy(int uniqueID) {
		if (assignedWorkerID != -1 && assignedWorkerID != uniqueID) {
			return false;
		}
		return assignedWorkerID == uniqueID || !isWorkerAssignedElsewhere(uniqueID);
	}

	public boolean claimWorker(int uniqueID) {
		if (getLevel().getWorldEntity().isNight()) {
			return false;
		}
		if (!canBeClaimedBy(uniqueID)) {
			return false;
		}
		if (assignedWorkerID == -1 || assignedWorkerID == uniqueID) {
			assignedWorkerID = uniqueID;
			assignedDay = getLevel().getWorldEntity().getDay();
			return true;
		}
		return false;
	}

	public boolean isAssignedTo(int uniqueID) {
		return assignedWorkerID == uniqueID;
	}

	public boolean isAssigned() {
		return assignedWorkerID != -1;
	}

	public void releaseWorker() {
		assignedWorkerID = -1;
		nextTaskIndex = 0;
	}

	public int getNextTaskIndex() {
		return nextTaskIndex;
	}

	public void advanceAfterTask(int index) {
		nextTaskIndex = tasks.isEmpty() ? 0 : (index + 1) % tasks.size();
	}

	public boolean hasActionableTasksCached() {
		for (CraftingTask task : tasks) {
			if (task.status == CraftingTask.STATUS_IN_PROGRESS) {
				return true;
			}
		}
		return false;
	}

	public boolean hasActionableTasks() {
		refreshTaskStatusesNow();
		for (CraftingTask task : tasks) {
			if (task.status == CraftingTask.STATUS_IN_PROGRESS) {
				return true;
			}
		}
		return false;
	}

	public int findNextActionableTask() {
		refreshTaskStatusesNow();
		if (tasks.isEmpty()) {
			return -1;
		}

		for (int offset = 0; offset < tasks.size(); offset++) {
			int index = (nextTaskIndex + offset) % tasks.size();
			if (tasks.get(index).status == CraftingTask.STATUS_IN_PROGRESS) {
				return index;
			}
		}
		return -1;
	}

	public void refreshTaskStatusesNow() {
		refreshTaskStatuses(true);
		nextStatusRefreshTime = getLevel().getTime() + STATUS_REFRESH_MS;
	}

	private void refreshTaskStatuses(boolean syncImmediately) {
		if (!getLevel().isServer()) {
			return;
		}

		boolean changed = false;
		Point settlementPoint = linkedStation == null ? new Point(tileX, tileY) : linkedStation;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(getLevel())
				.getServerDataAtTile(getLevel().getIdentifier(), settlementPoint.x, settlementPoint.y);

		for (CraftingTask task : tasks) {
			int status;
			List<String> details = Collections.emptyList();

			if (task.paused) {
				status = CraftingTask.STATUS_PAUSED;
			} else if (task.amount <= 0) {
				status = CraftingTask.STATUS_FINISHED;
			} else if (getValidLinkedStationObject() == null) {
				status = CraftingTask.STATUS_PROBLEM;
				details = Collections.singletonList(Localization.translate("ui", "craftingboardinvalidstation"));
			} else if (task.conditionType == CraftingTask.CONDITION_KEEP_STOCKED
					&& CraftingTaskLogic.countSettlementStock(this, settlement, task.itemID) >= task.amount) {
				status = CraftingTask.STATUS_FINISHED;
			} else {
				necesse.inventory.recipe.Recipe recipe = CraftingTaskLogic.getRecipe(this, task.itemID);
				if (recipe == null) {
					status = CraftingTask.STATUS_PROBLEM;
					details = Collections.singletonList(Localization.translate("ui", "craftingrecipeunavailable"));
				} else {
					String stationProblem = CraftingTaskLogic.getStationCraftingProblem(this, recipe);
					if (stationProblem != null) {
						status = CraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(stationProblem);
						if (task.setRuntimeStatus(status, details)) {
							changed = true;
						}
						continue;
					}
					List<String> missing = CraftingTaskLogic.getMissingIngredients(this, recipe);
					if (!missing.isEmpty()) {
						status = CraftingTask.STATUS_PROBLEM;
						if (missing.size() == 1 && missing.get(0).equals(Localization.translate("ui", "craftinginputnotlinked"))) {
							details = missing;
						} else {
							details = Collections.singletonList(Localization.translate("ui", "craftingmissingingredientslist", "ingredients", String.join(", ", missing)));
						}
					} else if (!CraftingTaskLogic.canFitResult(
							this,
							recipe,
							recipe.resultItem.copy(recipe.resultAmount)
					)) {
						status = CraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(
								CraftingTaskLogic.hasOutputStorage(this)
										? Localization.translate("ui", "craftingoutputfull")
										: Localization.translate("ui", "craftingoutputnotlinked")
						);
					} else {
						status = CraftingTask.STATUS_IN_PROGRESS;
					}
				}
			}

			int previousStatus = task.status;
			if (task.setRuntimeStatus(status, details)) {
				changed = true;
				Logging.logMessage("[CraftingJob] Board " + tileX + "," + tileY + " task item=" + task.itemID
						+ " status " + previousStatus + " -> " + status
						+ (details.isEmpty() ? "" : " details=" + String.join(" | ", details)));
			}
		}

		boolean anyActionable = false;
		for (CraftingTask task : tasks) {
			if (task.status == CraftingTask.STATUS_IN_PROGRESS) {
				anyActionable = true;
				break;
			}
		}
		if (!anyActionable && assignedWorkerID != -1) {
			releaseWorker();
		}

		if (changed) {
			markDirty();
			syncContent();
		}
	}

	private void commitTaskEdit() {
		taskRevision++;
		markDirty();
		nextStatusRefreshTime = 0L;
		refreshTaskStatuses(false);
		syncContent();
	}

	public void syncContentNow() {
		syncContent();
	}

	private void syncContent() {
		if (getLevel().isServer() && getLevel().getServer() != null) {
			getLevel().getServer().network.sendToClientsWithEntity(new PacketObjectEntity(this), this);
		}
	}
}
