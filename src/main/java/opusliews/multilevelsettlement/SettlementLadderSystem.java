package opusliews.multilevelsettlement;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.PortalObjectEntity;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;
import opusliews.network.PacketToggleSettlementLadder;

import java.util.ArrayList;
import java.util.List;

public final class SettlementLadderSystem {
	private static final String settlementFlagItemID = "settlementflag";

	private SettlementLadderSystem() {
	}

	public static boolean handlePlayerInteract(LevelObject levelObject, PlayerMob player) {
		if (levelObject == null || player == null || levelObject.level == null) return false;
		if (!isSupportedLadderObject(levelObject.object)) return false;

		if (!isHoldingSettlementFlag(player)) {
			if (Logging.logEnabled && levelObject.level.isClient()) {
				InventoryItem selected = player.getSelectedItem();
				Logging.logMessage("[SettlementLadder] Client ladder interaction is ordinary because Settlement Flag is not selected level=" + levelObject.level.getIdentifier() + " tile=" + levelObject.tileX + "," + levelObject.tileY + " selected=" + (selected == null || selected.item == null ? "none" : selected.item.getStringID()));
			}
			return false;
		}

		Level level = levelObject.level;
		int tileX = levelObject.tileX;
		int tileY = levelObject.tileY;

		if (level.isClient()) {
			if (level.getClient() == null) {
				if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Client could not send toggle packet because level has no client level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
				return true;
			}
			level.getClient().network.sendPacket(new PacketToggleSettlementLadder(level.getIdentifierHashCode(), tileX, tileY));
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Client sent explicit settlement-ladder toggle request level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return true;
		}

		if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Suppressed direct server ladder interaction while Settlement Flag is selected level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
		return true;
	}

