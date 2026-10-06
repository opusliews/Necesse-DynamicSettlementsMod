package opusliews.progression;

import necesse.engine.journal.JournalChallenge;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.JournalChallengeRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.clayfiring.ClayFiringZone;
import opusliews.item.MoldItem;
import opusliews.journal.GuideJournalRegistry;
import opusliews.logging.Logging;
import opusliews.mobs.BuilderHumanMob;
import opusliews.multilevelsettlement.*;
import opusliews.worldgengating.WorldgenStationProgressionSystem;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

public final class GuideProgressionSystem {
	private static final String saveKey = "DS_GUIDE_PROGRESSION";
	private static final long pollIntervalMs = 1000L;
	private static final WeakHashMap<PlayerMob, PlayerData> dataByPlayer = new WeakHashMap<>();

	private static final String[] firedMolds = {
			"ingotmold", "pickaxeheadmold", "axeheadmold", "shovelheadmold", "sickleblademold",
			"shearsblademold", "swordblademold", "thickplatemold", "sawblademold"
	};

	private GuideProgressionSystem() {
	}

	public static String revealChallengeID(String sectionStringID) {
		return "dsreveal_" + sectionStringID;
	}

	private static PlayerData getData(PlayerMob player) {
		synchronized (dataByPlayer) {
			return dataByPlayer.computeIfAbsent(player, ignored -> new PlayerData());
		}
	}

	public static void recordCraftedRecipe(PlayerMob player, Recipe recipe) {
		if (player == null || recipe == null || recipe.resultItem == null || recipe.resultItem.item == null) return;
		recordCraftedItem(player, recipe.resultItem.item.getStringID());
	}

	public static void recordCraftedItem(PlayerMob player, String itemStringID) {
		if (player == null || itemStringID == null || itemStringID.isEmpty()) return;
		PlayerData data = getData(player);
		if (!data.craftedItems.add(itemStringID)) return;
		if (Logging.logEnabled) Logging.logMessage("[GuideProgression] Recorded crafted item player=" + player.getDisplayName() + " item=" + itemStringID);
	}

	public static boolean hasEverCrafted(PlayerMob player, String itemStringID) {
		if (player == null || itemStringID == null) return false;
		if (getData(player).craftedItems.contains(itemStringID)) return true;

		// These two station families were already tracked before guide27-34 existed,
		// so existing saves can safely inherit their real crafted progression.
		if ("workstationduo".equals(itemStringID)) {
			return WorldgenStationProgressionSystem.getHighestCraftedTier(
					player,
					WorldgenStationProgressionSystem.StationFamily.WORKSTATION
			) >= 0;
		}
		if ("carpentersbench".equals(itemStringID)) {
			return WorldgenStationProgressionSystem.getHighestCraftedTier(
					player,
					WorldgenStationProgressionSystem.StationFamily.CARPENTER
			) >= 0;
		}
		return false;
	}

	public static void recordJobRequestBulletinPlaced(ServerClient client, ServerSettlementData settlement) {
		if (client == null || settlement == null) return;
		PlayerData data = getData(client.playerMob);
		if (data.placedJobRequestBulletin) return;
		data.placedJobRequestBulletin = true;
		if (Logging.logEnabled) Logging.logMessage("[GuideProgression] Recorded Job Request Bulletin placement player=" + client.getName() + " settlement=" + settlement.uniqueID);
	}

	public static boolean hasPlacedJobRequestBulletin(PlayerMob player) {
		return player != null && getData(player).placedJobRequestBulletin;
	}

	public static boolean hasSettlement(PlayerMob player) {
		if (player == null || !player.isServer() || !player.isServerClient()) return false;
		ServerClient client = player.getServerClient();
		if (client == null || client.getServer() == null) return false;
		SettlementsWorldData settlements = SettlementsWorldData.getSettlementsData(client.getServer());
		return !settlements.collectCachedSettlements(cache -> isSettlementMember(client, (CachedSettlementData)cache)).isEmpty();
	}

	/**
	 * Strict settlement membership check. Do not use NetworkSettlementData.doesClientHaveAccess here:
	 * vanilla deliberately grants management access to public settlements and to unowned settlements
	 * (ownerAuth == -1), which includes the hidden Elder settlement created near world spawn.
	 */
	public static boolean isSettlementMember(ServerClient client, ServerSettlementData settlement) {
		return client != null && settlement != null && settlement.networkData != null
				&& settlement.networkData.isClientPartOf(client);
	}

