package opusliews.worldgengating;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WorldgenTierTable {
	public enum Depth {
		SURFACE,
		CAVE,
		DEEP_CAVE
	}

	private static final Map<String, WorldgenLootTier[]> biomeTiers = new HashMap<>();

	static {
		registerBiome("forest", WorldgenLootTier.DEMONIC, WorldgenLootTier.DEMONIC, WorldgenLootTier.QUARTZ);
		registerBiome("plains", WorldgenLootTier.DEMONIC, WorldgenLootTier.DEMONIC, WorldgenLootTier.GLACIAL);
		registerBiome("snow", WorldgenLootTier.DEMONIC, WorldgenLootTier.DEMONIC, WorldgenLootTier.TUNGSTEN);
		registerBiome("swamp", WorldgenLootTier.RUNIC, WorldgenLootTier.RUNIC, WorldgenLootTier.DRYAD);
		registerBiome("desert", WorldgenLootTier.IVY, WorldgenLootTier.IVY, WorldgenLootTier.MYCELIUM);
	}

	public static final WorldgenLootTier DUNGEON = WorldgenLootTier.DEMONIC;
	public static final WorldgenLootTier TEMPLE = WorldgenLootTier.MYCELIUM;
	public static final WorldgenLootTier PIRATE_DISPLAY_STAND = WorldgenLootTier.GLACIAL;

	private WorldgenTierTable() {
	}

	private static void registerBiome(
			String biomeStringID,
			WorldgenLootTier surfaceTier,
			WorldgenLootTier caveTier,
			WorldgenLootTier deepCaveTier
	) {
		biomeTiers.put(normalizeBiomeID(biomeStringID), new WorldgenLootTier[]{surfaceTier, caveTier, deepCaveTier});
	}

	public static WorldgenLootTier getBiomeTier(String biomeStringID, Depth depth) {
		if (biomeStringID == null || depth == null) return null;
		WorldgenLootTier[] tiers = biomeTiers.get(normalizeBiomeID(biomeStringID));
		return tiers == null ? null : tiers[depth.ordinal()];
	}

	private static String normalizeBiomeID(String biomeStringID) {
		return biomeStringID.trim().toLowerCase(Locale.ROOT);
	}
}
