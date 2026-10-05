package opusliews.zones;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.ZoningChange;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.container.settlement.events.SettlementRestrictZonesFullEvent;
import necesse.inventory.container.settlement.events.SettlementOpenWorkZoneConfigEvent;
import necesse.inventory.container.settlement.events.SettlementWorkZonesEvent;
import necesse.level.maps.levelData.settlementData.RestrictZone;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import opusliews.logging.Logging;

import java.awt.Point;
import java.awt.Rectangle;

public final class SettlementIndependentZoneActionSupport {
	private SettlementIndependentZoneActionSupport() {
	}

	public static boolean handleCreateWorkZone(SettlementDependantContainer container, PacketReader reader) {
		ServerContext context = getNonSurfaceContext(container);
		if (context == null) return false;
		int zoneID = reader.getNextInt();
		int uniqueID = reader.getNextInt();
		Rectangle rectangle = readRectangle(reader);
		Point anchor = readOptionalPoint(reader);
		if (!isIndependentVanillaWorkZoneID(zoneID)) {
			broadcastWork(context.settlement);
			return true;
		}
		SettlementWorkZone zone = SettlementIndependentZoneSystem.createWorkZone(context.settlement, context.levelIdentifier, zoneID, uniqueID, rectangle, anchor);
		if (Logging.logEnabled) Logging.logMessage("[IndependentZones] Create work zone level=" + context.levelIdentifier + " zoneID=" + zoneID + " uniqueID=" + uniqueID + " success=" + (zone != null));
		broadcastWork(context.settlement);
		if (zone != null) new SettlementOpenWorkZoneConfigEvent(zone).applyAndSendToClient(context.client);
		return true;
	}

	public static boolean handleExpandWorkZone(SettlementDependantContainer container, PacketReader reader) {
		ServerContext context = getNonSurfaceContext(container);
		if (context == null) return false;
		int uniqueID = reader.getNextInt();
		Rectangle rectangle = readRectangle(reader);
		Point anchor = readOptionalPoint(reader);
		SettlementWorkZone current = SettlementIndependentZoneSystem.getWorkZone(context.settlement, uniqueID);
		if (current == null || !isIndependentVanillaWorkZoneID(current.getID())) {
			broadcastWork(context.settlement);
			return true;
		}
		SettlementWorkZone zone = SettlementIndependentZoneSystem.expandWorkZone(context.settlement, uniqueID, context.levelIdentifier, rectangle, anchor);
		broadcastWork(context.settlement);
		if (zone != null) new SettlementOpenWorkZoneConfigEvent(zone).applyAndSendToClient(context.client);
		return true;
	}

	public static boolean handleShrinkWorkZone(SettlementDependantContainer container, PacketReader reader) {
		ServerContext context = getNonSurfaceContext(container);
		if (context == null) return false;
		int uniqueID = reader.getNextInt();
		Rectangle rectangle = readRectangle(reader);
		SettlementWorkZone current = SettlementIndependentZoneSystem.getWorkZone(context.settlement, uniqueID);
		if (current == null || !isIndependentVanillaWorkZoneID(current.getID())) {
			broadcastWork(context.settlement);
			return true;
		}
		SettlementIndependentZoneSystem.shrinkWorkZone(context.settlement, uniqueID, context.levelIdentifier, rectangle);
		broadcastWork(context.settlement);
		return true;
	}

	public static boolean handleCreateRestrict(ServerSettlementData data, ServerClient client) {
		if (data == null || client == null || client.getLevel() == null) return false;
		LevelIdentifier level = client.getLevel().getIdentifier();
		if (!SettlementIndependentZoneSystem.isDomainLevel(data, level)) return false;
		SettlementIndependentZoneSystem.createRestrictZone(data, level);
		broadcastRestrict(data);
		return true;
	}

	public static boolean handleCloneRestrict(PacketReader reader, ServerSettlementData data, ServerClient client) {
		int uniqueID = reader.getNextInt();
		LevelIdentifier owner = SettlementIndependentZoneSystem.getRestrictZoneLevel(data, uniqueID);
		if (owner == null) return false;
		SettlementIndependentZoneSystem.cloneRestrictZone(data, uniqueID);
		broadcastRestrict(data);
		return true;
	}

	public static boolean handleChangeRestrict(PacketReader reader, ServerSettlementData data, ServerClient client) {
		int uniqueID = reader.getNextInt();
		LevelIdentifier owner = SettlementIndependentZoneSystem.getRestrictZoneLevel(data, uniqueID);
		if (owner == null) return false;
		LevelIdentifier currentLevel = client == null || client.getLevel() == null ? null : client.getLevel().getIdentifier();
		if (currentLevel != null && owner.equals(currentLevel) && SettlementIndependentZoneSystem.isSurfaceLevel(data, owner)) return false;
		ZoningChange change = ZoningChange.fromPacket(reader);
		if (currentLevel == null || !owner.equals(currentLevel)) {
			if (client != null) client.sendChatMessage(new LocalMessage("misc", "settlementrestrictionotherlevel"));
			broadcastRestrict(data);
			return true;
		}
		SettlementIndependentZoneSystem.changeRestrictZone(data, uniqueID, owner, change);
		broadcastRestrict(data);
		return true;
	}