	/**
	 * Cached equivalent of isSettlementMember. CachedSettlementData.hasAccess currently has the same
	 * owner/team semantics, but spelling it out here keeps membership checks distinct from the much
	 * broader loaded-settlement doesClientHaveAccess predicate.
	 */
	public static boolean isSettlementMember(ServerClient client, CachedSettlementData cached) {
		return client != null && cached != null
				&& (cached.getOwnerAuth() == client.authentication || client.isSameTeam(cached.getTeamID()));
	}

	public static void registerRevealChallenge(String sectionStringID) {
		GuideJournalRegistry.registerDiscoveryChallenge(revealChallengeID(sectionStringID));
	}

	public static boolean isRevealed(ServerClient client, String sectionStringID) {
		if (client == null || sectionStringID == null) return false;
		JournalChallenge challenge = JournalChallengeRegistry.getChallenge(revealChallengeID(sectionStringID));
		return challenge != null && challenge.isCompleted(client);
	}

	public static void reveal(ServerClient client, String sectionStringID) {
		if (client == null || sectionStringID == null) return;
		String challengeID = revealChallengeID(sectionStringID);
		JournalChallenge challenge = JournalChallengeRegistry.getChallenge(challengeID);
		if (challenge == null) {
			Logging.logMessage("[GuideProgression] Cannot reveal journal section=" + sectionStringID + " because challenge is missing: " + challengeID);
			return;
		}
		if (challenge.isCompleted(client)) return;
		challenge.markCompleted(client);
		if (Logging.logEnabled) Logging.logMessage("[GuideProgression] Revealed journal section=" + sectionStringID + " player=" + client.getName());
	}

	public static void complete(ServerClient client, String sectionStringID) {
		if (client == null || sectionStringID == null || !isRevealed(client, sectionStringID)) return;
		if (!GuideJournalRegistry.completeSection(client, sectionStringID) && Logging.logEnabled) {
			Logging.logMessage("[GuideProgression] Journal section completion was not applied section=" + sectionStringID + " player=" + client.getName());
		}
	}

	public static void revealForSettlement(ServerSettlementData settlement, String sectionStringID) {
		if (settlement == null || settlement.networkData == null) return;
		for (Object value : settlement.networkData.getTeamMembers()) {
			if (value instanceof ServerClient) reveal((ServerClient)value, sectionStringID);
		}
	}

	public static void completeForSettlement(ServerSettlementData settlement, String sectionStringID) {
		if (settlement == null || settlement.networkData == null) return;
		for (Object value : settlement.networkData.getTeamMembers()) {
			if (value instanceof ServerClient) complete((ServerClient)value, sectionStringID);
		}
	}

	public static void onSettlerMovedIn(ServerSettlementData settlement, String settlerStringID) {
		if (settlement == null || !"mage".equals(settlerStringID)) return;
		if (Logging.logEnabled) Logging.logMessage("[GuideProgression] Mage moved into settlement=" + settlement.uniqueID + "; revealing mold enchanting Journal");
		revealForSettlement(settlement, "moldenchanting");
	}

	public static void onNaturalStationBlocked(ServerClient client, WorldgenStationProgressionSystem.StationRequirement requirement) {
		if (client == null || requirement == null) return;
		if (GuideJournalRegistry.isSectionCompleted(client, "foundstations")) return;
		PlayerData data = getData(client.playerMob);
		if (data.foundStationFamily == null) {
			data.foundStationFamily = requirement.family.name();
			data.foundStationTier = requirement.tier;
			if (Logging.logEnabled) Logging.logMessage("[GuideProgression] Tracking blocked natural station player="
					+ client.getName() + " family=" + data.foundStationFamily + " tier=" + data.foundStationTier);
		}
		reveal(client, "foundstations");
	}

	public static boolean isFoundStationUnlocked(PlayerMob player) {
		if (player == null) return false;
		PlayerData data = getData(player);
		if (data.foundStationFamily == null) return false;
		try {
			WorldgenStationProgressionSystem.StationFamily family = WorldgenStationProgressionSystem.StationFamily.valueOf(data.foundStationFamily);
			return WorldgenStationProgressionSystem.getHighestCraftedTier(player, family) >= data.foundStationTier;
		}
		catch (IllegalArgumentException e) {
			Logging.logMessage("[GuideProgression] Invalid saved found-station family=" + data.foundStationFamily);
			return false;
		}
	}

