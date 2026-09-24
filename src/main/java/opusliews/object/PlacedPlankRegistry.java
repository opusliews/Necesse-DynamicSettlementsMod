package opusliews.object;

import java.util.HashMap;
import java.util.Map;

import necesse.engine.registries.ObjectRegistry;

public final class PlacedPlankRegistry {
	private static final String[] plankStringIDs = {
			"oakplank", "spruceplank", "pineplank", "palmplank", "birchplank",
			"willowplank", "mapleplank", "deadwoodplank", "bambooplank", "dryadplank"
	};
	private static final Map<String, String> objectByPlank = new HashMap<>();

	private PlacedPlankRegistry() {
	}

	public static void register() {
		for (String plankStringID : plankStringIDs) {
			String objectStringID = "dsplaced" + plankStringID;
			ObjectRegistry.registerObject(objectStringID, new PlacedPlankObject(plankStringID), 0.0F, false, false, false, plankStringID);
			objectByPlank.put(plankStringID, objectStringID);
		}
	}

	public static int getObjectID(String plankStringID) {
		String objectStringID = objectByPlank.get(plankStringID);
		return objectStringID == null ? -1 : ObjectRegistry.getObjectID(objectStringID);
	}
}