	public static boolean handleRenameRestrict(PacketReader reader, ServerSettlementData data) {
		int uniqueID = reader.getNextInt();
		LevelIdentifier owner = SettlementIndependentZoneSystem.getRestrictZoneLevel(data, uniqueID);
		if (owner == null || SettlementIndependentZoneSystem.isSurfaceLevel(data, owner)) return false;
		SettlementIndependentZoneSystem.renameRestrictZone(data, uniqueID, reader.getNextString());
		broadcastRestrict(data);
		return true;
	}

	public static boolean handleRecolorRestrict(PacketReader reader, ServerSettlementData data) {
		int uniqueID = reader.getNextInt();
		LevelIdentifier owner = SettlementIndependentZoneSystem.getRestrictZoneLevel(data, uniqueID);
		if (owner == null || SettlementIndependentZoneSystem.isSurfaceLevel(data, owner)) return false;
		SettlementIndependentZoneSystem.recolorRestrictZone(data, uniqueID, reader.getNextMaxValue(360));
		broadcastRestrict(data);
		return true;
	}

	public static boolean handleDeleteRestrict(PacketReader reader, ServerSettlementData data) {
		int uniqueID = reader.getNextInt();
		LevelIdentifier owner = SettlementIndependentZoneSystem.getRestrictZoneLevel(data, uniqueID);
		if (owner == null || SettlementIndependentZoneSystem.isSurfaceLevel(data, owner)) return false;
		SettlementIndependentZoneSystem.deleteRestrictZone(data, uniqueID);
		broadcastRestrict(data);
		return true;
	}

	public static void broadcastWork(ServerSettlementData settlement) {
		if (settlement == null || settlement.getServer() == null) return;
		SettlementIndependentZoneNetwork.broadcast(settlement);
		SettlementWorkZonesEvent event = new SettlementWorkZonesEvent(settlement);
		for (Object value : settlement.getServer().getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient target = (ServerClient)value;
			if (!(target.getContainer() instanceof SettlementDependantContainer)) continue;
			ServerSettlementData targetSettlement = ((SettlementDependantContainer)target.getContainer()).getServerData();
			if (targetSettlement != null && targetSettlement.uniqueID == settlement.uniqueID && settlement.networkData.doesClientHaveAccess(target)) event.applyAndSendToClient(target);
		}
	}

	public static void broadcastRestrict(ServerSettlementData settlement) {
		if (settlement == null || settlement.getServer() == null) return;
		SettlementRestrictZonesFullEvent event = new SettlementRestrictZonesFullEvent(settlement);
		for (Object value : settlement.getServer().getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient target = (ServerClient)value;
			if (!(target.getContainer() instanceof SettlementDependantContainer)) continue;
			ServerSettlementData targetSettlement = ((SettlementDependantContainer)target.getContainer()).getServerData();
			if (targetSettlement != null && targetSettlement.uniqueID == settlement.uniqueID && settlement.networkData.doesClientHaveAccess(target)) event.applyAndSendToClient(target);
		}
		SettlementIndependentZoneNetwork.broadcast(settlement);
	}

	private static ServerContext getNonSurfaceContext(SettlementDependantContainer container) {
		if (container == null || !container.client.isServer()) return null;
		ServerSettlementData settlement = container.getServerData();
		ServerClient client = container.client.getServerClient();
		if (settlement == null || client == null || client.getLevel() == null || !settlement.networkData.doesClientHaveAccess(client)) return null;
		LevelIdentifier level = client.getLevel().getIdentifier();
		if (SettlementIndependentZoneSystem.isSurfaceLevel(settlement, level) || !SettlementIndependentZoneSystem.isDomainLevel(settlement, level)) return null;
		return new ServerContext(settlement, level, client);
	}

	private static boolean isIndependentVanillaWorkZoneID(int zoneID) {
		return zoneID == SettlementWorkZoneRegistry.FORESTRY_ID
				|| zoneID == SettlementWorkZoneRegistry.HUSBANDRY_ID
				|| zoneID == SettlementWorkZoneRegistry.FERTILIZE_ID;
	}

	private static Rectangle readRectangle(PacketReader reader) {
		return new Rectangle(reader.getNextInt(), reader.getNextInt(), reader.getNextShortUnsigned(), reader.getNextShortUnsigned());
	}

	private static Point readOptionalPoint(PacketReader reader) {
		return reader.getNextBoolean() ? new Point(reader.getNextInt(), reader.getNextInt()) : null;
	}

	private static final class ServerContext {
		final ServerSettlementData settlement;
		final LevelIdentifier levelIdentifier;
		final ServerClient client;

		ServerContext(ServerSettlementData settlement, LevelIdentifier levelIdentifier, ServerClient client) {
			this.settlement = settlement;
			this.levelIdentifier = levelIdentifier;
			this.client = client;
		}
	}
}
