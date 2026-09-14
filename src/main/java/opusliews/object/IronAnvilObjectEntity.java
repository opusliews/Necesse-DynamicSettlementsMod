package opusliews.object;

import java.util.ArrayList;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.save.levelData.InventorySave;
import necesse.entity.TileEntity;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.inventory.InventoryRange;
import necesse.level.maps.Level;

public class IronAnvilObjectEntity extends ObjectEntity implements OEInventory {
	public static final String TYPE = "dynamicironanvil";
	public final Inventory inventory = new Inventory(1);

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
	}

	@Override
	public void applyLoadData(LoadData save) {
		super.applyLoadData(save);
		LoadData inventoryData = save.getFirstLoadDataByName("INVENTORY");
		if (inventoryData != null) {
			inventory.override(InventorySave.loadSave(inventoryData));
		}
	}

	@Override
	public void setupContentPacket(PacketWriter writer) {
		super.setupContentPacket(writer);
		inventory.writeContent(writer);
	}

	@Override
	public void applyContentPacket(PacketReader reader) {
		super.applyContentPacket(reader);
		inventory.override(Inventory.getInventory(reader));
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
		inventory.tickItems((TileEntity)this);
	}

	@Override
	public void serverTick() {
		super.serverTick();
		inventory.tickItems(this);
		serverTickInventorySync(getLevel().getServer(), this);
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
}
