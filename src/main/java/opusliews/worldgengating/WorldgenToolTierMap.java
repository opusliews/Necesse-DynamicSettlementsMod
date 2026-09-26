package opusliews.worldgengating;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicit progression tiers for vanilla non-combat tools.
 *
 * This intentionally does not rely on ToolDamageItem#getToolTier for known vanilla tools:
 * some utility tools inherit special/default tool-tier values that do not represent progression
 * (for example, the Sickle reports tool tier 10), and Ivy axes/shovels report tool tier 2 even
 * though they are Ivy-progression items.
 */
public final class WorldgenToolTierMap {
	private static final Map<String, WorldgenLootTier> tiers = new HashMap<>();

	static {
		// Pre-Demonic and Demonic progression all collapse to the minimum lock tier.
		put(WorldgenLootTier.DEMONIC,
				"woodpickaxe", "copperpickaxe", "ironpickaxe", "goldpickaxe", "frostpickaxe", "demonicpickaxe",
				"woodaxe", "copperaxe", "ironaxe", "goldaxe", "frostaxe", "demonicaxe",
				"woodshovel", "coppershovel", "ironshovel", "goldshovel", "frostshovel", "demonicshovel",
				"sickle", "shears", "woodfishingrod", "ironfishingrod", "goldfishingrod"
		);

		put(WorldgenLootTier.RUNIC,
				"runicpickaxe", "runicaxe", "runicshovel",
				"farmingscythe"
		);

		put(WorldgenLootTier.IVY,
				"ivypickaxe", "ivyaxe", "ivyshovel",
				"overgrownfishingrod"
		);

		put(WorldgenLootTier.QUARTZ,
				"quartzpickaxe", "quartzaxe", "quartzshovel"
		);

		put(WorldgenLootTier.TUNGSTEN,
				"tungstenpickaxe", "tungstenaxe", "tungstenshovel"
		);

		put(WorldgenLootTier.GLACIAL,
				"glacialpickaxe", "glacialaxe", "glacialshovel"
		);

		put(WorldgenLootTier.DRYAD,
				"dryadpickaxe", "dryadaxe", "dryadshovel"
		);

		put(WorldgenLootTier.MYCELIUM,
				"myceliumpickaxe", "myceliumaxe", "myceliumshovel"
		);

		// Ice Pickaxe and Multi Tool both have real tier-10 breaking capability.
		// Depths Catcher is a Fallen-Anvil/Shadow-Essence tool; our lock progression caps at Fossil.
		put(WorldgenLootTier.FOSSIL,
				"ancientfossilpickaxe", "ancientfossilaxe", "ancientfossilshovel",
				"icepickaxe", "multitool", "depthscatcher", "godrod"
		);
	}

	private WorldgenToolTierMap() {
	}

	public static WorldgenLootTier getTier(String itemStringID) {
		return itemStringID == null ? null : tiers.get(itemStringID);
	}

	private static void put(WorldgenLootTier tier, String... itemStringIDs) {
		for (String itemStringID : itemStringIDs) tiers.put(itemStringID, tier);
	}
}
