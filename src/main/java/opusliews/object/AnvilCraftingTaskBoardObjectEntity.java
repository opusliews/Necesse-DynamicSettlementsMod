package opusliews.object;

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
import necesse.level.gameObject.container.IronAnvilObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.crafting.AnvilCraftingLogic;
import opusliews.crafting.AnvilCraftingTask;
import opusliews.jobs.AnvilCraftingLevelJob;

public class AnvilCraftingTaskBoardObjectEntity extends ObjectEntity {
	public static final String TYPE = "anvilcraftingtaskboard";
	private static final long STATUS_REFRESH_MS = 500L;

	private Point linkedAnvil;
	private final ArrayList<AnvilCraftingTask> tasks = new ArrayList<>();

	private transient AnvilCraftingLevelJob craftingJob;
	private transient int assignedBlacksmithID = -1;
	private transient int assignedDay = -1;
	private transient int nextTaskIndex;
	private transient long nextStatusRefreshTime;

	public AnvilCraftingTaskBoardObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		if (linkedAnvil != null) {
			SaveData link = new SaveData("linkedAnvil");
			link.addInt("x", linkedAnvil.x);
			link.addInt("y", linkedAnvil.y);
			save.addSaveData(link);
		}

		SaveData tasksData = new SaveData("tasks");
		for (AnvilCraftingTask task : tasks) {
			SaveData taskData = new SaveData("task");
			task.addSaveData(taskData);
			tasksData.addSaveData(taskData);
		}
		save.addSaveData(tasksData);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		LoadData link = save.getFirstLoadDataByName("linkedAnvil");
		linkedAnvil = link == null ? null : new Point(link.getInt("x", 0), link.getInt("y", 0));

