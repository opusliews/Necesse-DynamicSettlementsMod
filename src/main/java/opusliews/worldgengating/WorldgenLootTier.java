package opusliews.worldgengating;

import static opusliews.DSItemRegistry.*;

public enum WorldgenLootTier {
	DEMONIC(2, "demonic", demonicshaftStringID, demonickeyStringID),
	RUNIC(3, "runic", runicshaftStringID, runickeyStringID),
	IVY(4, "ivy", ivyshaftStringID, ivykeyStringID),
	QUARTZ(5, "quartz", quartzshaftStringID, quartzkeyStringID),
	TUNGSTEN(6, "tungsten", tungstenshaftStringID, tungstenkeyStringID),
	GLACIAL(7, "glacial", glacialshaftStringID, glacialkeyStringID),
	DRYAD(8, "dryad", dryadshaftStringID, dryadkeyStringID),
	MYCELIUM(9, "mycelium", myceliumshaftStringID, myceliumkeyStringID),
	FOSSIL(10, "fossil", null, ancientfossilkeyStringID);

	public final int toolTier;
	public final String materialID;
	public final String shaftStringID;
	public final String keyStringID;

	WorldgenLootTier(int toolTier, String materialID, String shaftStringID, String keyStringID) {
		this.toolTier = toolTier;
		this.materialID = materialID;
		this.shaftStringID = shaftStringID;
		this.keyStringID = keyStringID;
	}

	public String getTierLocalizationKey() {
		return "tier" + materialID;
	}

	public static WorldgenLootTier fromRequiredToolTier(int toolTier) {
		int effectiveTier = Math.max(DEMONIC.toolTier, toolTier);
		for (WorldgenLootTier tier : values()) {
			if (tier.toolTier >= effectiveTier) return tier;
		}
		return FOSSIL;
	}

	public static WorldgenLootTier max(WorldgenLootTier first, WorldgenLootTier second) {
		if (first == null) return second;
		if (second == null) return first;
		return first.toolTier >= second.toolTier ? first : second;
	}
}
