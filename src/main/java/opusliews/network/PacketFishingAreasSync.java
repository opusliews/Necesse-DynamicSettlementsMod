package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.Zoning;
import opusliews.fishing.FishingAreaAssignUI;
import opusliews.fishing.FishingAreaLevelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PacketFishingAreasSync extends Packet {
	private final int settlementUniqueID;
	private final FishingAreaLevelData.Snapshot snapshot;

	public PacketFishingAreasSync(int settlementUniqueID, FishingAreaLevelData.Snapshot snapshot) {
		this.settlementUniqueID = settlementUniqueID;
		this.snapshot = snapshot;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextShortUnsigned(snapshot.areas.size());
		for (FishingAreaLevelData.FishingArea area : snapshot.areas) {
			writer.putNextInt(area.uniqueID);
			writer.putNextInt(area.index);
			writer.putNextShortUnsigned(area.colorHue);
			writer.putNextString(area.name);
			writer.putNextString(area.levelIdentifier.stringID);
			area.zoning.writeZonePacket(writer);
		}
		writer.putNextShortUnsigned(snapshot.assignments.size());
		for (Map.Entry<Integer, Integer> assignment : snapshot.assignments.entrySet()) {
			writer.putNextInt(assignment.getKey());
			writer.putNextInt(assignment.getValue());
		}
	}

	public PacketFishingAreasSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		ArrayList<FishingAreaLevelData.FishingArea> areas = new ArrayList<>();
		int areaCount = reader.getNextShortUnsigned();
		for (int i = 0; i < areaCount; i++) {
			int uniqueID = reader.getNextInt();
			int index = reader.getNextInt();
			int colorHue = reader.getNextShortUnsigned();
			String name = reader.getNextString();
			LevelIdentifier levelIdentifier = new LevelIdentifier(reader.getNextString());
			Zoning zoning = new Zoning();
			zoning.readZonePacket(reader);
			areas.add(new FishingAreaLevelData.FishingArea(uniqueID, index, colorHue, name, levelIdentifier, zoning));
		}
		HashMap<Integer, Integer> assignments = new HashMap<>();
		int assignmentCount = reader.getNextShortUnsigned();
		for (int i = 0; i < assignmentCount; i++) assignments.put(reader.getNextInt(), reader.getNextInt());
		snapshot = new FishingAreaLevelData.Snapshot(areas, assignments);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		FishingAreaAssignUI.applySync(client, settlementUniqueID, snapshot);
	}
}
