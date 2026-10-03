package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.fishing.FishingAreaLevelData;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

import java.awt.Rectangle;

public class PacketFishingAreaAction extends Packet {
	public static final int CREATE = 1;
	public static final int DELETE = 2;
	public static final int RENAME = 3;
	public static final int RECOLOR = 4;
	public static final int ASSIGN = 5;
	public static final int CHANGE_ZONE = 6;

	private final int settlementUniqueID;
	private final int action;
	private final int firstInt;
	private final int secondInt;
	private final String text;
	private final Rectangle rectangle;
	private final boolean expand;

	private PacketFishingAreaAction(int settlementUniqueID, int action, int firstInt, int secondInt, String text, Rectangle rectangle, boolean expand) {
		this.settlementUniqueID = settlementUniqueID;
		this.action = action;
		this.firstInt = firstInt;
		this.secondInt = secondInt;
		this.text = text;
		this.rectangle = rectangle;
		this.expand = expand;
		writePacket();
	}

	public static PacketFishingAreaAction create(int settlementUniqueID) {
		return new PacketFishingAreaAction(settlementUniqueID, CREATE, 0, 0, null, null, false);
	}

	public static PacketFishingAreaAction delete(int settlementUniqueID, int areaUniqueID) {
		return new PacketFishingAreaAction(settlementUniqueID, DELETE, areaUniqueID, 0, null, null, false);
	}

	public static PacketFishingAreaAction rename(int settlementUniqueID, int areaUniqueID, String name) {
		return new PacketFishingAreaAction(settlementUniqueID, RENAME, areaUniqueID, 0, name, null, false);
	}

	public static PacketFishingAreaAction recolor(int settlementUniqueID, int areaUniqueID, int hue) {
		return new PacketFishingAreaAction(settlementUniqueID, RECOLOR, areaUniqueID, hue, null, null, false);
	}

	public static PacketFishingAreaAction assign(int settlementUniqueID, int mobUniqueID, int areaUniqueID) {
		return new PacketFishingAreaAction(settlementUniqueID, ASSIGN, mobUniqueID, areaUniqueID, null, null, false);
	}

	public static PacketFishingAreaAction changeZone(int settlementUniqueID, int areaUniqueID, Rectangle rectangle, boolean expand) {
		return new PacketFishingAreaAction(settlementUniqueID, CHANGE_ZONE, areaUniqueID, 0, null, rectangle, expand);
	}

	public PacketFishingAreaAction(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		action = reader.getNextByteUnsigned();
		firstInt = reader.getNextInt();
		secondInt = reader.getNextInt();
		text = reader.getNextBoolean() ? reader.getNextString() : null;
		if (reader.getNextBoolean()) rectangle = new Rectangle(reader.getNextInt(), reader.getNextInt(), reader.getNextInt(), reader.getNextInt());
		else rectangle = null;
		expand = reader.getNextBoolean();
	}

	private void writePacket() {
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextByteUnsigned(action);
		writer.putNextInt(firstInt);
		writer.putNextInt(secondInt);
		writer.putNextBoolean(text != null);
		if (text != null) writer.putNextString(text);
		writer.putNextBoolean(rectangle != null);
		if (rectangle != null) {
			writer.putNextInt(rectangle.x);
			writer.putNextInt(rectangle.y);
			writer.putNextInt(rectangle.width);
			writer.putNextInt(rectangle.height);
		}
		writer.putNextBoolean(expand);
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!(client.getContainer() instanceof SettlementDependantContainer)) return;
		SettlementDependantContainer container = (SettlementDependantContainer)client.getContainer();
		ServerSettlementData settlement = container.getServerData();
		if (settlement == null || settlement.uniqueID != settlementUniqueID || !settlement.networkData.doesClientHaveAccess(client)) return;
		FishingAreaLevelData data = FishingAreaLevelData.get(settlement.getLevel(), true);
		if (data == null) return;

		boolean changed = false;
		switch (action) {
			case CREATE: {
				if (client.getLevel() == null) break;
				SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
				if (domain == null || !domain.containsLevel(client.getLevel().getIdentifier())) break;
				changed = data.createArea(settlementUniqueID, client.getLevel().getIdentifier()) != null;
				break;
			}
			case DELETE:
				changed = data.deleteArea(settlementUniqueID, firstInt);
				break;
			case RENAME:
				changed = data.renameArea(settlementUniqueID, firstInt, text);
				break;
			case RECOLOR:
				changed = data.recolorArea(settlementUniqueID, firstInt, secondInt);
				break;
			case ASSIGN: {
				LevelSettler settler = settlement.getSettler(firstInt);
				if (settler == null || settler.settler == null || !"angler".equals(settler.settler.getStringID())) break;
				changed = data.assignArea(settlementUniqueID, firstInt, secondInt);
				break;
			}
			case CHANGE_ZONE: {
				if (client.getLevel() == null) break;
				changed = data.changeArea(settlementUniqueID, firstInt, client.getLevel().getIdentifier(), rectangle, expand);
				break;
			}
			default:
				break;
		}

		if (Logging.logEnabled) {
			if (changed) Logging.logMessage("[FishingAreas] Applied fishing area action action=" + action + " settlement=" + settlementUniqueID + " first=" + firstInt + " second=" + secondInt);
			else Logging.logMessage("[FishingAreas] Fishing area action made no change action=" + action + " settlement=" + settlementUniqueID + " first=" + firstInt + " second=" + secondInt);
		}
		broadcastSync(server, settlement, data.snapshot(settlementUniqueID));
	}

	private static void broadcastSync(Server server, ServerSettlementData settlement, FishingAreaLevelData.Snapshot snapshot) {
		PacketFishingAreasSync sync = new PacketFishingAreasSync(settlement.uniqueID, snapshot);
		for (Object value : server.getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient target = (ServerClient)value;
			if (!(target.getContainer() instanceof SettlementDependantContainer)) continue;
			SettlementDependantContainer targetContainer = (SettlementDependantContainer)target.getContainer();
			ServerSettlementData targetSettlement = targetContainer.getServerData();
			if (targetSettlement != null && targetSettlement.uniqueID == settlement.uniqueID && targetSettlement.networkData.doesClientHaveAccess(target)) target.sendPacket(sync);
		}
	}
}
