package opusliews.object;

import java.awt.Point;
import java.util.ArrayList;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketObjectEntity;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.entity.TileEntity;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;

public abstract class DynamicCraftingStationObjectEntity extends ObjectEntity implements OEInventory {
	public static final int STORAGE_LINK_RADIUS = 3;

	public final Inventory inventory = new Inventory(1);

	private final ArrayList<Point> inputStorages = new ArrayList<>();
	private final ArrayList<Point> outputStorages = new ArrayList<>();
	private Point taskBoard;

	protected DynamicCraftingStationObjectEntity(Level level, String type, int tileX, int tileY) {
		super(level, type, tileX, tileY);

		inventory.filter = (slot, item) -> item == null;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addSaveData(InventorySave.getSave(inventory, "INVENTORY"));
		addLinkListSaveData(save, "inputStorages", inputStorages);
		addLinkListSaveData(save, "outputStorages", outputStorages);
		addLinkSaveData(save, "taskBoard", taskBoard);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		LoadData inventoryData = save.getFirstLoadDataByName("INVENTORY");
		if (inventoryData != null) inventory.override(InventorySave.loadSave(inventoryData));

		inputStorages.clear();
		inputStorages.addAll(readLinkListSaveData(save, "inputStorages"));
		outputStorages.clear();
		outputStorages.addAll(readLinkListSaveData(save, "outputStorages"));
		taskBoard = readLinkSaveData(save, "taskBoard");
	}

	@Override
	public void setupContentPacket(PacketWriter writer) {
		super.setupContentPacket(writer);
		inventory.writeContent(writer);
		writeLinks(writer, inputStorages);
		writeLinks(writer, outputStorages);
		writeLink(writer, taskBoard);
	}

	@Override
	public void applyContentPacket(PacketReader reader) {
		super.applyContentPacket(reader);
		inventory.override(Inventory.getInventory(reader));
		inputStorages.clear();
		inputStorages.addAll(readLinks(reader));
		outputStorages.clear();
		outputStorages.addAll(readLinks(reader));
		taskBoard = readLink(reader);
	}

	@Override
	public ArrayList<InventoryItem> getDroppedItems() {
		ArrayList<InventoryItem> list = new ArrayList<>();
		if (!inventory.isSlotClear(0)) list.add(inventory.getItem(0));
		return list;
	}

	@Override
	public void clientTick() {
		super.clientTick();
		inventory.tickItems((TileEntity)this);
	}

	@Override
	public void serverTick() {
		super.serverTick();
		validateStorageLinks();
		validateTaskBoard();
		inventory.tickItems(this);
		serverTickInventorySync(getLevel().getServer(), this);
	}

	private void validateStorageLinks() {
		boolean changed = inputStorages.removeIf(point -> !isValidLinkedStorage(point));
		changed |= outputStorages.removeIf(point -> !isValidLinkedStorage(point));
		if (changed) {
			markDirty();
			syncContent();
		}
	}

	private boolean isValidLinkedStorage(Point point) {
		if (!isWithinStorageLinkRange(point.x, point.y)) return false;

		LevelObject object = getLevel().getLevelObject(point.x, point.y);
		if (object == null) return false;

		LevelObject master = (LevelObject)object.getMasterLevelObject().orElse(null);
		if (master == null || master.tileX != point.x || master.tileY != point.y) return false;

		ObjectEntity objectEntity = master.getObjectEntity();
		if (!(objectEntity instanceof OEInventory) || objectEntity == this) return false;

		OEInventory linkedInventory = (OEInventory)objectEntity;
		return linkedInventory.getInventory() != null && linkedInventory.getSettlementStorage() != null;
	}

	private void validateTaskBoard() {
		if (taskBoard == null) return;

		ObjectEntity entity = getLevel().entityManager.getObjectEntity(taskBoard.x, taskBoard.y);
		if (!isCompatibleTaskBoard(entity)) {
			setTaskBoard(null);
			return;
		}

		Point linkedStation = getTaskBoardLinkedStation(entity);
		if (linkedStation == null || linkedStation.x != tileX || linkedStation.y != tileY) setTaskBoard(null);
	}

	@Override
	public void markClean() {
		super.markClean();
		inventory.clean();
	}

	public void setOutput(InventoryItem item) {
		inventory.setItem(0, item);
		markDirty();
	}

	public void ejectOutput() {
		InventoryItem item = inventory.getItem(0);
		if (item == null) return;

		if (getLevel().isServer()) {
			getLevel().entityManager.pickups.add(item.getPickupEntity(
					getLevel(),
					(float)(tileX * 32 + 16),
					(float)(tileY * 32 + 16)
			));
		}

		inventory.clearSlot(0);
		markDirty();
	}

	public ArrayList<Point> getInputStorages() {
		return copyPoints(inputStorages);
	}

	public ArrayList<Point> getOutputStorages() {
		return copyPoints(outputStorages);
	}


	public Point getTaskBoard() {
		return taskBoard == null ? null : new Point(taskBoard);
	}

	public boolean hasInputStorage(Point point) {
		return containsPoint(inputStorages, point);
	}