		tasks.clear();
		LoadData tasksData = save.getFirstLoadDataByName("tasks");
		if (tasksData != null) {
			for (LoadData taskData : tasksData.getLoadDataByName("task")) {
				AnvilCraftingTask task = new AnvilCraftingTask(taskData);
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
		writer.putNextBoolean(linkedAnvil != null);
		if (linkedAnvil != null) {
			writer.putNextInt(linkedAnvil.x);
			writer.putNextInt(linkedAnvil.y);
		}

		writer.putNextShortUnsigned(tasks.size());
		for (AnvilCraftingTask task : tasks) {
			task.writePacket(writer);
		}
	}

	@Override
	public void applyContentPacket(PacketReader reader) {
		super.applyContentPacket(reader);
		linkedAnvil = reader.getNextBoolean() ? new Point(reader.getNextInt(), reader.getNextInt()) : null;
		tasks.clear();
		int count = reader.getNextShortUnsigned();
		for (int i = 0; i < count; i++) {
			tasks.add(new AnvilCraftingTask(reader));
		}
	}

	@Override
	public void serverTick() {
		super.serverTick();
		validateLinkedAnvil();
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
		releaseBlacksmith();
		super.remove();
	}

	private void validateLinkedAnvil() {
		if (linkedAnvil == null) {
			return;
		}

		LevelObject linkedObject = getLevel().getLevelObject(linkedAnvil.x, linkedAnvil.y);
		if (!(linkedObject.object instanceof IronAnvilObject)) {
			setLinkedAnvilInternal(null, true);
			return;
		}

		ObjectEntity objectEntity = getLevel().entityManager.getObjectEntity(linkedAnvil.x, linkedAnvil.y);
		if (objectEntity instanceof AnvilObjectEntity) {
			Point taskBoard = ((AnvilObjectEntity)objectEntity).getTaskBoard();
			if (taskBoard == null || taskBoard.x != tileX || taskBoard.y != tileY) {
				setLinkedAnvilInternal(null, true);
			}
		}
	}

	private void updateDailyAssignment() {
		if (!getLevel().isServer()) {
			return;
		}

		int day = getLevel().getWorldEntity().getDay();
		if (getLevel().getWorldEntity().isNight()) {
			releaseBlacksmith();
			assignedDay = day;
			return;
		}

		if (assignedDay != -1 && assignedDay != day) {
			releaseBlacksmith();
		}
		assignedDay = day;

		if (assignedBlacksmithID != -1) {
			Mob mob = (Mob)getLevel().entityManager.mobs.get(assignedBlacksmithID, false);
			if (mob == null || mob.removed()) {
				releaseBlacksmith();
			}
		}
	}

	private void ensureCraftingJob() {
		if (!getLevel().isServer()) {
			return;
		}

		if (linkedAnvil == null || tasks.isEmpty()) {
			if (craftingJob != null) {
				craftingJob.remove();
				craftingJob = null;
			}
			if (tasks.isEmpty()) {
				releaseBlacksmith();
			}
			return;
		}

		if (craftingJob != null && !craftingJob.isRemoved()
				&& craftingJob.tileX == linkedAnvil.x && craftingJob.tileY == linkedAnvil.y) {
			return;
		}

		if (craftingJob != null) {
			craftingJob.remove();
		}

		TileLevelJob added = getLevel().jobsLayer.addJob(
				new AnvilCraftingLevelJob(linkedAnvil.x, linkedAnvil.y, tileX, tileY),
				false,
				false
		);
		craftingJob = added instanceof AnvilCraftingLevelJob ? (AnvilCraftingLevelJob)added : null;
	}

	public Point getLinkedAnvil() {
		return linkedAnvil == null ? null : new Point(linkedAnvil);
	}

	public LevelObject getValidLinkedAnvilObject() {
		if (linkedAnvil == null) {
			return null;
		}

		LevelObject linkedObject = getLevel().getLevelObject(linkedAnvil.x, linkedAnvil.y);
		if (!(linkedObject.object instanceof IronAnvilObject)) {
			return null;
		}

		int claimCount = 0;
		for (Object object : getLevel().entityManager.objectEntities) {
			if (object instanceof AnvilObjectEntity) {
				Point board = ((AnvilObjectEntity)object).getTaskBoard();
				if (board != null && board.x == tileX && board.y == tileY) {
					claimCount++;
				}
			}
		}

		ObjectEntity ownerEntity = getLevel().entityManager.getObjectEntity(linkedAnvil.x, linkedAnvil.y);
		if (ownerEntity instanceof AnvilObjectEntity) {
			Point board = ((AnvilObjectEntity)ownerEntity).getTaskBoard();
			if (board == null || board.x != tileX || board.y != tileY) {
				return null;
			}
		}

		return claimCount > 1 ? null : linkedObject;
	}

	public List<AnvilCraftingTask> getTasks() {
		ArrayList<AnvilCraftingTask> copy = new ArrayList<>();
		for (AnvilCraftingTask task : tasks) {
			copy.add(task.copy());
		}
		return copy;
	}

	public int getTaskCount() {
		return tasks.size();
	}

	public AnvilCraftingTask getTask(int index) {
		return index >= 0 && index < tasks.size() ? tasks.get(index) : null;
	}

	public void addTask(int itemID) {
		refreshTaskStatusesNow();
		tasks.add(new AnvilCraftingTask(itemID, AnvilCraftingTask.CONDITION_CRAFT_UNITS, 0));
		resetEvaluation();
	}

	public void removeTask(int index) {
		if (index >= 0 && index < tasks.size()) {
			tasks.remove(index);
			if (nextTaskIndex >= tasks.size()) {
				nextTaskIndex = 0;
			}
			resetEvaluation();
		}
	}

	public void moveTask(int from, int to) {
		if (from < 0 || from >= tasks.size() || to < 0 || to >= tasks.size() || from == to) {
			return;
		}

		AnvilCraftingTask task = tasks.remove(from);
		tasks.add(to, task);
		resetEvaluation();
	}

	public void updateTask(int index, int conditionType, int amount) {
		refreshTaskStatusesNow();
		AnvilCraftingTask task = getTask(index);
		if (task == null) {
			return;
		}

		task.conditionType = conditionType == AnvilCraftingTask.CONDITION_KEEP_STOCKED
				? AnvilCraftingTask.CONDITION_KEEP_STOCKED
				: AnvilCraftingTask.CONDITION_CRAFT_UNITS;
		task.amount = Math.max(0, Math.min(65535, amount));
		resetEvaluation();
	}

	public void setTaskPaused(int index, boolean paused) {
		refreshTaskStatusesNow();
		AnvilCraftingTask task = getTask(index);
		if (task == null || task.paused == paused) {
			return;
		}
		task.paused = paused;
		resetEvaluation();
	}

	public void decrementCraftAmount(int index, int produced) {
		AnvilCraftingTask task = getTask(index);
		if (task == null || task.conditionType != AnvilCraftingTask.CONDITION_CRAFT_UNITS) {
			return;
		}
		task.amount = Math.max(0, task.amount - Math.max(0, produced));
		resetEvaluation();
	}

	public void setLinkedAnvilInternal(Point point, boolean sync) {
		Point old = linkedAnvil == null ? null : new Point(linkedAnvil);
		linkedAnvil = point == null ? null : new Point(point);
		if (old == null ? linkedAnvil != null : !old.equals(linkedAnvil)) {
			releaseBlacksmith();
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

	private boolean isBlacksmithAssignedElsewhere(int uniqueID) {
		for (Object object : getLevel().entityManager.objectEntities) {
			if (object instanceof AnvilCraftingTaskBoardObjectEntity && object != this) {
				AnvilCraftingTaskBoardObjectEntity other = (AnvilCraftingTaskBoardObjectEntity)object;
				if (other.isAssignedTo(uniqueID)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean canBeClaimedBy(int uniqueID) {
		if (assignedBlacksmithID != -1 && assignedBlacksmithID != uniqueID) {
			return false;
		}
		return assignedBlacksmithID == uniqueID || !isBlacksmithAssignedElsewhere(uniqueID);
	}

	public boolean claimBlacksmith(int uniqueID) {
		if (getLevel().getWorldEntity().isNight()) {
			return false;
		}
		if (!canBeClaimedBy(uniqueID)) {
			return false;
		}
		if (assignedBlacksmithID == -1 || assignedBlacksmithID == uniqueID) {
			assignedBlacksmithID = uniqueID;
			assignedDay = getLevel().getWorldEntity().getDay();
			return true;
		}
		return false;
	}

	public boolean isAssignedTo(int uniqueID) {
		return assignedBlacksmithID == uniqueID;
	}

	public boolean isAssigned() {
		return assignedBlacksmithID != -1;
	}

	public void releaseBlacksmith() {
		assignedBlacksmithID = -1;
		nextTaskIndex = 0;
	}

	public int getNextTaskIndex() {
		return nextTaskIndex;
	}

	public void advanceAfterTask(int index) {
		nextTaskIndex = tasks.isEmpty() ? 0 : (index + 1) % tasks.size();
	}

	public boolean hasActionableTasksCached() {
		for (AnvilCraftingTask task : tasks) {
			if (task.status == AnvilCraftingTask.STATUS_IN_PROGRESS) {
				return true;
			}
		}
		return false;
	}

	public boolean hasActionableTasks() {
		refreshTaskStatusesNow();
		for (AnvilCraftingTask task : tasks) {
			if (task.status == AnvilCraftingTask.STATUS_IN_PROGRESS) {
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
			if (tasks.get(index).status == AnvilCraftingTask.STATUS_IN_PROGRESS) {
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
		Point settlementPoint = linkedAnvil == null ? new Point(tileX, tileY) : linkedAnvil;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(getLevel())
				.getServerDataAtTile(getLevel().getIdentifier(), settlementPoint.x, settlementPoint.y);

		for (AnvilCraftingTask task : tasks) {
			int status;
			List<String> details = Collections.emptyList();

			if (task.paused) {
				status = AnvilCraftingTask.STATUS_PAUSED;
			} else if (task.amount <= 0) {
				status = AnvilCraftingTask.STATUS_FINISHED;
			} else if (getValidLinkedAnvilObject() == null) {
				status = AnvilCraftingTask.STATUS_PROBLEM;
				details = Collections.singletonList("Board is not linked to a valid anvil");
			} else if (task.conditionType == AnvilCraftingTask.CONDITION_KEEP_STOCKED
					&& AnvilCraftingLogic.countSettlementStock(this, settlement, task.itemID) >= task.amount) {
				status = AnvilCraftingTask.STATUS_FINISHED;
			} else {
				necesse.inventory.recipe.Recipe recipe = AnvilCraftingLogic.getRecipe(this, task.itemID);
				if (recipe == null) {
					status = AnvilCraftingTask.STATUS_PROBLEM;
					details = Collections.singletonList("Recipe is not available at the linked anvil");
				} else {
					List<String> missing = AnvilCraftingLogic.getMissingIngredients(
							getLevel(),
							AnvilCraftingLogic.getInputRange(this),
							recipe
					);
					if (!missing.isEmpty()) {
						status = AnvilCraftingTask.STATUS_PROBLEM;
						if (missing.size() == 1 && missing.get(0).equals("Input storage is not linked")) {
							details = missing;
						} else {
							details = Collections.singletonList("Missing ingredients: " + String.join(", ", missing));
						}
					} else if (!AnvilCraftingLogic.canFitResult(
							getLevel(),
							AnvilCraftingLogic.getOutputRange(this),
							recipe.resultItem.copy(recipe.resultAmount)
					)) {
						status = AnvilCraftingTask.STATUS_PROBLEM;
						details = Collections.singletonList(
								AnvilCraftingLogic.getOutputRange(this) == null
										? "Output storage is not linked"
										: "Output storage is full"
						);
					} else {
						status = AnvilCraftingTask.STATUS_IN_PROGRESS;
					}
				}
			}

			changed |= task.setRuntimeStatus(status, details);
		}

		boolean anyActionable = false;
		for (AnvilCraftingTask task : tasks) {
			if (task.status == AnvilCraftingTask.STATUS_IN_PROGRESS) {
				anyActionable = true;
				break;
			}
		}
		if (!anyActionable && assignedBlacksmithID != -1) {
			releaseBlacksmith();
		}

		if (changed) {
			markDirty();
			syncContent();
		}
	}

	private void resetEvaluation() {
		markDirty();
		nextStatusRefreshTime = 0L;
		refreshTaskStatuses(true);
	}

	private void syncContent() {
		if (getLevel().isServer() && getLevel().getServer() != null) {
			getLevel().getServer().network.sendToClientsWithEntity(new PacketObjectEntity(this), this);
		}
	}
}
