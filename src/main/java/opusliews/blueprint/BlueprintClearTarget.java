package opusliews.blueprint;

public class BlueprintClearTarget {
	public enum Type {
		TILE,
		OBJECT
	}

	public final Type type;
	public final int layerID;
	public final int tileX;
	public final int tileY;

	public BlueprintClearTarget(Type type, int tileX, int tileY) {
		this(type, -1, tileX, tileY);
	}

	public BlueprintClearTarget(Type type, int layerID, int tileX, int tileY) {
		this.type = type;
		this.layerID = layerID;
		this.tileX = tileX;
		this.tileY = tileY;
	}
}