	public static void onTrapdoorUsed(PlayerMob player) {
		if (player != null && player.isServer() && player.isServerClient()) reveal(player.getServerClient(), "hidingholes");
	}

	public static void onBlueprintCreated(ServerClient client) {
		reveal(client, "blueprintcreation");
		complete(client, "blueprintcreation");
	}

	public static void onBlueprintProjectPlaced(ServerClient client) {
		reveal(client, "blueprintconstruction");
	}

	public static void onBlueprintProjectFinished(ServerSettlementData settlement) {
		completeForSettlement(settlement, "blueprintconstruction");
	}

	public static void onBlueprintWorkstationOpened(ServerClient client) {
		reveal(client, "blueprinttools");
	}

	public static void onWeatherDamage(ServerSettlementData settlement) {
		revealForSettlement(settlement, "builders");
	}

	public static void onWorkstationLinksOpened(ServerClient client, boolean alreadyLinked) {
		reveal(client, "workstationlinks");
		if (alreadyLinked) complete(client, "workstationlinks");
	}

	public static void onWorkstationLinksChanged(ServerClient client, boolean hasInput, boolean hasOutput) {
		if (client == null) return;
		reveal(client, "workstationlinks");
		if (hasInput && hasOutput) complete(client, "workstationlinks");
	}

	public static void onCraftingAutoStockEnabled(ServerClient client) {
		reveal(client, "craftingautostock");
	}

	public static void onStockTargetCreated(ServerClient client) {
		reveal(client, "stockmanagement");
		complete(client, "stockmanagement");
	}

	public static void onAutomatedMetalworkingTaskCreated(ServerClient client) {
		reveal(client, "automatedmetalworking");
	}

	public static void onAutomatedMetalworkingTaskFinished(ServerSettlementData settlement) {
		completeForSettlement(settlement, "automatedmetalworking");
	}

	public static void onWorkZoneCreated(SettlementDependantContainer container, int zoneID, int uniqueID) {
		if (container == null || !container.client.isServer()) return;
		ServerClient client = container.client.getServerClient();
		ServerSettlementData settlement = container.getServerData();
		if (client == null || settlement == null || settlement.getWorkZones().getZone(uniqueID) == null) return;
		if (zoneID == SettlementWorkZoneRegistry.getZoneID(CharcoalProductionZone.stringID)) onCharcoalZoneCreated(client);
		else if (zoneID == SettlementWorkZoneRegistry.getZoneID(ClayFiringZone.stringID)) onClayZoneCreated(client);
	}

	public static void onCharcoalZoneToolOpened(ServerClient client) {
		reveal(client, "charcoalautomation");
	}

	public static void onCharcoalZoneCreated(ServerClient client) {
		reveal(client, "charcoalautomation");
		complete(client, "charcoalautomation");
	}

	public static void onClayZoneToolOpened(ServerClient client) {
		reveal(client, "clayautomation");
	}

	public static void onClayZoneCreated(ServerClient client) {
		reveal(client, "clayautomation");
		complete(client, "clayautomation");
	}

	public static void onSettlementBarrierAttacked(ServerSettlementData settlement) {
		revealForSettlement(settlement, "settlementdefence");
	}

	public static void onGuardDutyChanged(ServerClient client) {
		reveal(client, "guardduty");
		complete(client, "guardduty");
	}

	public static void onGuardFatigueGained(ServerSettlementData settlement) {
		revealForSettlement(settlement, "guardfatigue");
	}

	public static void onBedUsed(ServerClient client) {
		if (client != null && hasSettlement(client.playerMob)) reveal(client, "sleeping");
	}

	public static void onSleepSettingsOpened(ServerClient client) {
		reveal(client, "sleepalarms");
	}

	public static void onSleepSettingsConfirmed(ServerClient client) {
		reveal(client, "sleepalarms");
		complete(client, "sleepalarms");
	}

	public static void onCaveResidentAssigned(ServerSettlementData settlement) {
		revealForSettlement(settlement, "caveresidents");
	}

	public static void onCaveSettlementManagementOpened(ServerClient client) {
		if (client == null || client.getLevel() == null || !client.getLevel().isCave) return;
		if (GuideJournalRegistry.isSectionCompleted(client, "cavesettlements")) reveal(client, "caveinfrastructure");
	}

