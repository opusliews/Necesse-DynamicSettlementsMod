package opusliews.object;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketObjectEntity;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.level.gameObject.container.IronAnvilObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import opusliews.crafting.AnvilCraftingTask;

public class AnvilCraftingTaskBoardObjectEntity extends ObjectEntity {
	public static final String TYPE = "anvilcraftingtaskboard";

	private Point linkedAnvil;
	private final ArrayList<AnvilCraftingTask> tasks = new ArrayList<>();

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
		tasks.add(new AnvilCraftingTask(itemID, AnvilCraftingTask.CONDITION_CRAFT_UNITS, 0));
		markChanged();
	}

	public void removeTask(int index) {
		if (index >= 0 && index < tasks.size()) {
			tasks.remove(index);
			markChanged();
		}
	}

	public void moveTask(int from, int to) {
		if (from < 0 || from >= tasks.size() || to < 0 || to >= tasks.size() || from == to) {
			return;
		}

		AnvilCraftingTask task = tasks.remove(from);
		tasks.add(to, task);
		markChanged();
	}

	public void updateTask(int index, int conditionType, int amount) {
		AnvilCraftingTask task = getTask(index);
		if (task == null) {
			return;
		}

		task.conditionType = conditionType == AnvilCraftingTask.CONDITION_KEEP_STOCKED
				? AnvilCraftingTask.CONDITION_KEEP_STOCKED
				: AnvilCraftingTask.CONDITION_CRAFT_UNITS;
		task.amount = Math.max(0, Math.min(65535, amount));
		markChanged();
	}

	public void setLinkedAnvilInternal(Point point, boolean sync) {
		linkedAnvil = point == null ? null : new Point(point);
		markDirty();
		if (sync) {
			syncContent();
		}
	}

	private void markChanged() {
		markDirty();
		syncContent();
	}

	private void syncContent() {
		if (getLevel().isServer() && getLevel().getServer() != null) {
			getLevel().getServer().network.sendToClientsWithEntity(new PacketObjectEntity(this), this);
		}
	}
}