	public boolean hasOutputStorage(Point point) {
		return containsPoint(outputStorages, point);
	}

	public void addInputStorage(Point point) {
		if (point == null || containsPoint(inputStorages, point)) return;
		inputStorages.add(new Point(point));
		markDirty();
		syncContent();
	}

	public void removeInputStorage(Point point) {
		if (point == null || !inputStorages.remove(point)) return;
		markDirty();
		syncContent();
	}

	public void addOutputStorage(Point point) {
		if (point == null || containsPoint(outputStorages, point)) return;
		outputStorages.add(new Point(point));
		markDirty();
		syncContent();
	}

	public void removeOutputStorage(Point point) {
		if (point == null || !outputStorages.remove(point)) return;
		markDirty();
		syncContent();
	}

	public void clearInputStorages() {
		if (inputStorages.isEmpty()) return;
		inputStorages.clear();
		markDirty();
		syncContent();
	}

	public void clearOutputStorages() {
		if (outputStorages.isEmpty()) return;
		outputStorages.clear();
		markDirty();
		syncContent();
	}

	public void setTaskBoard(Point point) {
		Point old = taskBoard == null ? null : new Point(taskBoard);
		taskBoard = point == null ? null : new Point(point);
		markDirty();

		if (getLevel().isServer()) {
			Point self = new Point(tileX, tileY);
			if (old != null && (taskBoard == null || !old.equals(taskBoard))) {
				ObjectEntity oldEntity = getLevel().entityManager.getObjectEntity(old.x, old.y);
				if (isCompatibleTaskBoard(oldEntity)) {
					Point owner = getTaskBoardLinkedStation(oldEntity);
					if (owner != null && owner.equals(self)) setTaskBoardLinkedStation(oldEntity, null, true);
				}
			}

			if (taskBoard != null) {
				ObjectEntity newEntity = getLevel().entityManager.getObjectEntity(taskBoard.x, taskBoard.y);
				if (isCompatibleTaskBoard(newEntity)) setTaskBoardLinkedStation(newEntity, self, true);
			}
		}

		syncContent();
	}

	public boolean isStorageInputForOtherStation(Point point) {
		for (Object object : getLevel().entityManager.objectEntities) {
			if (!(object instanceof DynamicCraftingStationObjectEntity) || object == this) continue;

			DynamicCraftingStationObjectEntity other = (DynamicCraftingStationObjectEntity)object;
			if (other.hasInputStorage(point)) return true;
		}
		return false;
	}

	public boolean isWithinStorageLinkRange(int x, int y) {
		return Math.abs(x - tileX) <= STORAGE_LINK_RADIUS && Math.abs(y - tileY) <= STORAGE_LINK_RADIUS;
	}

	public final boolean canUseTaskBoard(ObjectEntity entity) {
		return isCompatibleTaskBoard(entity);
	}

	public final Point getLinkedStationForTaskBoard(ObjectEntity entity) {
		return isCompatibleTaskBoard(entity) ? getTaskBoardLinkedStation(entity) : null;
	}

	public boolean canStartPlayerCraft(PlayerMob player, Recipe recipe) {
		return true;
	}

	public boolean canCompletePlayerCraft(PlayerMob player, Recipe recipe) {
		return true;
	}

	public void onPlayerCraftStarted(PlayerMob player, Recipe recipe) {
	}

	public void onPlayerCraftCompleted(PlayerMob player, Recipe recipe, InventoryItem resultItem) {
	}

	protected boolean isCompatibleTaskBoard(ObjectEntity entity) {
		return entity instanceof CraftingTaskBoardObjectEntity;
	}

	protected Point getTaskBoardLinkedStation(ObjectEntity entity) {
		return ((CraftingTaskBoardObjectEntity)entity).getLinkedStation();
	}

	protected void setTaskBoardLinkedStation(ObjectEntity entity, Point station, boolean sync) {
		((CraftingTaskBoardObjectEntity)entity).setLinkedStationInternal(station, sync);
	}

	public String getTaskBoardTextureKey() {
		return "unlinked";
	}

	public boolean supportsSettlerCraftingTasks() {
		return false;
	}

	public boolean canSettlerPerformCrafting(HumanMob worker) {
		return false;
	}

	public String getSettlerCraftingActivityText() {
		return "Working at crafting station";
	}

	public String getSettlerCraftingWorkItemStringID() {
		return null;
	}

	public int getSettlerCraftingActionDelay(HumanMob worker) {
		int happiness = Math.max(0, Math.min(100, worker.getSettlerHappiness()));
		return 5000 - happiness * 45;
	}

	public String getSettlerCraftingProblem(Recipe recipe) {
		return null;
	}

	public void onSettlerCraftStarted(HumanMob worker, Recipe recipe) {
	}

	public void onSettlerCraftCompleted(HumanMob worker, Recipe recipe, InventoryItem resultItem) {
	}

	public void playSettlerCraftingWorkEffect() {
	}

	public StationState captureStationState() {
		InventoryItem output = inventory.getItem(0);
		return new StationState(
				output == null ? null : output.copy(),
				copyPoints(inputStorages),
				copyPoints(outputStorages),
				taskBoard == null ? null : new Point(taskBoard)
		);
	}

