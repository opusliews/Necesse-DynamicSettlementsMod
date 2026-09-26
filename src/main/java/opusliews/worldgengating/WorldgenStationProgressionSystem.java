package opusliews.worldgengating;

import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.recipe.Recipe;
import opusliews.logging.Logging;

import java.util.EnumMap;
import java.util.WeakHashMap;

public final class WorldgenStationProgressionSystem {
	private static final String saveKey = "DS_WORLDGEN_STATION_PROGRESS";
	private static final WeakHashMap<PlayerMob, ProgressData> dataByPlayer = new WeakHashMap<>();

	public enum StationFamily {
		WORKSTATION,
		FORGE,
		CARPENTER,
		ANVIL,
		ALCHEMY,
		CARTOGRAPHER,
		CAMPFIRE,
		COOKING_POT,
		ROASTING_STATION,
		COOKING_STATION,
		GRAIN_MILL,
		INCINERATOR
	}

	public static final class StationRequirement {
		public final StationFamily family;
		public final int tier;

		public StationRequirement(StationFamily family, int tier) {
			this.family = family;
			this.tier = tier;
		}
	}

	private static final class ProgressData {
		private final EnumMap<StationFamily, Integer> highestCraftedTier = new EnumMap<>(StationFamily.class);
	}

	private WorldgenStationProgressionSystem() {
	}

	private static ProgressData getData(PlayerMob player) {
		synchronized (dataByPlayer) {
			return dataByPlayer.computeIfAbsent(player, key -> new ProgressData());
		}
	}

	public static StationRequirement getNaturalStationRequirement(String objectStringID) {
		if (objectStringID == null) return null;
		switch (objectStringID) {
			case "workstation":
			case "workstationduo":
			case "workstationduo2":
				return new StationRequirement(StationFamily.WORKSTATION, 0);
			case "tungstenworkstation":
			case "tungstenworkstation2":
				return new StationRequirement(StationFamily.WORKSTATION, 2);
			case "forge":
				return new StationRequirement(StationFamily.FORGE, 0);
			case "carpentersbench":
			case "carpentersbench2":
				return new StationRequirement(StationFamily.CARPENTER, 0);
			case "ironanvil":
				return new StationRequirement(StationFamily.ANVIL, 0);
			case "demonicanvil":
				return new StationRequirement(StationFamily.ANVIL, 1);
			case "tungstenanvil":
				return new StationRequirement(StationFamily.ANVIL, 2);
			case "alchemytable":
				return new StationRequirement(StationFamily.ALCHEMY, 0);
			case "voidalchemytable":
				return new StationRequirement(StationFamily.ALCHEMY, 1);
			case "caveglowalchemytable":
				return new StationRequirement(StationFamily.ALCHEMY, 2);
			case "cartographertable":
				return new StationRequirement(StationFamily.CARTOGRAPHER, 0);
			case "campfire":
				return new StationRequirement(StationFamily.CAMPFIRE, 0);
			case "cookingpot":
				return new StationRequirement(StationFamily.COOKING_POT, 0);
			case "roastingstation":
				return new StationRequirement(StationFamily.ROASTING_STATION, 0);
			case "cookingstation":
			case "cookingstation2":
				return new StationRequirement(StationFamily.COOKING_STATION, 0);
			case "grainmill":
			case "grainmill2":
			case "grainmill3":
			case "grainmill4":
				return new StationRequirement(StationFamily.GRAIN_MILL, 0);
			case "incinerator":
				return new StationRequirement(StationFamily.INCINERATOR, 0);
			default:
				return null;
		}
	}

	public static void recordCraftedRecipe(PlayerMob player, Recipe recipe) {
		if (player == null || recipe == null || recipe.resultItem == null) return;
		recordCraftedItem(player, recipe.resultItem.item.getStringID());
	}

