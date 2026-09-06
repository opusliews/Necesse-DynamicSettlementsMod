package opus.tools;

import necesse.engine.registries.ObjectLayerRegistry;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.*;
import necesse.level.gameTile.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BlueprintElement {
	private final int x;
	private final int y;

	private String tileID;
	private final List<BlueprintLayerObject> objects = new ArrayList<>();
	private int wireMask;
	private String logicGateID;
	private String logicGateData;
	private int logicGateRotation;

	public boolean isEmpty() {
		return tileID == null && objects.isEmpty() && wireMask == 0 && logicGateID == null;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public String getTileID() {
		return tileID;
	}

	public List<BlueprintLayerObject> getObjects() {
		return new ArrayList<>(objects);
	}

	public List<BlueprintLayerObject> getObjectsInPlacementOrder() {
		List<BlueprintLayerObject> sorted = getObjects();
		sorted.sort(Comparator.comparingInt(object -> {
			int layerID = getNumericLayerID(object.getLayerID());
			return layerID < 0 ? Integer.MAX_VALUE : layerID;
		}));
		return sorted;
	}

	public BlueprintLayerObject getObjectAtLayer(String layerID) {
		for (BlueprintLayerObject object : objects) {
			if (object.getLayerID().equals(layerID)) {
				return object;
			}
		}

		return null;
	}

	public void setObjectAtLayer(String layerID, String objectID, int rotation) {
		removeObjectAtLayer(layerID);

		if (layerID != null && objectID != null) {
			objects.add(new BlueprintLayerObject(layerID, objectID, rotation));
		}
	}

	public void addObject(BlueprintLayerObject object) {
		if (object != null) {
			setObjectAtLayer(object.getLayerID(), object.getObjectID(), object.getRotation());
		}
	}

	public boolean removeObjectAtLayer(String layerID) {
		return objects.removeIf(object -> object.getLayerID().equals(layerID));
	}

	public boolean removeObjectType(String objectID) {
		return objects.removeIf(object -> object.getObjectID().equals(objectID));
	}

	// Convenience accessors for the base layer. These keep older callers and
	// imported blueprint JSON compatible while layered code uses getObjects().
	public String getObjectID() {
		BlueprintLayerObject object = getObjectAtLayer("base");
		return object == null ? null : object.getObjectID();
	}

	public int getRotation() {
		BlueprintLayerObject object = getObjectAtLayer("base");
		return object == null ? 0 : object.getRotation();
	}

	public int getWireMask() {
		return wireMask;
	}

	public String getLogicGateID() {
		return logicGateID;
	}

	public String getLogicGateData() {
		return logicGateData;
	}

	public int getLogicGateRotation() {
		return logicGateRotation;
	}

	public void setTileID(String tileID) {
		this.tileID = tileID;
	}

	public void setObjectID(String objectID) {
		if (objectID == null) {
			removeObjectAtLayer("base");
		} else {
			setObjectAtLayer("base", objectID, getRotation());
		}
	}

	public void setRotation(int rotation) {
		BlueprintLayerObject object = getObjectAtLayer("base");

		if (object != null) {
			object.setRotation(rotation);
		}
	}

	public void setWireMask(int wireMask) {
		this.wireMask = wireMask & 0xF;
	}

	public void setLogicGateID(String logicGateID) {
		this.logicGateID = logicGateID;
	}

	public void setLogicGateData(String logicGateData) {
		this.logicGateData = logicGateData;
	}

	public void setLogicGateRotation(int logicGateRotation) {
		this.logicGateRotation = Math.floorMod(logicGateRotation, 4);
	}

	public BlueprintElement(int x, int y) {
		this.x = x;
		this.y = y;
	}

	private static int getNumericLayerID(String layerID) {
		try {
			return ObjectLayerRegistry.getLayerID(layerID);
		} catch (Exception e) {
			return -1;
		}
	}

	public static boolean isBlueprintObject(GameObject gameObject) {
		if (gameObject == null) {
			return false;
		}
		if (gameObject instanceof AirObject) {
			return false;
		}
		if (gameObject.toolType == ToolType.UNBREAKABLE) {
			return false;
		}

		return true;
	}

	public static boolean isBlueprintTile(GameTile gameTile) {
		if (gameTile == null) {
			return false;
		}
		if (gameTile instanceof EmptyTile) {
			return false;
		}
		if (gameTile instanceof LiquidTile) {
			return false;
		}
		if (gameTile instanceof ChromaKeyTile) {
			return false;
		}

		return true;
	}
}