	public static void onCrossLevelLogistics(ServerSettlementData settlement) {
		revealForSettlement(settlement, "crosslevellogistics");
	}

	public static void onCrossLevelCommand(ServerClient client) {
		reveal(client, "crosslevelcommands");
	}

	public static void onGuardAssignedCave(ServerClient client) {
		if (client == null || !GuideJournalRegistry.isSectionCompleted(client, "cavesettlements")) return;
		reveal(client, "caveguards");
	}

	public static void onUndergroundThreat(ServerSettlementData settlement) {
		revealForSettlement(settlement, "undergroundthreats");
	}


	public static void serverTick(PlayerMob player) {
		if (player == null || !player.isServer() || !player.isServerClient()) return;
		ServerClient client = player.getServerClient();
		if (client == null || client.getServer() == null) return;
		PlayerData data = getData(player);
		Level level = player.getLevel();
		long now = System.currentTimeMillis();
		if (now < data.nextPollTime) return;
		data.nextPollTime = now + pollIntervalMs;

		if (hasObtained(client, firedMolds)) reveal(client, "moldsanddurability");
		if (hasObtained(client, "treasureshovel")) reveal(client, "treasureshovel");
		if (hasObtained(client, "woodash")) reveal(client, "woodashfarmland");
		if (isRevealed(client, "woodashfarmland") && hasObtained(client, "farmland")) complete(client, "woodashfarmland");
		if (hasObtained(client, "blueprintItem")) reveal(client, "blueprintcreation");
		if (hasObtained(client, "projecteraser")) reveal(client, "projectmanagement");
		if (hasObtained(client, "inspectionglass")) reveal(client, "inspectionglass");
		if (hasObtained(client, "craftingtaskboard")) reveal(client, "craftingtasks");
		if (hasObtained(client, "warningbell")) {
			reveal(client, "warningbells");
			complete(client, "settlementdefence");
		}
		if (hasObtained(client, "malignancegoggles")) reveal(client, "malignance");
		if (hasEverCrafted(player, "farmland")) reveal(client, "farmchanges");
		if (hasSettlement(player)) reveal(client, "settlementintro");
		for (Object partyMob : client.adventureParty.getMobs()) {
			if (partyMob instanceof BuilderHumanMob) {
				reveal(client, "travellingbuilder");
				break;
			}
		}

		SettlementsWorldData settlements = SettlementsWorldData.getSettlementsData(client.getServer());
		for (Object value : settlements.collectCachedSettlements(cache -> isSettlementMember(client, (CachedSettlementData)cache))) {
			CachedSettlementData cached = (CachedSettlementData)value;
			ServerSettlementData settlement = settlements.getServerData(cached.uniqueID);
			if (settlement == null) settlement = settlements.getOrLoadServerData(cached.uniqueID);
			if (settlement == null) continue;
			pollSettlement(client, settlement);
		}

		if (isRevealed(client, "moldenchanting") && hasEnchantedMold(player)) {
			complete(client, "moldenchanting");
		}

		if (level != null && level.isCave) {
			SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(client.getServer(), level.getIdentifier(), player.getTileX(), player.getTileY());
			if (domain != null && isSettlementMember(client, domain.getSettlement())) {
				reveal(client, "cavesettlements");
			}
		}

		if (isFoundStationUnlocked(player)) complete(client, "foundstations");
	}

	private static void pollSettlement(ServerClient client, ServerSettlementData settlement) {
		boolean hasBuilder = false;
		boolean hasCarpenter = false;
		boolean hasAngler = false;
		boolean hasGuard = false;
		boolean hasMage = false;
		boolean hasSpecialist = false;
		boolean hasCaveResident = false;
		for (Object value : settlement.getSettlers()) {
			LevelSettler settler = (LevelSettler)value;
			String id = settler.settler.getStringID();
			if ("builder".equals(id)) hasBuilder = true;
			else if ("carpenter".equals(id)) hasCarpenter = true;
			else if ("angler".equals(id)) hasAngler = true;
			else if ("guard".equals(id)) hasGuard = true;
			else if ("mage".equals(id)) hasMage = true;
			if ("builder".equals(id) || "carpenter".equals(id) || "blacksmith".equals(id)
					|| "alchemist".equals(id) || "angler".equals(id)) hasSpecialist = true;
			if (settler.getBed() instanceof SettlementCaveBed) hasCaveResident = true;
		}
		if (hasBuilder) reveal(client, "builders");
		if (hasCarpenter) reveal(client, "carpenters");
		if (hasAngler) reveal(client, "fishingareas");
		if (hasGuard) reveal(client, "guardduty");
		if (hasMage) reveal(client, "moldenchanting");
		if (hasSpecialist) reveal(client, "happinesswork");
		if (hasCaveResident) reveal(client, "caveresidents");
		if (!SettlementLevelStorageManager.getStorage(settlement).isEmpty()) reveal(client, "stockmanagement");

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		boolean undergroundEstablished = domain != null && !SettlementLadderSystem.getDesignatedLinks(domain).isEmpty();
		if (undergroundEstablished) complete(client, "cavesettlements");
		if (settlement.isRaidOngoing() && undergroundEstablished) reveal(client, "multilevelraids");
	}

