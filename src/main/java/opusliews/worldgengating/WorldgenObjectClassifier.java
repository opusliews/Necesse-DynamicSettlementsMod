package opusliews.worldgengating;

import necesse.level.maps.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class WorldgenObjectClassifier {
	private static final Set<String> lockedContainers = new HashSet<>();
	private static final Set<String> tiedSacks = new HashSet<>();
	private static final Set<String> armorStands = new HashSet<>();
	private static final Set<String> craftingStations = new HashSet<>();
	private static final Map<String, WorldgenLootTier> stationMaterialTiers = new HashMap<>();

	static {
		addLockedContainers(
				"storagebox", "coolingbox", "barrel",
				"oakchest", "sprucechest", "pinechest", "willowchest", "palmchest", "maplechest", "birchchest",
				"dungeonchest", "bonechest", "dryadchest", "bamboochest", "deadwoodchest", "piratechest",
				"oakcabinet", "sprucecabinet", "pinecabinet", "willowcabinet", "palmcabinet", "maplecabinet", "birchcabinet",
				"dungeoncabinet", "bonecabinet", "dryadcabinet", "bamboocabinet", "deadwoodcabinet",
				"oakdresser", "sprucedresser", "pinedresser", "willowdresser", "palmdresser", "mapledresser", "birchdresser",
				"dungeondresser", "bonedresser", "dryaddresser", "bamboodresser", "deadwooddresser",
				"oakdisplay", "sprucedisplay", "pinedisplay", "willowdisplay", "palmdisplay", "mapledisplay", "birchdisplay",
				"dungeondisplay", "bonedisplay", "dryaddisplay", "bamboodisplay", "deadwooddisplay", "cavelingdisplay", "vulturedisplay",
				"stonecoffin", "basaltcoffin", "cryptcoffin"
		);

		tiedSacks.add("sack");
		tiedSacks.add("merchantsbackpack");
		armorStands.add("armorstand");
		armorStands.add("spideritearmorstand");

		addStation(WorldgenLootTier.DEMONIC,
				"workstation", "workstationduo", "workstationduo2",
				"forge",
				"carpentersbench", "carpentersbench2",
				"ironanvil",
				"alchemytable",
				"demonicanvil",
				"voidalchemytable",
				"cartographertable",
				"campfire",
				"cookingpot",
				"roastingstation",
				"grainmill", "grainmill2", "grainmill3", "grainmill4",
				"incinerator"
		);

		addStation(WorldgenLootTier.TUNGSTEN,
				"tungstenworkstation", "tungstenworkstation2",
				"tungstenanvil",
				"caveglowalchemytable",
				"cookingstation", "cookingstation2"
		);
	}

	private WorldgenObjectClassifier() {
	}

	private static void addLockedContainers(String... stringIDs) {
		for (String stringID : stringIDs) lockedContainers.add(stringID);
	}

	private static void addStation(WorldgenLootTier materialTier, String... stringIDs) {
		for (String stringID : stringIDs) {
			craftingStations.add(stringID);
			stationMaterialTiers.put(stringID, materialTier);
		}
	}

	public static WorldgenGatingData.NaturalType getNaturalType(String objectStringID) {
		if (objectStringID == null) return null;
		if (lockedContainers.contains(objectStringID)) return WorldgenGatingData.NaturalType.LOCKED_CONTAINER;
		if (tiedSacks.contains(objectStringID)) return WorldgenGatingData.NaturalType.TIED_SACK;
		if (armorStands.contains(objectStringID)) return WorldgenGatingData.NaturalType.ARMOR_STAND;
		if (craftingStations.contains(objectStringID)) return WorldgenGatingData.NaturalType.CRAFTING_STATION;
		return null;
	}

	public static WorldgenLootTier getRequiredTier(Level level, int tileX, int tileY, String objectStringID, WorldgenGatingData.NaturalType type) {
		WorldgenLootTier biomeTier = getBiomeTier(level, tileX, tileY);
		if (type == WorldgenGatingData.NaturalType.CRAFTING_STATION) {
			WorldgenLootTier materialTier = stationMaterialTiers.get(objectStringID);
			return WorldgenLootTier.max(biomeTier, materialTier == null ? WorldgenLootTier.DEMONIC : materialTier);
		}
		return biomeTier;
	}

	public static WorldgenLootTier getBiomeTier(Level level, int tileX, int tileY) {
		if (level == null) return WorldgenLootTier.DEMONIC;

		String biomeStringID = level.getBiome(tileX, tileY).getStringID();
		if ("dungeon".equals(biomeStringID)) return WorldgenTierTable.DUNGEON;
		if ("temple".equals(biomeStringID)) return WorldgenTierTable.TEMPLE;

		WorldgenTierTable.Depth depth = level.isDeepCaveLevel()
				? WorldgenTierTable.Depth.DEEP_CAVE
				: WorldgenTierTable.Depth.CAVE;
		WorldgenLootTier tier = WorldgenTierTable.getBiomeTier(biomeStringID, depth);
		return tier == null ? WorldgenLootTier.DEMONIC : tier;
	}
}
