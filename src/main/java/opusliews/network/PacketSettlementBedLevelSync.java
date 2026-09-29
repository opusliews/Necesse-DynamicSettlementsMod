package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementBedLevelIndicatorSystem;

import java.util.HashMap;
import java.util.Map;

public class PacketSettlementBedLevelSync extends Packet {
	private final int settlementUniqueID;
	private final HashMap<Integer, Boolean> bedLevels;

	public PacketSettlementBedLevelSync(int settlementUniqueID, Map<Integer, Boolean> bedLevels) {
		this.settlementUniqueID = settlementUniqueID;
		this.bedLevels = new HashMap<>();
		if (bedLevels != null) this.bedLevels.putAll(bedLevels);

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextShortUnsigned(this.bedLevels.size());
		for (Map.Entry<Integer, Boolean> entry : this.bedLevels.entrySet()) {
			writer.putNextInt(entry.getKey());
			writer.putNextBoolean(entry.getValue());
		}
	}

	public PacketSettlementBedLevelSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		bedLevels = new HashMap<>();
		int count = reader.getNextShortUnsigned();
		for (int i = 0; i < count; i++) bedLevels.put(reader.getNextInt(), reader.getNextBoolean());
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		if (Logging.logEnabled) Logging.logMessage("[BedLevelIndicator] Client received bed-level sync settlement=" + settlementUniqueID + " bedLevels=" + bedLevels);
		SettlementBedLevelIndicatorSystem.applySync(settlementUniqueID, bedLevels);
	}
}