	public void applyStationState(StationState state) {
		if (state == null) return;

		inventory.setItem(0, state.output == null ? null : state.output.copy());
		inputStorages.clear();
		inputStorages.addAll(copyPoints(state.inputStorages));
		outputStorages.clear();
		outputStorages.addAll(copyPoints(state.outputStorages));
		taskBoard = state.taskBoard == null ? null : new Point(state.taskBoard);
		markDirty();
		syncContent();
	}

	public static StationState captureStationState(Level level, int tileX, int tileY) {
		if (level == null) return null;
		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		return entity instanceof DynamicCraftingStationObjectEntity
				? ((DynamicCraftingStationObjectEntity)entity).captureStationState()
				: null;
	}

	public static void applyStationState(Level level, int tileX, int tileY, StationState state) {
		if (level == null || state == null) return;
		ObjectEntity entity = level.entityManager.getObjectEntity(tileX, tileY);
		if (entity instanceof DynamicCraftingStationObjectEntity) {
			((DynamicCraftingStationObjectEntity)entity).applyStationState(state);
		}
	}

	public static class StationState {
		private final InventoryItem output;
		private final ArrayList<Point> inputStorages;
		private final ArrayList<Point> outputStorages;
		private final Point taskBoard;

		private StationState(
				InventoryItem output,
				ArrayList<Point> inputStorages,
				ArrayList<Point> outputStorages,
				Point taskBoard
		) {
			this.output = output;
			this.inputStorages = inputStorages;
			this.outputStorages = outputStorages;
			this.taskBoard = taskBoard;
		}
	}

	private void syncContent() {
		if (getLevel().isServer() && getLevel().getServer() != null) {
			getLevel().getServer().network.sendToClientsWithEntity(new PacketObjectEntity(this), this);
		}
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	@Override
	public GameMessage getInventoryName() {
		return getObject().getLocalization();
	}

	@Override
	public boolean canSetInventoryName() {
		return false;
	}

	@Override
	public boolean canQuickStackInventory() {
		return false;
	}

	@Override
	public boolean canRestockInventory() {
		return false;
	}

	@Override
	public boolean canSortInventory() {
		return false;
	}

	@Override
	public boolean canUseForNearbyCrafting() {
		return false;
	}

	@Override
	public InventoryRange getSettlementStorage() {
		return null;
	}

	private static ArrayList<Point> copyPoints(ArrayList<Point> points) {
		ArrayList<Point> copy = new ArrayList<>(points.size());
		for (Point point : points) copy.add(new Point(point));
		return copy;
	}

	private static boolean containsPoint(ArrayList<Point> points, Point target) {
		return target != null && points.contains(target);
	}

	private static void addLinkListSaveData(SaveData save, String key, ArrayList<Point> points) {
		SaveData list = new SaveData(key);
		for (Point point : points) addLinkSaveData(list, "link", point);
		save.addSaveData(list);
	}

	private static ArrayList<Point> readLinkListSaveData(LoadData save, String key) {
		ArrayList<Point> points = new ArrayList<>();
		LoadData list = save.getFirstLoadDataByName(key);
		if (list == null) return points;

		for (LoadData link : list.getLoadDataByName("link")) {
			int x = link.getInt("x", Integer.MIN_VALUE, false);
			int y = link.getInt("y", Integer.MIN_VALUE, false);
			if (x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) points.add(new Point(x, y));
		}
		return points;
	}

	private static void addLinkSaveData(SaveData save, String key, Point point) {
		if (point == null) return;

		SaveData link = new SaveData(key);
		link.addInt("x", point.x);
		link.addInt("y", point.y);
		save.addSaveData(link);
	}

	private static Point readLinkSaveData(LoadData save, String key) {
		LoadData link = save.getFirstLoadDataByName(key);
		if (link == null) return null;

		int x = link.getInt("x", Integer.MIN_VALUE, false);
		int y = link.getInt("y", Integer.MIN_VALUE, false);
		return x == Integer.MIN_VALUE || y == Integer.MIN_VALUE ? null : new Point(x, y);
	}

	private static void writeLinks(PacketWriter writer, ArrayList<Point> points) {
		writer.putNextInt(points.size());
		for (Point point : points) {
			writer.putNextInt(point.x);
			writer.putNextInt(point.y);
		}
	}

	private static ArrayList<Point> readLinks(PacketReader reader) {
		int count = reader.getNextInt();
		ArrayList<Point> points = new ArrayList<>(Math.max(0, count));
		for (int i = 0; i < count; i++) points.add(new Point(reader.getNextInt(), reader.getNextInt()));
		return points;
	}

	private static void writeLink(PacketWriter writer, Point point) {
		writer.putNextBoolean(point != null);
		if (point != null) {
			writer.putNextInt(point.x);
			writer.putNextInt(point.y);
		}
	}

	private static Point readLink(PacketReader reader) {
		return reader.getNextBoolean() ? new Point(reader.getNextInt(), reader.getNextInt()) : null;
	}
}
