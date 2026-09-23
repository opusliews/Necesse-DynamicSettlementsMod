package opusliews.item;

import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FireableMatItem extends MatItem {
	private static final Set<String> firedItemStringIDs = Collections.synchronizedSet(new HashSet<>());

	private final String firedItemStringID;

	public FireableMatItem(int stackSize, Item.Rarity rarity, String firedItemStringID) {
		super(stackSize, rarity);
		this.firedItemStringID = firedItemStringID;
		if (firedItemStringID != null && !firedItemStringID.isEmpty()) {
			firedItemStringIDs.add(firedItemStringID);
		}
	}

	public String getFiredItemStringID() {
		return firedItemStringID;
	}

	public static boolean isFiredItemStringID(String stringID) {
		return stringID != null && firedItemStringIDs.contains(stringID);
	}
}
