package opusliews.forge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import necesse.inventory.InventoryItem;
import opusliews.durability.DurabilityAction;
import opusliews.durability.ItemDurabilityRegistry;
import opusliews.durability.ItemDurabilitySystem;
import opusliews.logging.Logging;

public final class MeltablePartSystem {
	private static final Set<String> recoverableBars = new HashSet<>();
	private static final Map<String, MeltablePartDefinition> definitions = new LinkedHashMap<>();

	static {
		recoverableBars.add("copperbar");
		recoverableBars.add("ironbar");
		recoverableBars.add("goldbar");
		recoverableBars.add("tungstenbar");
		recoverableBars.add("demonicbar");
		recoverableBars.add("ivybar");
		recoverableBars.add("glacialbar");
		recoverableBars.add("myceliumbar");
		recoverableBars.add("ancientfossilbar");
		recoverableBars.add("nightsteelbar");
		recoverableBars.add("spideritebar");
	}

	private MeltablePartSystem() {
	}

	public static boolean isRecoverableBar(String itemStringID) {
		return itemStringID != null && recoverableBars.contains(itemStringID);
	}

	public static void registerPart(String partStringID, String barStringID, int barAmount) {
		if (partStringID == null || partStringID.isEmpty() || !isRecoverableBar(barStringID) || barAmount < 1) {
			if (Logging.logEnabled) {
				Logging.logMessage("[PartMelting] Rejected registration part=" + partStringID
						+ " bar=" + barStringID + " bars=" + barAmount);
			}
			return;
		}

		MeltablePartDefinition previous = definitions.put(partStringID, new MeltablePartDefinition(partStringID, barStringID, barAmount));
		if (previous != null && (!previous.barStringID.equals(barStringID) || previous.barAmount != barAmount)) {
			Logging.logMessage("[PartMelting] Replaced conflicting registration part=" + partStringID
					+ " oldBar=" + previous.barStringID + " oldBars=" + previous.barAmount
					+ " newBar=" + barStringID + " newBars=" + barAmount);
		}

		ItemDurabilityRegistry.configure(partStringID, barAmount)
				.on(DurabilityAction.CRAFTING_USE, 1)
				.destroyOnBreak();

		if (Logging.logEnabled) {
			Logging.logMessage("[PartMelting] Registered part=" + partStringID
					+ " recoverableBar=" + barStringID + " bars=" + barAmount);
		}
	}

	public static boolean isMeltablePart(InventoryItem item) {
		return item != null && item.item != null && definitions.containsKey(item.item.getStringID());
	}

	public static boolean isPartiallyMelted(InventoryItem item) {
		return isMeltablePart(item) && !ItemDurabilitySystem.isFullDurability(item);
	}

	public static boolean canUseAsComponent(InventoryItem item) {
		return !isPartiallyMelted(item);
	}

	public static MeltablePartDefinition getDefinition(String partStringID) {
		return partStringID == null ? null : definitions.get(partStringID);
	}

	public static Map<String, MeltablePartDefinition> getDefinitions() {
		return Collections.unmodifiableMap(definitions);
	}

	public static final class MeltablePartDefinition {
		public final String partStringID;
		public final String barStringID;
		public final int barAmount;

		public MeltablePartDefinition(String partStringID, String barStringID, int barAmount) {
			this.partStringID = partStringID;
			this.barStringID = barStringID;
			this.barAmount = barAmount;
		}
	}
}