	public static void handleTogglePacket(Server server, ServerClient client, int levelIdentifierHashCode, int tileX, int tileY) {
		if (server == null || client == null || client.playerMob == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle packet rejected due to missing server/client/player");
			return;
		}

		Level level = server.world.getLevel(client);
		if (level == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle packet rejected because player level could not be resolved player=" + client.authentication);
			return;
		}
		if (level.getIdentifierHashCode() != levelIdentifierHashCode) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle packet rejected due to level mismatch player=" + client.authentication + " actual=" + level.getIdentifier() + " packetHash=" + levelIdentifierHashCode);
			return;
		}
		if (!isHoldingSettlementFlag(client.playerMob)) {
			if (Logging.logEnabled) {
				InventoryItem selected = client.playerMob.getSelectedItem();
				Logging.logMessage("[SettlementLadder] Toggle packet rejected because Settlement Flag is not selected player=" + client.authentication + " selected=" + (selected == null || selected.item == null ? "none" : selected.item.getStringID()));
			}
			return;
		}
		if (!isSupportedLadderObject(level.getObject(tileX, tileY))) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle packet rejected because target is not a supported ladder level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY + " object=" + level.getObject(tileX, tileY).getStringID());
			return;
		}
		if (!level.getLevelObject(tileX, tileY).isInInteractRange(client.playerMob)) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle packet rejected because ladder is out of interaction range player=" + client.authentication + " level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return;
		}

		toggleDesignation(level, tileX, tileY, client);
	}

	public static List<SettlementLadderLink> getDesignatedLinks(SettlementLevelDomain domain) {
		ArrayList<SettlementLadderLink> result = new ArrayList<>();
		if (domain == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Cannot get designated links for null settlement domain");
			return result;
		}

		Level surface = getSurfaceLevel(domain, false);
		if (surface == null) return result;
		SettlementLadderLevelData data = SettlementLadderLevelData.get(surface, false);
		if (data == null) return result;
		result.addAll(data.getLinks(domain.getSettlementUniqueID()));
		return result;
	}

	public static List<SettlementLadderLink> getValidLinks(SettlementLevelDomain domain) {
		return getValidLinks(domain, false);
	}

	public static List<SettlementLadderLink> getValidLinks(SettlementLevelDomain domain, boolean loadLevels) {
		ArrayList<SettlementLadderLink> valid = new ArrayList<>();
		if (domain == null) return valid;
		for (SettlementLadderLink link : getDesignatedLinks(domain)) {
			if (validateLink(domain, link, loadLevels)) valid.add(link);
		}
		return valid;
	}

	public static boolean transitionMob(Mob mob, SettlementLadderLink link, SettlementLevelType destinationType) {
		if (mob == null || link == null || destinationType == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition rejected due to null argument mob=" + mob + " link=" + link + " destinationType=" + destinationType);
			return false;
		}
		if (mob instanceof PlayerMob) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition API rejected player mob=" + mob.getUniqueID());
			return false;
		}

		Level currentLevel = mob.getLevel();
		if (currentLevel == null || !currentLevel.isServer() || currentLevel.getServer() == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition rejected because mob has no server level mob=" + mob.getUniqueID());
			return false;
		}

		ServerSettlementData settlement = SettlementsWorldData.getSettlementsData(currentLevel.getServer()).getServerData(link.settlementUniqueID);
		if (settlement == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition cannot resolve settlement=" + link.settlementUniqueID + " mob=" + mob.getUniqueID());
			return false;
		}
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || !validateLink(domain, link, true)) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition rejected invalid link settlement=" + link.settlementUniqueID + " mob=" + mob.getUniqueID() + " link=" + link);
			return false;
		}

		SettlementLevelType currentType = domain.getLevelType(currentLevel.getIdentifier());
		if (currentType == null || currentType == destinationType) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition rejected invalid source/destination mob=" + mob.getUniqueID() + " currentType=" + currentType + " destinationType=" + destinationType);
			return false;
		}
		if (!link.matches(currentType, mob.getTileX(), mob.getTileY())) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition rejected because mob is not on designated ladder mob=" + mob.getUniqueID() + " mobTile=" + mob.getTileX() + "," + mob.getTileY() + " link=" + link);
			return false;
		}

		Level destination = getDomainLevel(domain, destinationType, true);
		if (destination == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] NPC transition could not load destination mob=" + mob.getUniqueID() + " destinationType=" + destinationType);
			return false;
		}

		int destinationTileX = link.getTileX(destinationType);
		int destinationTileY = link.getTileY(destinationType);
		int destinationX = destinationTileX * 32 + 16;
		int destinationY = destinationTileY * 32 + 16;
		currentLevel.entityManager.changeMobLevel(mob, destination, destinationX, destinationY, true);

		boolean success = mob.getLevel() == destination;
		if (Logging.logEnabled) {
			Logging.logMessage("[SettlementLadder] NPC instant transition " + (success ? "completed" : "FAILED") + " mob=" + mob.getStringID() + "#" + mob.getUniqueID() + " from=" + currentType + " to=" + destinationType + " via=" + link);
		}
		return success;
	}

	public static void onSupportedLadderDestroyed(Level level, int tileX, int tileY, GameObject object) {
		if (level == null || !level.isServer() || level.getServer() == null || !isSupportedLadderObject(object)) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(level.getServer(), level.getIdentifier(), tileX, tileY);
		if (domain == null) return;
		SettlementLevelType levelType = domain.getLevelType(level.getIdentifier());
		if (levelType == null) return;

		Level surface = getSurfaceLevel(domain, false);
		if (surface == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Could not remove designation after ladder destruction because surface level is unavailable settlement=" + domain.getSettlementUniqueID() + " destroyedLevel=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			return;
		}
		SettlementLadderLevelData data = SettlementLadderLevelData.get(surface, false);
		if (data == null) return;
		int removed = data.removeLinksAt(domain, levelType, tileX, tileY);
		if (removed > 0 && Logging.logEnabled) Logging.logMessage("[SettlementLadder] Removed " + removed + " designation(s) because ladder endpoint was destroyed settlement=" + domain.getSettlementUniqueID() + " levelType=" + levelType + " tile=" + tileX + "," + tileY);
	}

	public static boolean isSupportedLadderObject(GameObject object) {
		return isSurfaceLadderObject(object) || isCaveLadderObject(object);
	}

	public static boolean isSurfaceLadderObject(GameObject object) {
		if (object == null) return false;
		String stringID = object.getStringID();
		return "ladderdown".equals(stringID) || "holecaveladder".equals(stringID);
	}

	public static boolean isCaveLadderObject(GameObject object) {
		if (object == null) return false;
		String stringID = object.getStringID();
		return "ladderup".equals(stringID) || "holecaveladderup".equals(stringID);
	}

	private static void toggleDesignation(Level level, int tileX, int tileY, ServerClient client) {
		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(level.getServer(), level.getIdentifier(), tileX, tileY);
		if (domain == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle rejected: no settlement domain level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
			sendMessage(client, "This ladder is not inside a settlement.");
			return;
		}

		CachedSettlementData cached = SettlementsWorldData.getSettlementsData(level.getServer()).getCachedData(domain.getSettlementUniqueID());
		if (cached == null || !cached.hasAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle rejected: player has no settlement access settlement=" + domain.getSettlementUniqueID() + " player=" + client.authentication + " tile=" + tileX + "," + tileY);
			sendMessage(client, "You do not have access to this settlement.");
			return;
		}

		SettlementLevelType levelType = domain.getLevelType(level.getIdentifier());
		if (levelType == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle rejected: source level is not in domain settlement=" + domain.getSettlementUniqueID() + " level=" + level.getIdentifier());
			sendMessage(client, "This ladder is not on a supported settlement level.");
			return;
		}

		Level surface = getSurfaceLevel(domain, true);
		if (surface == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle rejected: could not resolve surface level settlement=" + domain.getSettlementUniqueID());
			sendMessage(client, "Could not access the settlement surface level.");
			return;
		}
		SettlementLadderLevelData data = SettlementLadderLevelData.get(surface, true);
		if (data == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle rejected: could not create ladder level data settlement=" + domain.getSettlementUniqueID());
			sendMessage(client, "Could not update the settlement ladder.");
			return;
		}

		SettlementLadderLink existing = data.findLink(domain.getSettlementUniqueID(), levelType, tileX, tileY);
		if (existing != null) {
			if (!data.removeLink(existing)) {
				if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Failed to remove existing designation settlement=" + domain.getSettlementUniqueID() + " link=" + existing);
				sendMessage(client, "Could not remove the settlement ladder designation.");
				return;
			}
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Disabled " + existing + " by player=" + client.authentication);
			sendMessage(client, "Settlement ladder disabled.");
			return;
		}

		SettlementLadderLink link = buildValidatedLink(domain, level, levelType, tileX, tileY);
		if (link == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Toggle rejected: ladder pair validation failed settlement=" + domain.getSettlementUniqueID() + " levelType=" + levelType + " tile=" + tileX + "," + tileY);
			sendMessage(client, "This ladder does not have a valid surface/cave counterpart.");
			return;
		}
		if (!data.addLink(link)) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Failed to add designation due to duplicate endpoint settlement=" + domain.getSettlementUniqueID() + " link=" + link);
			sendMessage(client, "This ladder conflicts with an existing settlement ladder designation.");
			return;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[SettlementLadder] Enabled " + link + " by player=" + client.authentication);
			SettlementLevelType oppositeType = levelType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;
			SettlementLevelPosition oppositeEndpoint = new SettlementLevelPosition(domain.getLevelIdentifier(oppositeType), link.getTileX(oppositeType), link.getTileY(oppositeType));
			SettlementCrossLevelRoute routeCheck = SettlementCrossLevelRouting.findBestRoute(client.playerMob, domain, oppositeEndpoint, false);
			Logging.logMessage("[SettlementRouting] Designation self-check " + (routeCheck == null ? "FAILED" : "OK") + " player=" + client.authentication + " link=" + link + " route=" + routeCheck);
		}
		sendMessage(client, "Settlement ladder enabled.");
	}

	private static SettlementLadderLink buildValidatedLink(SettlementLevelDomain domain, Level sourceLevel, SettlementLevelType sourceType, int tileX, int tileY) {
		GameObject sourceObject = sourceLevel.getObject(tileX, tileY);
		if ((sourceType == SettlementLevelType.SURFACE && !isSurfaceLadderObject(sourceObject))
				|| (sourceType == SettlementLevelType.CAVE && !isCaveLadderObject(sourceObject))) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Wrong ladder direction for settlement endpoint settlement=" + domain.getSettlementUniqueID() + " levelType=" + sourceType + " tile=" + tileX + "," + tileY + " object=" + sourceObject.getStringID());
			return null;
		}

		if (!domain.isTileWithinBounds(sourceLevel.getIdentifier(), tileX, tileY)) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Source ladder is outside projected settlement bounds settlement=" + domain.getSettlementUniqueID() + " level=" + sourceLevel.getIdentifier() + " tile=" + tileX + "," + tileY);
			return null;
		}

		SettlementLevelType destinationType = sourceType == SettlementLevelType.SURFACE ? SettlementLevelType.CAVE : SettlementLevelType.SURFACE;
		Level destinationLevel = getDomainLevel(domain, destinationType, true);
		if (destinationLevel == null) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Could not load counterpart level settlement=" + domain.getSettlementUniqueID() + " destinationType=" + destinationType);
			return null;
		}

		int destinationTileX = tileX;
		int destinationTileY = tileY;
		ObjectEntity sourceEntity = sourceLevel.entityManager.getObjectEntity(tileX, tileY);
		if (sourceEntity instanceof PortalObjectEntity) {
			PortalObjectEntity portal = (PortalObjectEntity)sourceEntity;
			LevelIdentifier expectedDestination = domain.getLevelIdentifier(destinationType);
			if (!expectedDestination.equals(portal.getDestinationIdentifier())) {
				if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Source portal points outside settlement domain settlement=" + domain.getSettlementUniqueID() + " source=" + sourceLevel.getIdentifier() + " actualDestination=" + portal.getDestinationIdentifier() + " expected=" + expectedDestination);
				return null;
			}
			destinationTileX = portal.destinationTileX;
			destinationTileY = portal.destinationTileY;
		}

		if (!domain.isTileWithinBounds(destinationLevel.getIdentifier(), destinationTileX, destinationTileY)) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Counterpart ladder is outside projected settlement bounds settlement=" + domain.getSettlementUniqueID() + " level=" + destinationLevel.getIdentifier() + " tile=" + destinationTileX + "," + destinationTileY);
			return null;
		}

		destinationLevel.regionManager.ensureTileIsLoaded(destinationTileX, destinationTileY);
		GameObject counterpart = destinationLevel.getObject(destinationTileX, destinationTileY);
		boolean counterpartValid = destinationType == SettlementLevelType.SURFACE
				? isSurfaceLadderObject(counterpart)
				: isCaveLadderObject(counterpart);
		if (!counterpartValid) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Missing supported counterpart settlement=" + domain.getSettlementUniqueID() + " destination=" + destinationLevel.getIdentifier() + " tile=" + destinationTileX + "," + destinationTileY + " object=" + counterpart.getStringID());
			return null;
		}

		if (sourceType == SettlementLevelType.SURFACE) {
			return new SettlementLadderLink(domain.getSettlementUniqueID(), tileX, tileY, destinationTileX, destinationTileY);
		}
		return new SettlementLadderLink(domain.getSettlementUniqueID(), destinationTileX, destinationTileY, tileX, tileY);
	}

	private static boolean validateLink(SettlementLevelDomain domain, SettlementLadderLink link, boolean loadLevels) {
		if (domain == null || link == null || link.settlementUniqueID != domain.getSettlementUniqueID()) return false;
		Level surface = getDomainLevel(domain, SettlementLevelType.SURFACE, loadLevels);
		Level cave = getDomainLevel(domain, SettlementLevelType.CAVE, loadLevels);
		if (surface == null || cave == null) return false;
		if (!domain.isTileWithinBounds(surface.getIdentifier(), link.surfaceTileX, link.surfaceTileY)) return false;
		if (!domain.isTileWithinBounds(cave.getIdentifier(), link.caveTileX, link.caveTileY)) return false;
		surface.regionManager.ensureTileIsLoaded(link.surfaceTileX, link.surfaceTileY);
		cave.regionManager.ensureTileIsLoaded(link.caveTileX, link.caveTileY);
		return isSurfaceLadderObject(surface.getObject(link.surfaceTileX, link.surfaceTileY))
				&& isCaveLadderObject(cave.getObject(link.caveTileX, link.caveTileY));
	}

	private static Level getSurfaceLevel(SettlementLevelDomain domain, boolean load) {
		return getDomainLevel(domain, SettlementLevelType.SURFACE, load);
	}

	private static Level getDomainLevel(SettlementLevelDomain domain, SettlementLevelType type, boolean load) {
		if (domain == null || type == null) return null;
		LevelIdentifier identifier = domain.getLevelIdentifier(type);
		if (identifier == null) return null;
		Server server = domain.getSettlement().getServer();
		Level level = server.world.levelManager.getLevel(identifier);
		if (level == null && load) {
			if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Loading level for ladder operation settlement=" + domain.getSettlementUniqueID() + " levelType=" + type + " identifier=" + identifier);
			level = server.world.getLevel(identifier);
		}
		return level;
	}

	public static boolean isHoldingSettlementFlag(PlayerMob player) {
		InventoryItem selected = player.getSelectedItem();
		return selected != null && selected.item != null && settlementFlagItemID.equals(selected.item.getStringID());
	}

	private static void sendMessage(ServerClient client, String message) {
		if (client != null) client.sendChatMessage(new StaticMessage(message));
	}
}
