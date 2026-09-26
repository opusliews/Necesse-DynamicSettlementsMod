package opusliews.worldgengating;

public final class WorldgenGatingData {
	public enum NaturalType {
		LOCKED_CONTAINER,
		TIED_SACK,
		CRAFTING_STATION,
		ARMOR_STAND
	}

	public static final String naturalKey = "dsWorldgenNatural";
	public static final String naturalTypeKey = "dsWorldgenNaturalType";
	public static final String tierKey = "dsWorldgenTier";
	public static final String lockedKey = "dsWorldgenLocked";
	public static final String storedInventoryKey = "dsWorldgenInventory";

	private WorldgenGatingData() {
	}
}
