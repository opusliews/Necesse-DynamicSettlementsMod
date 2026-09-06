package opus.blueprint;

import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.WallTorchObject;
import necesse.level.gameObject.furniture.FlowerObject;

public final class BlueprintObjectMaterialResolver {
	private BlueprintObjectMaterialResolver() {
	}

	public static String getPlacementPrerequisiteItemID(String objectID) {
		GameObject object = objectID == null ? null : ObjectRegistry.getObject(objectID);
		return object instanceof FlowerObject ? "flowerpot" : null;
	}

	public static boolean isPlacementPrerequisite(String currentObjectID, String wantedObjectID) {
		String prerequisiteItemID = getPlacementPrerequisiteItemID(wantedObjectID);
		return prerequisiteItemID != null && prerequisiteItemID.equals(currentObjectID);
	}

	public static String getMaterialItemID(String objectID) {
		if (objectID == null) {
			return null;
		}

		int directItemID = ItemRegistry.getItemID(objectID);

		if (directItemID >= 0 && ItemRegistry.isObtainable(directItemID)) {
			return objectID;
		}

		GameObject object = ObjectRegistry.getObject(objectID);

		if (object instanceof WallTorchObject) {
			String sourceItemID = ((WallTorchObject)object).getItemDroppedStringID();
			int sourceItemNumericID = sourceItemID == null ? -1 : ItemRegistry.getItemID(sourceItemID);

			if (sourceItemNumericID >= 0 && ItemRegistry.isObtainable(sourceItemNumericID)) {
				return sourceItemID;
			}
		}

		return objectID;
	}
}
