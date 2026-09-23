package opusliews.network;

import java.util.LinkedHashMap;
import java.util.Map;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.clayfiring.ClayFiringCatalog;
import opusliews.clayfiring.ClayFiringSettingsLevelData;
import opusliews.logging.Logging;

public class PacketClayFiringSettingsUpdate extends Packet {
	private final int settlementUniqueID;
	private final LinkedHashMap<String, Integer> targets;
	private final boolean repeatForever;

	public PacketClayFiringSettingsUpdate(int settlementUniqueID, Map<String, Integer> targets, boolean repeatForever) {
		this.settlementUniqueID = settlementUniqueID;
		this.targets = sanitize(targets);
		this.repeatForever = repeatForever;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writeTargets(writer, this.targets);
		writer.putNextBoolean(repeatForever);
	}

	public PacketClayFiringSettingsUpdate(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		targets = sanitize(readTargets(reader));
		repeatForever = reader.getNextBoolean();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementContainer)) return;
		SettlementContainer container = (SettlementContainer)client.getContainer();
		if (container.getSettlementUniqueID() != settlementUniqueID) return;
		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(server).getOrLoadServerData(settlementUniqueID);
		if (settlement == null) return;
		ClayFiringSettingsLevelData data = ClayFiringSettingsLevelData.get(settlement.getLevel(), true);
		data.setProductionSettings(targets, repeatForever);
		Logging.logMessage("[ClayFiring] Updated settings settlement=" + settlementUniqueID
				+ " repeat=" + repeatForever + " targets=" + data.getProductionTargets());
		client.sendPacket(new PacketClayFiringSettingsSync(data.getProductionTargets(), data.isRepeatForever()));
	}

	static void writeTargets(PacketWriter writer, Map<String, Integer> targets) {
		writer.putNextInt(targets.size());
		for (Map.Entry<String, Integer> entry : targets.entrySet()) {
			writer.putNextString(entry.getKey());
			writer.putNextInt(Math.max(0, entry.getValue()));
		}
	}

	static LinkedHashMap<String, Integer> readTargets(PacketReader reader) {
		LinkedHashMap<String, Integer> targets = new LinkedHashMap<>();
		int count = Math.max(0, Math.min(256, reader.getNextInt()));
		for (int i = 0; i < count; i++) {
			String itemStringID = reader.getNextString();
			int amount = Math.max(0, reader.getNextInt());
			targets.put(itemStringID, amount);
		}
		return targets;
	}

	static LinkedHashMap<String, Integer> sanitize(Map<String, Integer> targets) {
		LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
		for (ClayFiringCatalog.Entry entry : ClayFiringCatalog.getEntries()) {
			result.put(entry.firedItemStringID, Math.max(0, targets == null ? 0 : targets.getOrDefault(entry.firedItemStringID, 0)));
		}
		return result;
	}
}
