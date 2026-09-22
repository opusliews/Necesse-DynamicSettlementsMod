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
import opusliews.crafting.CraftingTaskRecipe;
import opusliews.forge.ForgeTaskSystem;
import opusliews.forge.ForgeRequirementSystem;
import opusliews.crafting.CraftingTask;
import opusliews.jobs.CraftingStationLevelJob;
import opusliews.logging.Logging;

public class CraftingTaskBoardObjectEntity extends ObjectEntity {
	public static final String TYPE = "craftingtaskboard";
	private static final long STATUS_REFRESH_MS = 500L;

	private Point linkedStation;
	private final ArrayList<CraftingTask> tasks = new ArrayList<>();
	private final ArrayList<ForgeAssignment> forgeAssignments = new ArrayList<>();
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

		SaveData forgeAssignmentsData = new SaveData("forgeAssignments");
		for (ForgeAssignment assignment : forgeAssignments) {
			SaveData assignmentData = new SaveData("assignment");
			assignment.addSaveData(assignmentData);
			forgeAssignmentsData.addSaveData(assignmentData);
		}
		save.addSaveData(forgeAssignmentsData);
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
		forgeAssignments.clear();
		LoadData forgeAssignmentsData = save.getFirstLoadDataByName("forgeAssignments");
		if (forgeAssignmentsData != null) {
			for (LoadData assignmentData : forgeAssignmentsData.getLoadDataByName("assignment")) {
				forgeAssignments.add(new ForgeAssignment(assignmentData));
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
		processForgeAssignments();
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

	public void addForgeAssignment(Point forgePoint, int outputItemID, int outputAmount) {
		if (forgePoint == null || outputItemID < 0 || outputAmount <= 0) return;
		forgeAssignments.add(new ForgeAssignment(forgePoint.x, forgePoint.y, outputItemID, outputAmount));
		markDirty();
	}

	public boolean hasForgeAssignment(Point forgePoint) {
		if (forgePoint == null) return false;
		for (ForgeAssignment assignment : forgeAssignments) {
			if (assignment.forgeX == forgePoint.x && assignment.forgeY == forgePoint.y) return true;
		}
		return false;
	}

	public int getPendingForgeAmount(int itemID) {
		int total = 0;
		for (ForgeAssignment assignment : forgeAssignments) {
			if (assignment.outputItemID == itemID) total += assignment.outputAmount;
		}
		return total;
	}

	private void processForgeAssignments() {
		if (!getLevel().isServer() || forgeAssignments.isEmpty()) return;
		boolean changed = false;
		for (int i = forgeAssignments.size() - 1; i >= 0; i--) {
			ForgeAssignment assignment = forgeAssignments.get(i);
			Point forgePoint = new Point(assignment.forgeX, assignment.forgeY);
			if (ForgeTaskSystem.collectAssignment(this, forgePoint, assignment.outputItemID, assignment.outputAmount)) {
				forgeAssignments.remove(i);
				onForgeAssignmentCompleted(assignment.outputItemID, assignment.outputAmount);
				changed = true;
				continue;
			}
			if (!ForgeTaskSystem.isAssignmentStillProcessing(this, forgePoint)) {
				forgeAssignments.remove(i);
				Logging.logMessage("[CraftingForgeJob] Dropped interrupted forge assignment outputItem="
						+ assignment.outputItemID + " forge=" + assignment.forgeX + "," + assignment.forgeY);
				changed = true;
			}
		}
		if (changed) {
			markDirty();
			nextStatusRefreshTime = 0L;
		}
	}

	private void onForgeAssignmentCompleted(int itemID, int producedAmount) {
		for (int i = 0; i < tasks.size(); i++) {
			CraftingTask task = tasks.get(i);
			if (task.itemID != itemID || task.sourceType != CraftingTask.SOURCE_FORGE || task.paused || task.amount <= 0) continue;
			if (task.conditionType == CraftingTask.CONDITION_CRAFT_UNITS) {
				task.amount = Math.max(0, task.amount - Math.max(0, producedAmount));
			}
			break;
		}
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

	public boolean addTask(int expectedRevision, int itemID, int sourceType) {
		if (expectedRevision != taskRevision) return false;
		refreshTaskStatusesNow();
		tasks.add(new CraftingTask(itemID, sourceType, CraftingTask.CONDITION_CRAFT_UNITS, 0));
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
		for (int i = 0; i < tasks.size(); i++) {
			if (isTaskActionableNow(i)) return true;
		}
		return false;
	}

	public boolean hasActionableTasks() {
		refreshTaskStatusesNow();
		for (int i = 0; i < tasks.size(); i++) {
			if (isTaskActionableNow(i)) return true;
		}
		return false;
	}

	public int findNextActionableTask() {
		refreshTaskStatusesNow();
		if (tasks.isEmpty()) return -1;

		for (int offset = 0; offset < tasks.size(); offset++) {
			int index = (nextTaskIndex + offset) % tasks.size();
			if (isTaskActionableNow(index)) return index;
		}
		return -1;
	}

	private boolean isTaskActionableNow(int index) {
		CraftingTask task = getTask(index);
		if (task == null || task.status != CraftingTask.STATUS_IN_PROGRESS) return false;
		CraftingTaskRecipe taskRecipe = CraftingTaskLogic.getTaskRecipe(this, task);
		if (taskRecipe == null || !taskRecipe.isForgeRecipe()) return true;
		if (task.conditionType == CraftingTask.CONDITION_CRAFT_UNITS
				&& getPendingForgeAmount(task.itemID) >= task.amount) return false;
		return ForgeTaskSystem.getMissingIngredients(this, taskRecipe).isEmpty()
				&& ForgeTaskSystem.hasAvailableForge(this, taskRecipe)
				&& ForgeTaskSystem.hasFuelAvailable(this, taskRecipe)
				&& CraftingTaskLogic.canFitResult(this, taskRecipe);
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
				CraftingTaskRecipe taskRecipe = CraftingTaskLogic.getTaskRecipe(this, task);
				if (taskRecipe == null) {
					status = CraftingTask.STATUS_PROBLEM;
					details = Collections.singletonList(Localization.translate("ui", "craftingrecipeunavailable"));
				} else {
					String stationProblem = taskRecipe.isForgeRecipe()
							? null
							: CraftingTaskLogic.getStationCraftingProblem(this, taskRecipe.recipe);
					if (stationProblem != null) {
						status = CraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(stationProblem);
					} else if (!taskRecipe.isForgeRecipe()
							&& ForgeRequirementSystem.requiresRunningForge(taskRecipe.recipe)
							&& ForgeRequirementSystem.getStatus(
									getLinkedStationEntity(),
									taskRecipe.recipe,
									CraftingTaskLogic.getStoragePool(this)
							) == ForgeRequirementSystem.Status.NO_LINKED_FORGE) {
						status = CraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(Localization.translate("ui", "craftingrequireslinkedforge"));
					} else if (!taskRecipe.isForgeRecipe()
							&& ForgeRequirementSystem.requiresRunningForge(taskRecipe.recipe)
							&& ForgeRequirementSystem.getStatus(
									getLinkedStationEntity(),
									taskRecipe.recipe,
									CraftingTaskLogic.getStoragePool(this)
							) == ForgeRequirementSystem.Status.NO_FUEL) {
						status = CraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(Localization.translate("ui", "statusmissingforgefuel"));
					} else if (taskRecipe.isForgeRecipe()
							&& (getLinkedStationEntity() == null || getLinkedStationEntity().getValidLinkedForges().isEmpty())) {
						status = CraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(Localization.translate("ui", "craftingrequireslinkedforge"));
					} else {
						List<String> missing = CraftingTaskLogic.getMissingIngredients(this, taskRecipe);
						int pending = taskRecipe.isForgeRecipe() ? getPendingForgeAmount(task.itemID) : 0;
						boolean pendingCoversCraftUnits = task.conditionType == CraftingTask.CONDITION_CRAFT_UNITS
								&& pending >= task.amount;
						if (!pendingCoversCraftUnits && !missing.isEmpty()) {
							status = CraftingTask.STATUS_PROBLEM;
							if (missing.size() == 1 && missing.get(0).equals(Localization.translate("ui", "craftinginputnotlinked"))) {
								details = missing;
							} else {
								details = Collections.singletonList(Localization.translate("ui", "craftingmissingingredientslist", "ingredients", String.join(", ", missing)));
							}
						} else if (!pendingCoversCraftUnits && taskRecipe.isForgeRecipe()
								&& ForgeTaskSystem.hasAvailableForge(this, taskRecipe)
								&& !ForgeTaskSystem.hasFuelAvailable(this, taskRecipe)) {
							status = CraftingTask.STATUS_PROBLEM;
							details = Collections.singletonList(Localization.translate("ui", "statusmissingforgefuel"));
						} else if (!pendingCoversCraftUnits && !CraftingTaskLogic.canFitResult(this, taskRecipe)) {
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
	private static final class ForgeAssignment {
		private final int forgeX;
		private final int forgeY;
		private final int outputItemID;
		private final int outputAmount;

		private ForgeAssignment(int forgeX, int forgeY, int outputItemID, int outputAmount) {
			this.forgeX = forgeX;
			this.forgeY = forgeY;
			this.outputItemID = outputItemID;
			this.outputAmount = Math.max(1, outputAmount);
		}

		private ForgeAssignment(LoadData data) {
			this(data.getInt("forgeX", 0), data.getInt("forgeY", 0), data.getInt("outputItemID", -1), data.getInt("outputAmount", 1));
		}

		private void addSaveData(SaveData data) {
			data.addInt("forgeX", forgeX);
			data.addInt("forgeY", forgeY);
			data.addInt("outputItemID", outputItemID);
			data.addInt("outputAmount", outputAmount);
		}
	}

}
