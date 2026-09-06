package opus.tools;

public class BlueprintLayerObject {
	private final String layerID;
	private final String objectID;
	private int rotation;

	public BlueprintLayerObject(String layerID, String objectID, int rotation) {
		this.layerID = layerID;
		this.objectID = objectID;
		this.rotation = Math.floorMod(rotation, 4);
	}

	public String getLayerID() {
		return layerID;
	}

	public String getObjectID() {
		return objectID;
	}

	public int getRotation() {
		return rotation;
	}

	public void setRotation(int rotation) {
		this.rotation = Math.floorMod(rotation, 4);
	}

	public BlueprintLayerObject copy() {
		return new BlueprintLayerObject(layerID, objectID, rotation);
	}
}
