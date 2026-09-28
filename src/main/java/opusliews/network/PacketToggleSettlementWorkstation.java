package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import necesse.level.maps.levelData.settlementData.SettlementWorkstationObject;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

public class PacketToggleSettlementWorkstation extends Packet {
	private final int levelIdentifierHashCode;
	private final int tileX;
	private final int tileY;
	private final int settlementUniqueID;

	public PacketToggleSettlementWorkstation(int levelIdentifierHashCode, int tileX, int tileY, int settlementUniqueID) {
		this.levelIdentifierHashCode = levelIdentifierHashCode;
		this.tileX = tileX;
		this.tileY = tileY;
		this.settlementUniqueID = settlementUniqueID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(levelIdentifierHashCode);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextInt(settlementUniqueID);
	}

	public PacketToggleSettlementWorkstation(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		levelIdentifierHashCode = reader.getNextInt();
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementDependantContainer)) return;
		SettlementDependantContainer container = (SettlementDependantContainer)client.getContainer();
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || settlement.uniqueID != settlementUniqueID || !settlement.networkData.doesClientHaveAccess(client)) return;
		Level level = server.world.getLevel(client);
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (level == null || level.getIdentifierHashCode() != levelIdentifierHashCode || domain == null
				|| domain.getLevelType(level.getIdentifier()) == null || level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return;

		LevelObject master = (LevelObject)level.getLevelObject(tileX, tileY).getMasterLevelObject().orElse(null);
		if (master == null) return;
		SettlementWorkstation existing = SettlementLevelStorageManager.getWorkstation(settlement, level.getIdentifier(), master.tileX, master.tileY);
		if (existing != null) {
			SettlementLevelStorageManager.removeWorkstation(settlement, level.getIdentifier(), master.tileX, master.tileY);
		}
		else {
			if (!(master.object instanceof SettlementWorkstationObject)) return;
			SettlementLevelStorageManager.assignWorkstation(settlement, level, master.tileX, master.tileY);
		}
		client.sendPacket(new PacketSettlementWorkstationSync(settlementUniqueID, level.getIdentifierHashCode(), true,
				SettlementLevelStorageManager.getWorkstationPositions(settlement, level.getIdentifier())));
	}
}
