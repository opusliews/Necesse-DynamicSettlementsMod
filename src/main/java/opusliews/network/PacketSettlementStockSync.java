package opusliews.network;

import java.util.LinkedHashMap;
import java.util.Map;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.stock.SettlementStockUI;

public class PacketSettlementStockSync extends Packet {
	private final int tileX;
	private final int tileY;
	private final LinkedHashMap<Integer, Integer> targets;

	public PacketSettlementStockSync(int tileX, int tileY, Map<Integer, Integer> targets) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.targets = sanitize(targets);
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextShortUnsigned(this.targets.size());
		for (Map.Entry<Integer, Integer> entry : this.targets.entrySet()) {
			writer.putNextShortUnsigned(entry.getKey());
			writer.putNextInt(entry.getValue());
		}
	}

	public PacketSettlementStockSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		targets = new LinkedHashMap<>();
		int count = Math.min(65535, reader.getNextShortUnsigned());
		for (int i = 0; i < count; i++) {
			int itemID = reader.getNextShortUnsigned();
			int stock = Math.max(0, reader.getNextInt());
			if (stock > 0) targets.put(itemID, stock);
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		SettlementStockUI.applySync(tileX, tileY, targets);
	}

	private static LinkedHashMap<Integer, Integer> sanitize(Map<Integer, Integer> targets) {
		LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
		if (targets == null) return result;
		for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}
}
