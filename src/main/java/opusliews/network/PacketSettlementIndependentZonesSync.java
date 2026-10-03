package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.util.LevelIdentifier;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import opusliews.logging.Logging;
import opusliews.zones.SettlementIndependentZonesLevelData;
import opusliews.zones.SettlementIndependentZoneSystem;
import opusliews.zones.SettlementWorkZoneClientUI;
import opusliews.zones.SettlementZoneClientCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class PacketSettlementIndependentZonesSync extends Packet {
	private final int settlementUniqueID;
	private final HashMap<Integer, LevelIdentifier> restrictOwners = new HashMap<>();
	private final HashMap<Integer, LevelIdentifier> workOwners = new HashMap<>();
	private final HashMap<Integer, SettlementWorkZone> workZones = new HashMap<>();

	public PacketSettlementIndependentZonesSync(ServerSettlementData settlement) {
		settlementUniqueID = settlement.uniqueID;
		SettlementIndependentZonesLevelData data = SettlementIndependentZoneSystem.getData(settlement, false);
		if (data != null) {
			for (SettlementIndependentZonesLevelData.RestrictEntry entry : data.getRestricts(settlement.uniqueID)) restrictOwners.put(entry.uniqueID, entry.levelIdentifier);
			for (SettlementIndependentZonesLevelData.WorkEntry entry : data.getWorks(settlement.uniqueID)) workOwners.put(entry.uniqueID, entry.levelIdentifier);
		}
		for (SettlementWorkZone zone : SettlementIndependentZoneSystem.getAllCustomWorkZones(settlement)) workZones.put(zone.getUniqueID(), zone);
		if (Logging.logEnabled) Logging.logMessage("[IndependentZonesDebug] Server building sync settlement=" + settlementUniqueID
				+ " surfaceZones=" + describeSurfaceZones(settlement)
				+ " customZones=" + describeCustomZones(workZones, workOwners));
		write();
	}

	public PacketSettlementIndependentZonesSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		int restrictCount = reader.getNextShortUnsigned();
		for (int i = 0; i < restrictCount; i++) restrictOwners.put(reader.getNextInt(), new LevelIdentifier(reader.getNextString()));
		int workOwnerCount = reader.getNextShortUnsigned();
		for (int i = 0; i < workOwnerCount; i++) workOwners.put(reader.getNextInt(), new LevelIdentifier(reader.getNextString()));
		int workZoneCount = reader.getNextShortUnsigned();
		for (int i = 0; i < workZoneCount; i++) {
			int zoneID = reader.getNextShortUnsigned();
			int uniqueID = reader.getNextInt();
			try {
				SettlementWorkZone zone = SettlementWorkZoneRegistry.getNewZone(zoneID);
				zone.setUniqueID(uniqueID);
				zone.applyPacket(reader);
				workZones.put(uniqueID, zone);
			}
			catch (RuntimeException error) {
				if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Failed reading synced work zone uniqueID=" + uniqueID + " zoneID=" + zoneID + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage());
				throw error;
			}
		}
	}

	private void write() {
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextShortUnsigned(restrictOwners.size());
		for (Integer uniqueID : restrictOwners.keySet()) {
			writer.putNextInt(uniqueID);
			writer.putNextString(restrictOwners.get(uniqueID).stringID);
		}
		writer.putNextShortUnsigned(workOwners.size());
		for (Integer uniqueID : workOwners.keySet()) {
			writer.putNextInt(uniqueID);
			writer.putNextString(workOwners.get(uniqueID).stringID);
		}
		writer.putNextShortUnsigned(workZones.size());
		for (SettlementWorkZone zone : workZones.values()) {
			writer.putNextShortUnsigned(zone.getID());
			writer.putNextInt(zone.getUniqueID());
			zone.writePacket(writer);
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		HashSet<Integer> previousCustomZoneIDs = SettlementZoneClientCache.getCustomWorkZoneIDs(settlementUniqueID);
		SettlementZoneClientCache.apply(settlementUniqueID, restrictOwners, workOwners, workZones);
		SettlementWorkZoneClientUI.applySyncedWorkZones(settlementUniqueID, previousCustomZoneIDs);
		if (Logging.logEnabled) {
			LevelIdentifier current = client == null || client.getLevel() == null ? null : client.getLevel().getIdentifier();
			Logging.logMessage("[IndependentZonesDebug] Client received sync settlement=" + settlementUniqueID
					+ " currentLevel=" + (current == null ? "null" : current.stringID)
					+ " previousCustomIDs=" + sortedIDs(previousCustomZoneIDs)
					+ " customZones=" + describeCustomZones(workZones, workOwners));
		}
	}

	public static String describeSurfaceZones(ServerSettlementData settlement) {
		ArrayList<String> result = new ArrayList<>();
		if (settlement != null && settlement.getWorkZones() != null) {
			for (Object value : settlement.getWorkZones().getZones().values()) {
				SettlementWorkZone zone = (SettlementWorkZone)value;
				result.add(zone.getUniqueID() + ":" + zone.getStringID() + ":size=" + zone.size());
			}
		}
		Collections.sort(result);
		return result.toString();
	}

	public static String describeCustomZones(HashMap<Integer, SettlementWorkZone> zones, HashMap<Integer, LevelIdentifier> owners) {
		ArrayList<String> result = new ArrayList<>();
		for (SettlementWorkZone zone : zones.values()) {
			LevelIdentifier owner = owners.get(zone.getUniqueID());
			result.add(zone.getUniqueID() + ":" + zone.getStringID() + ":owner=" + (owner == null ? "null" : owner.stringID) + ":size=" + zone.size());
		}
		Collections.sort(result);
		return result.toString();
	}

	public static String sortedIDs(Iterable<Integer> ids) {
		ArrayList<Integer> result = new ArrayList<>();
		for (Integer id : ids) result.add(id);
		Collections.sort(result);
		return result.toString();
	}
}
