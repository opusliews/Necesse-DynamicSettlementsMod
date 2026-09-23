package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.container.settlement.events.SettlementStorageChangeAllowedEvent;
import necesse.inventory.item.Item;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.stock.SettlementStockSystem;

public class PacketSettlementStockUpdate extends Packet {
	private final int tileX;
	private final int tileY;
	private final int itemID;
	private final int stock;

	public PacketSettlementStockUpdate(int tileX, int tileY, int itemID, int stock) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.itemID = itemID;
		this.stock = Math.max(0, stock);
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextShortUnsigned(itemID);
		writer.putNextInt(this.stock);
	}

	public PacketSettlementStockUpdate(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		itemID = reader.getNextShortUnsigned();
		stock = Math.max(0, reader.getNextInt());
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementDependantContainer)) return;
		SettlementDependantContainer container = (SettlementDependantContainer)client.getContainer();
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || !settlement.networkData.doesClientHaveAccess(client)) return;
		SettlementInventory storage = settlement.storageManager.getStorage(tileX, tileY);
		Item item = ItemRegistry.getItem(itemID);
		if (storage == null || item == null) return;

		SettlementStockSystem.setStockTarget(storage, itemID, stock);
		if (stock > 0 && !storage.filter.isItemAllowed(item)) {
			storage.filter.setItemAllowed(item, true);
			new SettlementStorageChangeAllowedEvent(
					settlement,
					tileX,
					tileY,
					new Item[]{item},
					true
			).applyAndSendToClientsAt(settlement.getLevel());
		}
		SettlementStockSystem.enforceConfiguredMax(settlement, storage, true);
		server.network.sendToClientsAtEntireLevel(
				new PacketSettlementStockSync(tileX, tileY, SettlementStockSystem.getTargets(storage)),
				settlement.getLevel()
		);
	}
}
