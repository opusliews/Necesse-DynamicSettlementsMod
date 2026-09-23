package opusliews.clayfiring;

import java.util.ArrayList;
import java.util.List;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.item.Item;
import opusliews.item.FireableMatItem;

public final class ClayFiringCatalog {
	private ClayFiringCatalog() {
	}

	public static List<Entry> getEntries() {
		ArrayList<Entry> entries = new ArrayList<>();
		for (Object value : ItemRegistry.getItems()) {
			if (!(value instanceof FireableMatItem)) continue;
			FireableMatItem unfired = (FireableMatItem)value;
			String firedStringID = unfired.getFiredItemStringID();
			if (firedStringID == null || firedStringID.isEmpty()) continue;
			Item fired = ItemRegistry.getItem(firedStringID);
			if (fired == null) continue;
			entries.add(new Entry(unfired.getStringID(), firedStringID));
		}
		return entries;
	}

	public static class Entry {
		public final String unfiredItemStringID;
		public final String firedItemStringID;

		public Entry(String unfiredItemStringID, String firedItemStringID) {
			this.unfiredItemStringID = unfiredItemStringID;
			this.firedItemStringID = firedItemStringID;
		}
	}
}
