package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.stock.SettlementStockSystem;

public class PacketSettlementStockRequest extends Packet {
	private final int tileX;
	private final int tileY;

	public PacketSettlementStockRequest(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	public PacketSettlementStockRequest(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementDependantContainer)) return;
		SettlementDependantContainer container = (SettlementDependantContainer)client.getContainer();
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || !settlement.networkData.doesClientHaveAccess(client)) return;
		SettlementInventory storage = settlement.storageManager.getStorage(tileX, tileY);
		if (storage == null) return;
		client.sendPacket(new PacketSettlementStockSync(tileX, tileY, SettlementStockSystem.getTargets(storage)));
	}
}
