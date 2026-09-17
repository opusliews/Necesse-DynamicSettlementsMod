package opusliews.object;

import java.util.HashMap;
import java.util.Map;
import necesse.engine.registries.ObjectRegistry;

public final class PlacedLogRegistry {
	private static final String[] logStringIDs = {
			"oaklog", "sprucelog", "pinelog", "palmlog", "willowlog",
			"maplelog", "birchlog", "bamboo", "deadwoodlog", "dryadlog"
	};
	private static final Map<String, String> objectByLog = new HashMap<>();

	private PlacedLogRegistry() {
	}

	public static void register() {
		for (String logStringID : logStringIDs) {
			String objectStringID = "dsplaced" + logStringID;
			ObjectRegistry.registerObject(objectStringID, new PlacedLogObject(logStringID), 0.0F, false, false, false, logStringID);
			objectByLog.put(logStringID, objectStringID);
		}
	}

	public static int getObjectID(String logStringID) {
		String objectStringID = objectByLog.get(logStringID);
		return objectStringID == null ? -1 : ObjectRegistry.getObjectID(objectStringID);
	}
}
