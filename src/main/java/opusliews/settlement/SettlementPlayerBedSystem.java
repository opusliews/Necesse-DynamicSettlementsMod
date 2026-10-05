package opusliews.settlement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.server.ServerClient;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.HumanLook;
import necesse.inventory.container.BedContainer;
import necesse.inventory.container.settlement.SettlementContainer;
import necesse.inventory.container.settlement.events.SettlementSettlersChangedEvent;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.furniture.SettlerBedObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementBed;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementCaveBed;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.network.PacketPlayerSettlementBedSync;

public final class SettlementPlayerBedSystem {
	public static final String saveRoot = "DYNAMIC_SETTLEMENTS_PLAYER_BEDS";
	private static final String saveEntry = "PLAYER_BED";

	private static final Map<ServerSettlementData, SettlementState> serverStates = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Integer, List<ClientAssignment>> clientAssignments = new ConcurrentHashMap<>();

	private SettlementPlayerBedSystem() {
	}

	public static void addSaveData(ServerSettlementData settlement, SaveData save) {
		if (settlement == null || save == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cannot save because settlement/save is null settlement=" + settlement + " save=" + save);
			return;
		}

		SettlementState state = serverStates.get(settlement);
		if (state == null || state.assignments.isEmpty()) return;

		SaveData root = new SaveData(saveRoot);
		synchronized (state) {
			for (PlayerBedAssignment assignment : state.assignments.values()) {
				if (assignment == null || assignment.levelIdentifier == null) {
					if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Skipped invalid assignment while saving settlement=" + settlement.uniqueID);
					continue;
				}
				SaveData entry = new SaveData(saveEntry);
				entry.addLong("authentication", assignment.authentication);
				entry.addInt("characterUniqueID", assignment.characterUniqueID);
				entry.addSafeString("playerName", assignment.playerName == null ? "" : assignment.playerName);
				entry.addSafeString("level", assignment.levelIdentifier.stringID);
				entry.addInt("tileX", assignment.tileX);
				entry.addInt("tileY", assignment.tileY);
				SaveData look = new SaveData("LOOK");
				assignment.look.addSaveData(look);
				entry.addSaveData(look);
				root.addSaveData(entry);
			}
		}
		save.addSaveData(root);
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Saved assignments settlement=" + settlement.uniqueID + " count=" + state.assignments.size());
	}

	public static void applyLoadData(ServerSettlementData settlement, LoadData save, int tileXOffset, int tileYOffset) {
		if (settlement == null || save == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cannot load because settlement/save is null settlement=" + settlement + " save=" + save);
			return;
		}

		SettlementState state = new SettlementState();
		LoadData root = save.getFirstLoadDataByName(saveRoot);
		if (root != null) {
			for (LoadData entry : root.getLoadDataByName(saveEntry)) {
				try {
					long authentication = entry.getLong("authentication", 0L, false);
					int characterUniqueID = entry.getInt("characterUniqueID", 0, false);
					String playerName = entry.getSafeString("playerName", "", false);
					String levelString = entry.getSafeString("level", null, false);
					if (levelString == null || levelString.isEmpty()) {
						if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Ignored saved assignment with missing level settlement=" + settlement.uniqueID + " auth=" + authentication + " character=" + characterUniqueID);
						continue;
					}
					LevelIdentifier levelIdentifier = new LevelIdentifier(levelString);
					int tileX = entry.getInt("tileX", 0, false) + tileXOffset;
					int tileY = entry.getInt("tileY", 0, false) + tileYOffset;
					HumanLook look = new HumanLook();
					LoadData lookData = entry.getFirstLoadDataByName("LOOK");
					if (lookData != null) look.applyLoadData(lookData);
					else if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Saved assignment has no LOOK; using default appearance settlement=" + settlement.uniqueID + " auth=" + authentication + " character=" + characterUniqueID);

					PlayerBedAssignment assignment = new PlayerBedAssignment(authentication, characterUniqueID, playerName, look, levelIdentifier, tileX, tileY);
					PlayerKey key = assignment.getKey();
					PlayerBedAssignment duplicateBed = findAssignmentAt(state, levelIdentifier, tileX, tileY, key);
					if (duplicateBed != null) {
						if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Ignored duplicate saved bed reservation settlement=" + settlement.uniqueID + " bed=" + levelIdentifier + "@" + tileX + "," + tileY + " existingPlayer=" + duplicateBed.playerName + " duplicatePlayer=" + playerName);
						continue;
					}
					state.assignments.put(key, assignment);
				} catch (Exception e) {
					if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Failed loading saved assignment settlement=" + settlement.uniqueID + " error=" + e.getMessage());
				}
			}
		}

		if (state.assignments.isEmpty()) serverStates.remove(settlement);
		else serverStates.put(settlement, state);
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Loaded assignments settlement=" + settlement.uniqueID + " count=" + state.assignments.size() + " tileOffset=" + tileXOffset + "," + tileYOffset);
	}