	private static boolean hasEnchantedMold(PlayerMob player) {
		return player != null && player.getInv().hasAnyItem(
				false, false, false, false, "moldenchanting",
				MoldItem::hasUnbreaking
		);
	}

	private static boolean hasObtained(ServerClient client, String itemStringID) {
		return client != null && itemStringID != null && client.characterStats().items_obtained.isItemObtained(itemStringID);
	}

	private static boolean hasObtained(ServerClient client, String[] itemStringIDs) {
		if (client == null || itemStringIDs == null) return false;
		for (String itemStringID : itemStringIDs) {
			if (hasObtained(client, itemStringID)) return true;
		}
		return false;
	}

	public static void addSaveData(PlayerMob player, SaveData save) {
		if (player == null || save == null) return;
		PlayerData data = getData(player);
		SaveData root = new SaveData(saveKey);
		if (!data.craftedItems.isEmpty()) root.addStringHashSet("craftedItems", new HashSet<>(data.craftedItems));
		root.addBoolean("placedJobRequestBulletin", data.placedJobRequestBulletin);
		if (data.foundStationFamily != null) {
			root.addUnsafeString("foundStationFamily", data.foundStationFamily);
			root.addInt("foundStationTier", data.foundStationTier);
		}
		save.addSaveData(root);
	}

	public static void applyLoadData(PlayerMob player, LoadData save) {
		if (player == null || save == null) return;
		PlayerData data = getData(player);
		data.craftedItems.clear();
		data.placedJobRequestBulletin = false;
		data.foundStationFamily = null;
		data.foundStationTier = -1;
		LoadData root = save.getFirstLoadDataByName(saveKey);
		if (root == null) return;
		data.craftedItems.addAll(root.getStringHashSet("craftedItems", new HashSet<>(), false));
		data.placedJobRequestBulletin = root.getBoolean("placedJobRequestBulletin", false, false);
		data.foundStationFamily = root.getUnsafeString("foundStationFamily", null, false);
		data.foundStationTier = root.getInt("foundStationTier", -1, false);
	}

	public static void writeCharacterPacket(PlayerMob player, PacketWriter writer) {
		PlayerData data = getData(player);
		writer.putNextShortUnsigned(data.craftedItems.size());
		for (String item : data.craftedItems) writer.putNextString(item);
		writer.putNextBoolean(data.placedJobRequestBulletin);
		writer.putNextBoolean(data.foundStationFamily != null);
		if (data.foundStationFamily != null) {
			writer.putNextString(data.foundStationFamily);
			writer.putNextInt(data.foundStationTier);
		}
	}

	public static void applyCharacterPacket(PlayerMob player, PacketReader reader) {
		PlayerData data = getData(player);
		data.craftedItems.clear();
		int count = reader.getNextShortUnsigned();
		for (int i = 0; i < count; i++) data.craftedItems.add(reader.getNextString());
		data.placedJobRequestBulletin = reader.getNextBoolean();
		if (reader.getNextBoolean()) {
			data.foundStationFamily = reader.getNextString();
			data.foundStationTier = reader.getNextInt();
		}
		else {
			data.foundStationFamily = null;
			data.foundStationTier = -1;
		}
	}

	private static final class PlayerData {
		private final Set<String> craftedItems = Collections.synchronizedSet(new HashSet<>());
		private boolean placedJobRequestBulletin;
		private String foundStationFamily;
		private int foundStationTier = -1;
		private long nextPollTime;
	}
}
