package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opusliews.multilevelsettlement.SettlementLadderSystem;

public class PacketToggleSettlementLadder extends Packet {
	public final int levelIdentifierHashCode;
	public final int tileX;
	public final int tileY;
	public final int settlementUniqueID;

	public PacketToggleSettlementLadder(int levelIdentifierHashCode, int tileX, int tileY, int settlementUniqueID) {
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

	public PacketToggleSettlementLadder(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		levelIdentifierHashCode = reader.getNextInt();
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		settlementUniqueID = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		SettlementLadderSystem.handleTogglePacket(server, client, levelIdentifierHashCode, tileX, tileY, settlementUniqueID);
	}
}