	public static void onBedDestroyed(Level level, int tileX, int tileY, GameObject object) {
		if (level == null || level.getServer() == null || !(object instanceof SettlerBedObject)) return;

		LevelObject master = ((SettlerBedObject)object).getSettlerBedMasterLevelObject(level, tileX, tileY);
		if (master == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Could not resolve destroyed bed master level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return;
		}

		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(level.getServer(), level.getIdentifier(), master.tileX, master.tileY);
		if (domain == null || domain.getSettlement() == null) return;
		ServerSettlementData settlement = domain.getSettlement();
		SettlementState state = serverStates.get(settlement);
		if (state == null) return;

		PlayerBedAssignment removed = null;
		synchronized (state) {
			PlayerBedAssignment assignment = findAssignmentAt(state, level.getIdentifier(), master.tileX, master.tileY, null);
			if (assignment != null) {
				state.assignments.remove(assignment.getKey());
				removed = assignment;
			}
		}

		if (removed == null) return;
		if (state.assignments.isEmpty()) serverStates.remove(settlement);
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Removed player bed because bed was destroyed settlement=" + settlement.uniqueID
				+ " player=" + removed.playerName + " bed=" + removed.levelIdentifier + "@" + removed.tileX + "," + removed.tileY);
		broadcastAssignmentSync(settlement);
	}

	public static boolean isPlayerReserved(SettlementBed bed) {
		if (bed == null || bed.data == null) return false;
		LevelIdentifier levelIdentifier = getBedLevelIdentifier(bed);
		if (levelIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Could not resolve level for settlement bed settlement=" + bed.data.uniqueID + " tile=" + bed.tileX + "," + bed.tileY);
			return false;
		}
		return isPlayerReserved(bed.data, levelIdentifier, bed.tileX, bed.tileY);
	}

	public static boolean isPlayerReserved(ServerSettlementData settlement, LevelIdentifier levelIdentifier, int tileX, int tileY) {
		if (settlement == null || levelIdentifier == null) return false;
		SettlementState state = serverStates.get(settlement);
		if (state == null) return false;
		synchronized (state) {
			return findAssignmentAt(state, levelIdentifier, tileX, tileY, null) != null;
		}
	}

	public static PlayerBedAssignment beginManualSettlerBedAssignment(ServerSettlementData settlement, SettlementBed bed, ServerClient client) {
		if (settlement == null || bed == null || client == null) return null;
		LevelIdentifier levelIdentifier = getBedLevelIdentifier(bed);
		if (levelIdentifier == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Manual settler assignment could not resolve bed level settlement=" + settlement.uniqueID + " tile=" + bed.tileX + "," + bed.tileY);
			return null;
		}

		SettlementState state = serverStates.get(settlement);
		if (state == null) return null;
		PlayerKey key = PlayerKey.from(client);
		synchronized (state) {
			PlayerBedAssignment assignment = state.assignments.get(key);
			if (assignment == null || !assignment.matches(levelIdentifier, bed.tileX, bed.tileY)) return null;
			state.assignments.remove(key);
			if (state.assignments.isEmpty()) serverStates.remove(settlement);
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Temporarily released requester's own player bed for manual settler assignment client=" + client.getName()
					+ " settlement=" + settlement.uniqueID + " bed=" + levelIdentifier + "@" + bed.tileX + "," + bed.tileY);
			return assignment;
		}
	}

	public static void finishManualSettlerBedAssignment(ServerSettlementData settlement, ServerClient client, PlayerBedAssignment releasedAssignment, boolean success) {
		if (settlement == null || client == null || releasedAssignment == null) return;
		if (!success) {
			SettlementState state = getOrCreateState(settlement);
			synchronized (state) {
				state.assignments.put(releasedAssignment.getKey(), releasedAssignment);
			}
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Restored player bed after failed manual settler assignment client=" + client.getName()
					+ " settlement=" + settlement.uniqueID + " bed=" + releasedAssignment.levelIdentifier + "@" + releasedAssignment.tileX + "," + releasedAssignment.tileY);
			return;
		}

		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Manual settler assignment replaced requester's own player bed client=" + client.getName()
				+ " settlement=" + settlement.uniqueID + " bed=" + releasedAssignment.levelIdentifier + "@" + releasedAssignment.tileX + "," + releasedAssignment.tileY);
		sendManualAssignmentSync(settlement, client);
	}

	private static void sendManualAssignmentSync(ServerSettlementData settlement, ServerClient requester) {
		if (settlement == null || requester == null) return;
		requester.sendPacket(buildSyncPacket(settlement));
		if (settlement.getServer() == null) return;
		for (Object value : settlement.getServer().getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient other = (ServerClient)value;
			if (other == requester) continue;
			if (!(other.getContainer() instanceof SettlementContainer)) continue;
			SettlementContainer container = (SettlementContainer)other.getContainer();
			if (container.getSettlementUniqueID() != settlement.uniqueID) continue;
			other.sendPacket(buildSyncPacket(settlement));
		}
	}

	public static void handleBedQuery(ServerClient client) {
		BedContext context = resolveCurrentBedContext(client, false);
		if (context == null) {
			if (client != null) client.sendPacket(PacketPlayerSettlementBedSync.noSettlementContext());
			return;
		}

		PlayerKey key = PlayerKey.from(client);
		boolean ownBed = isCurrentPlayersBed(context.settlement, key, context.level.getIdentifier(), context.tileX, context.tileY);
		client.sendPacket(buildSyncPacket(context.settlement, context.level.getIdentifier(), context.tileX, context.tileY, true, ownBed));
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Sent bed query result client=" + client.getName() + " settlement=" + context.settlement.uniqueID + " bed=" + context.level.getIdentifier() + "@" + context.tileX + "," + context.tileY + " own=" + ownBed);
	}

	public static void handleBedToggle(ServerClient client) {
		BedContext context = resolveCurrentBedContext(client, true);
		if (context == null) {
			if (client != null) client.sendPacket(PacketPlayerSettlementBedSync.noSettlementContext());
			return;
		}

		PlayerKey key = PlayerKey.from(client);
		SettlementState state = getOrCreateState(context.settlement);
		PlayerBedAssignment previous;
		synchronized (state) {
			previous = state.assignments.get(key);
			if (previous != null && previous.matches(context.level.getIdentifier(), context.tileX, context.tileY)) {
				state.assignments.remove(key);
				if (state.assignments.isEmpty()) serverStates.remove(context.settlement);
				if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cleared player bed client=" + client.getName() + " settlement=" + context.settlement.uniqueID + " bed=" + context.level.getIdentifier() + "@" + context.tileX + "," + context.tileY);
				client.sendChatMessage(new LocalMessage("misc", "playersettlementbedcleared"));
				syncAfterChange(context.settlement, client, context.level.getIdentifier(), context.tileX, context.tileY, false);
				return;
			}

			PlayerBedAssignment occupiedByPlayer = findAssignmentAt(state, context.level.getIdentifier(), context.tileX, context.tileY, key);
			if (occupiedByPlayer != null) {
				if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected assignment because another player owns bed requester=" + client.getName() + " owner=" + occupiedByPlayer.playerName + " settlement=" + context.settlement.uniqueID + " bed=" + context.level.getIdentifier() + "@" + context.tileX + "," + context.tileY);
				client.sendChatMessage(new LocalMessage("misc", "playersettlementbedoccupied"));
				client.sendPacket(buildSyncPacket(context.settlement, context.level.getIdentifier(), context.tileX, context.tileY, true, false));
				return;
			}

			PlayerMob player = client.playerMob;
			HumanLook look = player == null || player.look == null ? new HumanLook() : new HumanLook(player.look);
			PlayerBedAssignment newAssignment = new PlayerBedAssignment(client.authentication, client.getCharacterUniqueID(), client.getName(), look, context.level.getIdentifier(), context.tileX, context.tileY);
			state.assignments.put(key, newAssignment);
		}

		if (!clearSettlerFromTargetBed(context)) {
			synchronized (state) {
				if (previous == null) state.assignments.remove(key);
				else state.assignments.put(key, previous);
				if (state.assignments.isEmpty()) serverStates.remove(context.settlement);
			}
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Assignment rolled back because NPC bed occupant could not be cleared client=" + client.getName() + " settlement=" + context.settlement.uniqueID + " bed=" + context.level.getIdentifier() + "@" + context.tileX + "," + context.tileY);
			client.sendChatMessage(new LocalMessage("misc", "playersettlementbedfailed"));
			client.sendPacket(buildSyncPacket(context.settlement, context.level.getIdentifier(), context.tileX, context.tileY, true, false));
			return;
		}

		context.settlement.sendEvent(SettlementSettlersChangedEvent.class);
		client.sendChatMessage(new LocalMessage("misc", "playersettlementbedset"));
		if (Logging.logEnabled) {
			Logging.logMessage("[PlayerSettlementBed] Assigned player bed client=" + client.getName() + " settlement=" + context.settlement.uniqueID
					+ " bed=" + context.level.getIdentifier() + "@" + context.tileX + "," + context.tileY
					+ (previous == null ? " previous=none" : " previous=" + previous.levelIdentifier + "@" + previous.tileX + "," + previous.tileY));
		}
		syncAfterChange(context.settlement, client, context.level.getIdentifier(), context.tileX, context.tileY, true);
	}

	public static PacketPlayerSettlementBedSync buildSyncPacket(ServerSettlementData settlement) {
		return buildSyncPacket(settlement, null, 0, 0, false, false);
	}

	public static PacketPlayerSettlementBedSync buildSyncPacket(ServerSettlementData settlement, LevelIdentifier contextLevel, int contextTileX, int contextTileY, boolean hasContext, boolean currentBedAssignedToPlayer) {
		if (settlement == null) return PacketPlayerSettlementBedSync.noSettlementContext();
		cleanupInvalidLoadedAssignments(settlement);
		List<PlayerBedAssignment> snapshot = getAssignmentsSnapshot(settlement);
		refreshConnectedPlayerLooks(settlement, snapshot);
		return new PacketPlayerSettlementBedSync(settlement.uniqueID, snapshot, hasContext, contextLevel, contextTileX, contextTileY, currentBedAssignedToPlayer);
	}

	public static void sendSettlementSync(ServerSettlementData settlement, ServerClient client) {
		if (settlement == null || client == null) return;
		client.sendPacket(buildSyncPacket(settlement));
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Sent settlement bed sync settlement=" + settlement.uniqueID + " client=" + client.getName());
	}

	public static void applyClientSync(int settlementUniqueID, List<ClientAssignment> assignments) {
		if (settlementUniqueID == 0) return;
		List<ClientAssignment> copy = assignments == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(assignments));
		clientAssignments.put(settlementUniqueID, copy);
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Client applied assignment sync settlement=" + settlementUniqueID + " count=" + copy.size());
	}

	public static List<ClientAssignment> getClientAssignments(int settlementUniqueID) {
		List<ClientAssignment> result = clientAssignments.get(settlementUniqueID);
		return result == null ? Collections.emptyList() : result;
	}

	public static void clearClientState() {
		clientAssignments.clear();
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cleared client assignment cache");
	}

	private static BedContext resolveCurrentBedContext(ServerClient client, boolean sendErrors) {
		if (client == null) return null;
		if (!(client.getContainer() instanceof BedContainer)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected bed action because client is not in a BedContainer client=" + client.getName());
			if (sendErrors) client.sendChatMessage(new LocalMessage("misc", "playersettlementbednotinbed"));
			return null;
		}

		BedContainer container = (BedContainer)client.getContainer();
		Level level = container.objectEntity == null ? null : container.objectEntity.getLevel();
		if (level == null || level.getServer() == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected bed action because bed level/server is unavailable client=" + client.getName());
			if (sendErrors) client.sendChatMessage(new LocalMessage("misc", "playersettlementbedfailed"));
			return null;
		}

		if (client.getLevel() == null || !client.getLevel().getIdentifier().equals(level.getIdentifier())) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected bed action because player and BedContainer levels differ client=" + client.getName() + " playerLevel=" + (client.getLevel() == null ? "null" : client.getLevel().getIdentifier()) + " bedLevel=" + level.getIdentifier());
			if (sendErrors) client.sendChatMessage(new LocalMessage("misc", "playersettlementbedfailed"));
			return null;
		}

		if (!(level.getObject(container.tileX, container.tileY) instanceof SettlerBedObject)
				|| !((SettlerBedObject)level.getObject(container.tileX, container.tileY)).isMasterBedObject(level, container.tileX, container.tileY)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected bed action because current bed is not a valid master settler bed client=" + client.getName() + " level=" + level.getIdentifier() + " tile=" + container.tileX + "," + container.tileY + " object=" + level.getObject(container.tileX, container.tileY).getStringID());
			if (sendErrors) client.sendChatMessage(new LocalMessage("misc", "playersettlementbedinvalid"));
			return null;
		}

		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(level.getServer(), level.getIdentifier(), container.tileX, container.tileY);
		if (domain == null || domain.getSettlement() == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Current bed is not inside a player settlement client=" + client.getName() + " level=" + level.getIdentifier() + " tile=" + container.tileX + "," + container.tileY);
			if (sendErrors) client.sendChatMessage(new LocalMessage("misc", "playersettlementbednosettlement"));
			return null;
		}

		ServerSettlementData settlement = domain.getSettlement();
		if (settlement.networkData == null || !settlement.networkData.doesClientHaveAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Rejected bed action because client lacks settlement access client=" + client.getName() + " settlement=" + settlement.uniqueID);
			if (sendErrors) client.sendChatMessage(new LocalMessage("misc", "playersettlementbednoaccess"));
			return null;
		}

		return new BedContext(settlement, level, container.tileX, container.tileY);
	}

	private static boolean clearSettlerFromTargetBed(BedContext context) {
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(context.settlement);
		if (domain == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cannot clear NPC from target bed because settlement domain is missing settlement=" + context.settlement.uniqueID);
			return false;
		}
		SettlementLevelType type = domain.getLevelType(context.level.getIdentifier());
		if (type == SettlementLevelType.CAVE) {
			SettlementCaveBed bed = SettlementCaveBedSystem.getOrCreateCaveBed(context.settlement, context.tileX, context.tileY);
			if (bed == null) {
				if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cannot reserve cave bed because cave SettlementBed could not be created settlement=" + context.settlement.uniqueID + " bed=" + context.tileX + "," + context.tileY);
				return false;
			}
			LevelSettler occupant = bed.getSettler();
			if (occupant == null) return true;
			boolean cleared = SettlementCaveBedSystem.clearCaveBed(occupant, true);
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Clearing cave bed NPC occupant settlement=" + context.settlement.uniqueID + " bed=" + context.tileX + "," + context.tileY + " settler=" + occupant.mobUniqueID + " success=" + cleared);
			return cleared;
		}
		if (type == SettlementLevelType.SURFACE) {
			SettlementBed bed = context.settlement.addOrValidateBed(context.tileX, context.tileY);
			if (bed == null) {
				if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cannot reserve surface bed because settlement bed validation failed settlement=" + context.settlement.uniqueID + " bed=" + context.tileX + "," + context.tileY);
				return false;
			}
			LevelSettler occupant = bed.getSettler();
			if (occupant == null) return true;
			boolean cleared = bed.clearSettler();
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Clearing surface bed NPC occupant settlement=" + context.settlement.uniqueID + " bed=" + context.tileX + "," + context.tileY + " settler=" + occupant.mobUniqueID + " success=" + cleared);
			return cleared;
		}

		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Cannot reserve bed on unsupported settlement level settlement=" + context.settlement.uniqueID + " level=" + context.level.getIdentifier());
		return false;
	}

	private static void syncAfterChange(ServerSettlementData settlement, ServerClient requester, LevelIdentifier currentBedLevel, int currentBedX, int currentBedY, boolean requesterOwnsCurrentBed) {
		requester.sendPacket(buildSyncPacket(settlement, currentBedLevel, currentBedX, currentBedY, true, requesterOwnsCurrentBed));
		if (settlement == null || settlement.getServer() == null) return;
		for (Object value : settlement.getServer().getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient other = (ServerClient)value;
			if (other == requester) continue;
			if (!(other.getContainer() instanceof SettlementContainer)) continue;
			SettlementContainer container = (SettlementContainer)other.getContainer();
			if (container.getSettlementUniqueID() != settlement.uniqueID) continue;
			other.sendPacket(buildSyncPacket(settlement));
		}
	}

	private static void broadcastAssignmentSync(ServerSettlementData settlement) {
		if (settlement == null || settlement.getServer() == null) return;
		PacketPlayerSettlementBedSync packet = buildSyncPacket(settlement);
		for (Object value : settlement.getServer().getClients()) {
			if (!(value instanceof ServerClient)) continue;
			ServerClient client = (ServerClient)value;
			if (!(client.getContainer() instanceof SettlementContainer)) continue;
			SettlementContainer container = (SettlementContainer)client.getContainer();
			if (container.getSettlementUniqueID() != settlement.uniqueID) continue;
			client.sendPacket(packet);
		}
	}

	private static boolean isCurrentPlayersBed(ServerSettlementData settlement, PlayerKey key, LevelIdentifier levelIdentifier, int tileX, int tileY) {
		SettlementState state = serverStates.get(settlement);
		if (state == null) return false;
		synchronized (state) {
			PlayerBedAssignment assignment = state.assignments.get(key);
			return assignment != null && assignment.matches(levelIdentifier, tileX, tileY);
		}
	}

	private static SettlementState getOrCreateState(ServerSettlementData settlement) {
		synchronized (serverStates) {
			SettlementState state = serverStates.get(settlement);
			if (state == null) {
				state = new SettlementState();
				serverStates.put(settlement, state);
			}
			return state;
		}
	}

	private static List<PlayerBedAssignment> getAssignmentsSnapshot(ServerSettlementData settlement) {
		SettlementState state = serverStates.get(settlement);
		if (state == null) return Collections.emptyList();
		synchronized (state) {
			return new ArrayList<>(state.assignments.values());
		}
	}

	private static void refreshConnectedPlayerLooks(ServerSettlementData settlement, List<PlayerBedAssignment> snapshot) {
		if (settlement == null || settlement.getServer() == null || snapshot == null || snapshot.isEmpty()) return;
		for (PlayerBedAssignment assignment : snapshot) {
			for (Object value : settlement.getServer().getClients()) {
				if (!(value instanceof ServerClient)) continue;
				ServerClient client = (ServerClient)value;
				if (client.authentication != assignment.authentication || client.getCharacterUniqueID() != assignment.characterUniqueID) continue;
				assignment.playerName = client.getName();
				if (client.playerMob != null && client.playerMob.look != null) assignment.look = new HumanLook(client.playerMob.look);
				break;
			}
		}
	}

	private static void cleanupInvalidLoadedAssignments(ServerSettlementData settlement) {
		SettlementState state = serverStates.get(settlement);
		if (state == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;

		List<PlayerKey> invalid = new ArrayList<>();
		synchronized (state) {
			for (Map.Entry<PlayerKey, PlayerBedAssignment> entry : state.assignments.entrySet()) {
				PlayerBedAssignment assignment = entry.getValue();
				SettlementLevelType type = domain.getLevelType(assignment.levelIdentifier);
				if (type == null) {
					invalid.add(entry.getKey());
					continue;
				}
				Level level = domain.getLoadedLevel(type);
				if (level == null) continue;
				if (!SettlementBed.isValidBed(level, assignment.tileX, assignment.tileY)) invalid.add(entry.getKey());
			}
			for (PlayerKey key : invalid) {
				PlayerBedAssignment removed = state.assignments.remove(key);
				if (removed != null && Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Removed invalid persisted assignment settlement=" + settlement.uniqueID + " player=" + removed.playerName + " bed=" + removed.levelIdentifier + "@" + removed.tileX + "," + removed.tileY);
			}
		}
		if (state.assignments.isEmpty()) serverStates.remove(settlement);
	}

	private static LevelIdentifier getBedLevelIdentifier(SettlementBed bed) {
		if (bed instanceof SettlementCaveBed) {
			Level cave = ((SettlementCaveBed)bed).getBedLevel();
			return cave == null ? null : cave.getIdentifier();
		}
		return bed.data == null || bed.data.getLevel() == null ? null : bed.data.getLevel().getIdentifier();
	}

	private static PlayerBedAssignment findAssignmentAt(SettlementState state, LevelIdentifier levelIdentifier, int tileX, int tileY, PlayerKey ignoreKey) {
		if (state == null || levelIdentifier == null) return null;
		for (Map.Entry<PlayerKey, PlayerBedAssignment> entry : state.assignments.entrySet()) {
			if (ignoreKey != null && ignoreKey.equals(entry.getKey())) continue;
			PlayerBedAssignment assignment = entry.getValue();
			if (assignment != null && assignment.matches(levelIdentifier, tileX, tileY)) return assignment;
		}
		return null;
	}

	private static final class SettlementState {
		private final Map<PlayerKey, PlayerBedAssignment> assignments = new LinkedHashMap<>();
	}

	private static final class BedContext {
		private final ServerSettlementData settlement;
		private final Level level;
		private final int tileX;
		private final int tileY;

		private BedContext(ServerSettlementData settlement, Level level, int tileX, int tileY) {
			this.settlement = settlement;
			this.level = level;
			this.tileX = tileX;
			this.tileY = tileY;
		}
	}

	private static final class PlayerKey {
		private final long authentication;
		private final int characterUniqueID;

		private PlayerKey(long authentication, int characterUniqueID) {
			this.authentication = authentication;
			this.characterUniqueID = characterUniqueID;
		}

		private static PlayerKey from(ServerClient client) {
			return new PlayerKey(client.authentication, client.getCharacterUniqueID());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof PlayerKey)) return false;
			PlayerKey other = (PlayerKey)obj;
			return authentication == other.authentication && characterUniqueID == other.characterUniqueID;
		}

		@Override
		public int hashCode() {
			return Objects.hash(authentication, characterUniqueID);
		}
	}

	public static final class PlayerBedAssignment {
		public final long authentication;
		public final int characterUniqueID;
		public String playerName;
		public HumanLook look;
		public final LevelIdentifier levelIdentifier;
		public final int tileX;
		public final int tileY;

		public PlayerBedAssignment(long authentication, int characterUniqueID, String playerName, HumanLook look, LevelIdentifier levelIdentifier, int tileX, int tileY) {
			this.authentication = authentication;
			this.characterUniqueID = characterUniqueID;
			this.playerName = playerName == null ? "" : playerName;
			this.look = look == null ? new HumanLook() : new HumanLook(look);
			this.levelIdentifier = levelIdentifier;
			this.tileX = tileX;
			this.tileY = tileY;
		}

		private PlayerKey getKey() {
			return new PlayerKey(authentication, characterUniqueID);
		}

		public boolean matches(LevelIdentifier levelIdentifier, int tileX, int tileY) {
			return this.levelIdentifier != null && this.levelIdentifier.equals(levelIdentifier) && this.tileX == tileX && this.tileY == tileY;
		}
	}

	public static final class ClientAssignment {
		public final String playerName;
		public final HumanLook look;
		public final LevelIdentifier levelIdentifier;
		public final int tileX;
		public final int tileY;

		public ClientAssignment(String playerName, HumanLook look, LevelIdentifier levelIdentifier, int tileX, int tileY) {
			this.playerName = playerName == null ? "" : playerName;
			this.look = look == null ? new HumanLook() : new HumanLook(look);
			this.levelIdentifier = levelIdentifier;
			this.tileX = tileX;
			this.tileY = tileY;
		}
	}
}