	public static void recordCraftedItem(PlayerMob player, String itemStringID) {
		if (player == null || itemStringID == null) return;
		switch (itemStringID) {
			case "workstationduo":
				setHighest(player, StationFamily.WORKSTATION, 0, itemStringID);
				break;
			case "demonicworkstationduo":
				setHighest(player, StationFamily.WORKSTATION, 1, itemStringID);
				break;
			case "tungstenworkstation":
				setHighest(player, StationFamily.WORKSTATION, 2, itemStringID);
				break;
			case "fallenworkstation":
				setHighest(player, StationFamily.WORKSTATION, 3, itemStringID);
				break;
			case "forge":
				setHighest(player, StationFamily.FORGE, 0, itemStringID);
				break;
			case "carpentersbench":
				setHighest(player, StationFamily.CARPENTER, 0, itemStringID);
				break;
			case "tungstencarpentersbench":
				setHighest(player, StationFamily.CARPENTER, 1, itemStringID);
				break;
			case "fallencarpentersbench":
				setHighest(player, StationFamily.CARPENTER, 2, itemStringID);
				break;
			case "ironanvil":
				setHighest(player, StationFamily.ANVIL, 0, itemStringID);
				break;
			case "demonicanvil":
				setHighest(player, StationFamily.ANVIL, 1, itemStringID);
				break;
			case "tungstenanvil":
				setHighest(player, StationFamily.ANVIL, 2, itemStringID);
				break;
			case "fallenanvil":
				setHighest(player, StationFamily.ANVIL, 3, itemStringID);
				break;
			case "alchemytable":
				setHighest(player, StationFamily.ALCHEMY, 0, itemStringID);
				break;
			case "voidalchemytable":
				setHighest(player, StationFamily.ALCHEMY, 1, itemStringID);
				break;
			case "caveglowalchemytable":
				setHighest(player, StationFamily.ALCHEMY, 2, itemStringID);
				break;
			case "fallenalchemytable":
				setHighest(player, StationFamily.ALCHEMY, 3, itemStringID);
				break;
			case "cartographertable":
				setHighest(player, StationFamily.CARTOGRAPHER, 0, itemStringID);
				break;
			case "campfire":
				setHighest(player, StationFamily.CAMPFIRE, 0, itemStringID);
				break;
			case "cookingpot":
				setHighest(player, StationFamily.COOKING_POT, 0, itemStringID);
				break;
			case "roastingstation":
				setHighest(player, StationFamily.ROASTING_STATION, 0, itemStringID);
				break;
			case "cookingstation":
				setHighest(player, StationFamily.COOKING_STATION, 0, itemStringID);
				setHighest(player, StationFamily.COOKING_POT, 1, itemStringID);
				setHighest(player, StationFamily.ROASTING_STATION, 1, itemStringID);
				break;
			case "grainmill":
				setHighest(player, StationFamily.GRAIN_MILL, 0, itemStringID);
				break;
			case "incinerator":
				setHighest(player, StationFamily.INCINERATOR, 0, itemStringID);
				break;
		}
	}

	public static boolean canUseNaturalStation(PlayerMob player, String objectStringID) {
		StationRequirement requirement = getNaturalStationRequirement(objectStringID);
		if (requirement == null) return false;
		return getHighestCraftedTier(player, requirement.family) >= requirement.tier;
	}

	public static int getHighestCraftedTier(PlayerMob player, StationFamily family) {
		if (player == null || family == null) return -1;
		return getData(player).highestCraftedTier.getOrDefault(family, -1);
	}

	private static void setHighest(PlayerMob player, StationFamily family, int tier, String itemStringID) {
		ProgressData data = getData(player);
		int oldTier = data.highestCraftedTier.getOrDefault(family, -1);
		if (tier <= oldTier) return;
		data.highestCraftedTier.put(family, tier);
		Logging.logMessage("[WorldgenGating] Player crafted station item=" + itemStringID
				+ " family=" + family + " tier=" + tier
				+ " player=" + player.getDisplayName());
	}

	public static void addSaveData(PlayerMob player, SaveData save) {
		ProgressData data = getData(player);
		SaveData out = new SaveData(saveKey);
		for (StationFamily family : StationFamily.values()) {
			int tier = data.highestCraftedTier.getOrDefault(family, -1);
			if (tier >= 0) out.addInt(family.name(), tier);
		}
		save.addSaveData(out);
	}

	public static void applyLoadData(PlayerMob player, LoadData save) {
		ProgressData data = getData(player);
		data.highestCraftedTier.clear();
		LoadData in = save.getFirstLoadDataByName(saveKey);
		if (in == null) return;
		for (StationFamily family : StationFamily.values()) {
			int tier = in.getInt(family.name(), -1, false);
			if (tier >= 0) data.highestCraftedTier.put(family, tier);
		}
	}

	public static void writeCharacterPacket(PlayerMob player, PacketWriter writer) {
		ProgressData data = getData(player);
		for (StationFamily family : StationFamily.values()) {
			writer.putNextInt(data.highestCraftedTier.getOrDefault(family, -1));
		}
	}

	public static void applyCharacterPacket(PlayerMob player, PacketReader reader) {
		ProgressData data = getData(player);
		data.highestCraftedTier.clear();
		for (StationFamily family : StationFamily.values()) {
			int tier = reader.getNextInt();
			if (tier >= 0) data.highestCraftedTier.put(family, tier);
		}
	}
}
