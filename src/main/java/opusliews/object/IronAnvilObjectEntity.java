package opusliews.object;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketObjectEntity;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;

import java.awt.*;
import java.util.ArrayList;

public class IronAnvilObjectEntity extends ObjectEntity implements OEInventory {
	public static final String TYPE = "dynamicironanvil";
	public static final int STORAGE_LINK_RADIUS = 2;

	public final Inventory inventory = new Inventory(1);

	private Point inputStorage;
	private Point outputStorage;

	public IronAnvilObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);

		// The slot is an output only. Server code may set it directly, but
		// ordinary inventory insertion is rejected.
		inventory.filter = (slot, item) -> item == null;
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addSaveData(InventorySave.getSave(inventory, "INVENTORY"));
		addStorageLinkSaveData(save, "inputStorage", inputStorage);
		addStorageLinkSaveData(save, "outputStorage", outputStorage);
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		LoadData inventoryData = save.getFirstLoadDataByName("INVENTORY");
		if (inventoryData != null) {
			inventory.override(InventorySave.loadSave(inventoryData));
		}

		inputStorage = readStorageLinkSaveData(save, "inputStorage");
		outputStorage = readStorageLinkSaveData(save, "outputStorage");
	}

	@Override
	public void setupContentPacket(PacketWriter writer) {
		super.setupContentPacket(writer);
		inventory.writeContent(writer);
		writeStorageLink(writer, inputStorage);
		writeStorageLink(writer, outputStorage);
	}

	@Override
	public void applyContentPacket(PacketReader reader) {
		super.applyContentPacket(reader);
		inventory.override(Inventory.getInventory(reader));
		inputStorage = readStorageLink(reader);
		outputStorage = readStorageLink(reader);
	}

	@Override
	public ArrayList<InventoryItem> getDroppedItems() {
		ArrayList<InventoryItem> list = new ArrayList<>();
		if (!inventory.isSlotClear(0)) {
			list.add(inventory.getItem(0));
		}
		return list;
	}

	@Override
	public void clientTick() {
		super.clientTick();
		inventory.tickItems(this);
	}

	@Override
	public void serverTick() {
		super.serverTick();

		validateStorageLinks();

		inventory.tickItems(this);
		serverTickInventorySync(getLevel().getServer(), this);
	}

	private void validateStorageLinks() {
		if (inputStorage != null && !isValidLinkedStorage(inputStorage)) {
			setInputStorage(null);
		}

		if (outputStorage != null && !isValidLinkedStorage(outputStorage)) {
			setOutputStorage(null);
		}
	}

	private boolean isValidLinkedStorage(Point point) {
		if (!isWithinStorageLinkRange(point.x, point.y)) {
			return false;
		}

		LevelObject object = getLevel().getLevelObject(point.x, point.y);
		if (object == null) {
			return false;
		}

		LevelObject master = object.getMasterLevelObject().orElse(null);
		if (master == null || master.tileX != point.x || master.tileY != point.y) {
			return false;
		}

		ObjectEntity objectEntity = master.getObjectEntity();
		if (!(objectEntity instanceof OEInventory) || objectEntity == this) {
			return false;
		}

		OEInventory inventory = (OEInventory)objectEntity;
		return inventory.getInventory() != null && inventory.getSettlementStorage() != null;
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
		if (item == null) {
			return;
		}

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

	public Point getInputStorage() {
		return inputStorage == null ? null : new Point(inputStorage);
	}

	public Point getOutputStorage() {
		return outputStorage == null ? null : new Point(outputStorage);
	}

	public void setInputStorage(Point point) {
		inputStorage = point == null ? null : new Point(point);
		markDirty();
		syncContent();
	}

	public void setOutputStorage(Point point) {
		outputStorage = point == null ? null : new Point(point);
		markDirty();
		syncContent();
	}

	private void syncContent() {
		if (getLevel().isServer() && getLevel().getServer() != null) {
			getLevel().getServer().network.sendToClientsWithEntity(new PacketObjectEntity(this), this);
		}
	}

	public boolean isWithinStorageLinkRange(int x, int y) {
		return Math.abs(x - tileX) <= STORAGE_LINK_RADIUS && Math.abs(y - tileY) <= STORAGE_LINK_RADIUS;
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

	private static void addStorageLinkSaveData(SaveData save, String key, Point point) {
		if (point == null) {
			return;
		}

		SaveData link = new SaveData(key);
		link.addInt("x", point.x);
		link.addInt("y", point.y);
		save.addSaveData(link);
	}

	private static Point readStorageLinkSaveData(LoadData save, String key) {
		LoadData link = save.getFirstLoadDataByName(key);
		if (link == null) {
			return null;
		}

		int x = link.getInt("x", Integer.MIN_VALUE, false);
		int y = link.getInt("y", Integer.MIN_VALUE, false);
		return x == Integer.MIN_VALUE || y == Integer.MIN_VALUE ? null : new Point(x, y);
	}

	private static void writeStorageLink(PacketWriter writer, Point point) {
		writer.putNextBoolean(point != null);
		if (point != null) {
			writer.putNextInt(point.x);
			writer.putNextInt(point.y);
		}
	}

	private static Point readStorageLink(PacketReader reader) {
		return reader.getNextBoolean() ? new Point(reader.getNextInt(), reader.getNextInt()) : null;
	}
}
