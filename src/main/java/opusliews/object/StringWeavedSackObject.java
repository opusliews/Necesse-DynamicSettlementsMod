package opusliews.object;

import java.awt.Color;
import necesse.level.gameObject.container.StorageBoxInventoryObject;

/** Small placeable storage sack. Contents drop normally when the object is broken. */
public class StringWeavedSackObject extends StorageBoxInventoryObject {
	public static final String stringID = "stringweavedsack";

	public StringWeavedSackObject() {
		super(stringID, 10, new Color(153, 126, 94));
		this.objectHealth = 30;
	}
}
