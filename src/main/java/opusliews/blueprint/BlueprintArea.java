package opusliews.blueprint;

import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.TileRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.AirObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SwitchObject;
import necesse.level.gameObject.WallTorchObject;
import necesse.level.gameTile.GameTile;
import necesse.level.maps.Level;
import necesse.level.maps.multiTile.MultiTile;
import opusliews.mobs.BuilderHumanMob;
import opusliews.tools.BlueprintData;
import opusliews.tools.BlueprintElement;
import opusliews.tools.BlueprintLayerObject;

import java.awt.*;
import java.util.List;
import java.util.*;

public class BlueprintArea {
	private final String uniqueID;
	private final int originX;
	private final int originY;
	private final int width;
	private final int height;
	private final BlueprintData blueprintData;
	private final int settlementUniqueID;
	private boolean constructionStarted;
	private String constructionBlockedReason;
	private boolean constructionComplete;
	private final Map<Integer, Map<String, Integer>> builderMaterialAllocations = new HashMap<>();
	private final Set<Integer> assignedBuilderIDs = new HashSet<>();

	public BlueprintArea(
			int settlementUniqueID,
			int originX,
			int originY,
			int width,
			int height,
			BlueprintData blueprintData
	) {
		this(UUID.randomUUID().toString(), settlementUniqueID, originX, originY, width, height,
				blueprintData, false, false);
	}

	private BlueprintArea(
			String uniqueID,
			int settlementUniqueID,
			int originX,
			int originY,
			int width,
			int height,
			BlueprintData blueprintData,
			boolean constructionStarted,
			boolean constructionComplete
	) {
		this.uniqueID = uniqueID;
		this.settlementUniqueID = settlementUniqueID;
		this.originX = originX;
		this.originY = originY;
		this.width = width;
		this.height = height;
		this.blueprintData = blueprintData;
		this.constructionStarted = constructionStarted;
		this.constructionComplete = constructionComplete;
	}

	public String getUniqueID() {
		return uniqueID;
	}

	public int getOriginX() {
		return originX;
	}

	public int getOriginY() {
		return originY;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getSettlementUniqueID() {
		return settlementUniqueID;
	}

	public void assignBuilder(BuilderHumanMob builder) {
		assignedBuilderIDs.add(builder.getUniqueID());
	}

	public void releaseBuilder(int builderUniqueID) {
		assignedBuilderIDs.remove(builderUniqueID);
		clearBuilderMaterialAllocation(builderUniqueID);
	}

	public boolean isBuilderAssigned(int builderUniqueID) {
		return assignedBuilderIDs.contains(builderUniqueID);
	}

	public List<BuilderHumanMob> getAssignedBuilders(Level level) {
		List<BuilderHumanMob> builders = new ArrayList<>();

		for (int uniqueID : assignedBuilderIDs) {
			BuilderHumanMob mob = (BuilderHumanMob)level.entityManager.mobs.get(uniqueID, false);
			if (mob != null) {
				builders.add(mob);
			}
		}

		return builders;
	}

	public BlueprintData getBlueprintData() {
		return blueprintData;
	}

	public Rectangle getTileBounds() {
		return new Rectangle(originX, originY, width, height);
	}

	public List<Point> getOutsideBorderTiles() {
		List<Point> tiles = new ArrayList<>();
		int left = originX - 1;
		int right = originX + width;
		int top = originY - 1;
		int bottom = originY + height;

		for (int x = left; x <= right; x++) {
			tiles.add(new Point(x, top));
			tiles.add(new Point(x, bottom));
		}

		for (int y = originY; y < originY + height; y++) {
			tiles.add(new Point(left, y));
			tiles.add(new Point(right, y));
		}

		return tiles;
	}

	public BlueprintTileTarget findFirstTileTarget(Level level) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null || element.getTileID() == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;
				GameTile wantedTile = TileRegistry.getTile(element.getTileID());

				if (level.getTileID(worldX, worldY) != wantedTile.getID()) {
					return new BlueprintTileTarget(worldX, worldY, element.getTileID());
				}
			}
		}

		return null;
	}

	public BlueprintObjectTarget findFirstObjectTarget(Level level) {
		return findFirstObjectTarget(level, false);
	}

	public BlueprintObjectTarget findFirstWallObjectTarget(Level level) {
		return findFirstObjectTarget(level, true);
	}

	private BlueprintObjectTarget findFirstObjectTarget(Level level, boolean wallObjects) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				for (BlueprintLayerObject layerObject : element.getObjectsInPlacementOrder()) {
					int layerID = resolveLayerID(layerObject.getLayerID());

					if (layerID < 0 || isWallPlacementObject(layerID, layerObject) != wallObjects || !isObjectPlacementObject(layerObject)) {
						continue;
					}

					if (!isObjectComplete(level, layerID, layerObject, worldX, worldY)) {
						return new BlueprintObjectTarget(
								layerID, worldX, worldY, layerObject.getObjectID(), layerObject.getRotation());
					}
				}
			}
		}

		return null;
	}

	private boolean isBlueprintObjectComplete(
			Level level,
			BlueprintElement element,
			BlueprintLayerObject layerObject,
			int worldX,
			int worldY
	) {
		if (element == null || layerObject == null) {
			return true;
		}

		int layerID = resolveLayerID(layerObject.getLayerID());
		GameObject wantedObject = ObjectRegistry.getObject(layerObject.getObjectID());

		if (layerID < 0 || wantedObject == null) {
			return false;
		}

		if (wantedObject.isMultiTileMaster()) {
			return isObjectComplete(level, layerID, layerObject, worldX, worldY);
		}

		Point masterPos = (Point)wantedObject
				.getMultiTile(layerObject.getRotation())
				.getMasterTilePos(worldX, worldY)
				.orElse(null);

		if (masterPos == null) {
			return false;
		}

		int localMasterX = masterPos.x - originX;
		int localMasterY = masterPos.y - originY;
		BlueprintElement masterElement = blueprintData.getElementAt(localMasterX, localMasterY);

		if (masterElement == null) {
			return false;
		}

		BlueprintLayerObject masterLayerObject = masterElement.getObjectAtLayer(layerObject.getLayerID());
		return masterLayerObject != null
				&& isObjectComplete(level, layerID, masterLayerObject, masterPos.x, masterPos.y);
	}

	public BlueprintClearTarget findFirstClearTarget(Level level) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int worldX = originX + x;
				int worldY = originY + y;
				BlueprintElement element = blueprintData.getElementAt(x, y);
				String wantedTileID = element == null ? null : element.getTileID();

				if (wantedTileID != null) {
					GameTile currentTile = level.getTile(worldX, worldY);
					GameTile wantedTile = TileRegistry.getTile(wantedTileID);

					if (currentTile.getID() != wantedTile.getID() && !wantedTile.canReplace(level, worldX, worldY)) {
						return new BlueprintClearTarget(BlueprintClearTarget.Type.TILE, worldX, worldY);
					}
				}

				for (int layerID : ObjectLayerRegistry.getLayerIDs()) {
					GameObject currentObject = level.getObject(layerID, worldX, worldY);

					if (currentObject instanceof AirObject) {
						continue;
					}

					String layerStringID = ObjectLayerRegistry.getLayerStringID(layerID);
					BlueprintLayerObject wanted = element == null ? null : element.getObjectAtLayer(layerStringID);

					if (wanted != null
							&& BlueprintObjectMaterialResolver.isPlacementPrerequisite(
									currentObject.getStringID(), wanted.getObjectID())) {
						continue;
					}

					GameObject wantedObject = wanted == null ? null : ObjectRegistry.getObject(wanted.getObjectID());

					if (wanted == null
							|| !isSameBlueprintObject(currentObject, wantedObject)
							|| level.getObjectRotation(layerID, worldX, worldY) != wanted.getRotation()
							|| !isBlueprintObjectComplete(level, element, wanted, worldX, worldY)) {
						return new BlueprintClearTarget(BlueprintClearTarget.Type.OBJECT, layerID, worldX, worldY);
					}
				}
			}
		}

		return null;
	}

	public boolean hasConstructionStarted() {
		return constructionStarted;
	}

	public void setConstructionStarted(boolean constructionStarted) {
		this.constructionStarted = constructionStarted;
	}

	public boolean setConstructionBlockedReason(String reason) {
		if (Objects.equals(constructionBlockedReason, reason)) {
			return false;
		}

		constructionBlockedReason = reason;
		return true;
	}

	public boolean clearConstructionBlockedReason() {
		if (constructionBlockedReason == null) {
			return false;
		}

		constructionBlockedReason = null;
		return true;
	}

	public String getConstructionBlockedReason() {
		return constructionBlockedReason;
	}

	public boolean isConstructionBlocked() {
		return constructionBlockedReason != null;
	}

	public SaveData getSaveData() {
		SaveData save = new SaveData("BLUEPRINT_AREA");
		save.addUnsafeString("uniqueID", uniqueID);
		save.addInt("settlementUniqueID", settlementUniqueID);
		save.addInt("originX", originX);
		save.addInt("originY", originY);
		save.addInt("width", width);
		save.addInt("height", height);
		save.addSafeString("blueprintData", blueprintData.toJson());
		save.addBoolean("constructionStarted", constructionStarted);
		save.addBoolean("constructionComplete", constructionComplete);

		if (constructionBlockedReason != null) {
			save.addUnsafeString("constructionBlockedReason", constructionBlockedReason);
		}

		if (!assignedBuilderIDs.isEmpty()) {
			SaveData buildersSave = new SaveData("BUILDERS");

			for (int builderUniqueID : assignedBuilderIDs) {
				SaveData builderSave = new SaveData("BUILDER");
				builderSave.addInt("uniqueID", builderUniqueID);
				Map<String, Integer> allocation = builderMaterialAllocations.get(builderUniqueID);

				if (allocation != null && !allocation.isEmpty()) {
					SaveData allocationSave = new SaveData("ALLOCATION");

					for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
						SaveData itemSave = new SaveData("ITEM");
						itemSave.addUnsafeString("itemID", entry.getKey());
						itemSave.addInt("amount", entry.getValue());
						allocationSave.addSaveData(itemSave);
					}

					builderSave.addSaveData(allocationSave);
				}

				buildersSave.addSaveData(builderSave);
			}

			save.addSaveData(buildersSave);
		}

		return save;
	}

	private void consumeBuilderMaterialAllocation(int builderUniqueID, String itemID, int amount) {
		Map<String, Integer> allocation = builderMaterialAllocations.get(builderUniqueID);

		if (allocation == null) {
			return;
		}

		int remaining = allocation.getOrDefault(itemID, 0) - amount;

		if (remaining > 0) {
			allocation.put(itemID, remaining);
		} else {
			allocation.remove(itemID);
		}

		if (allocation.isEmpty()) {
			builderMaterialAllocations.remove(builderUniqueID);
		}
	}

	public BuilderHumanMob consumeBuilderMaterial(Level level, String itemID) {
		for (BuilderHumanMob builder : getAssignedBuilders(level)) {
			ListIterator<InventoryItem> iterator = builder.getWorkInventory().listIterator();

			while (iterator.hasNext()) {
				InventoryItem item = iterator.next();

				if (!item.item.getStringID().equals(itemID) || item.getAmount() <= 0) {
					continue;
				}

				item.setAmount(item.getAmount() - 1);

				if (item.getAmount() <= 0) {
					iterator.remove();
				}

				builder.getWorkInventory().markDirty();
				consumeBuilderMaterialAllocation(builder.getUniqueID(), itemID, 1);
				return builder;
			}
		}

		return null;
	}

	private boolean isObjectComplete(
			Level level,
			int layerID,
			BlueprintLayerObject layerObject,
			int worldX,
			int worldY
	) {
		if (layerObject == null) {
			return true;
		}

		GameObject wantedObject = ObjectRegistry.getObject(layerObject.getObjectID());
		GameObject currentObject = level.getObject(layerID, worldX, worldY);

		if (wantedObject == null
				|| !isSameBlueprintObject(currentObject, wantedObject)
				|| level.getObjectRotation(layerID, worldX, worldY) != layerObject.getRotation()) {
			return false;
		}

		if (!wantedObject.isMultiTileMaster()) {
			return true;
		}

		for (Object valueObject : wantedObject.getMultiTile(layerObject.getRotation()).getIDs(worldX, worldY)) {
			MultiTile.CoordinateValue value = (MultiTile.CoordinateValue)valueObject;
			GameObject expectedObject = ObjectRegistry.getObject((Integer)value.value);
			GameObject actualObject = level.getObject(layerID, value.tileX, value.tileY);

			if (!isSameBlueprintObject(actualObject, expectedObject)
					|| level.getObjectRotation(layerID, value.tileX, value.tileY) != layerObject.getRotation()) {
				return false;
			}
		}

		return true;
	}

	private boolean isSameBlueprintObject(GameObject currentObject, GameObject wantedObject) {
		if (currentObject == null || wantedObject == null) {
			return false;
		}

		if (currentObject.getID() == wantedObject.getID()) {
			return true;
		}

		if (currentObject instanceof SwitchObject && wantedObject instanceof SwitchObject) {
			SwitchObject currentSwitch = (SwitchObject)currentObject;
			SwitchObject wantedSwitch = (SwitchObject)wantedObject;
			return currentSwitch.counterID == wantedObject.getID() && wantedSwitch.counterID == currentObject.getID();
		}

		return false;
	}

	private boolean isObjectPlacementObject(BlueprintLayerObject layerObject) {
		if (layerObject == null) {
			return false;
		}

		GameObject object = ObjectRegistry.getObject(layerObject.getObjectID());
		return object != null && object.isMultiTileMaster();
	}

	private boolean isWallPlacementObject(int layerID, BlueprintLayerObject layerObject) {
		if (layerID == ObjectLayerRegistry.WALL_DECOR) {
			return true;
		}

		GameObject object = ObjectRegistry.getObject(layerObject.getObjectID());
		return object instanceof WallTorchObject;
	}

	private int resolveLayerID(String layerStringID) {
		try {
			return ObjectLayerRegistry.getLayerID(layerStringID);
		} catch (Exception e) {
			return -1;
		}
	}

	public Map<String, Integer> getRequiredMaterials(Level level) {
		Map<String, Integer> required = new LinkedHashMap<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;
				String wantedTileID = element.getTileID();

				if (wantedTileID != null) {
					GameTile currentTile = level.getTile(worldX, worldY);
					GameTile wantedTile = TileRegistry.getTile(wantedTileID);

					if (currentTile.getID() != wantedTile.getID()) {
						required.merge(wantedTileID, 1, Integer::sum);
					}
				}

				for (BlueprintLayerObject layerObject : element.getObjectsInPlacementOrder()) {
					int layerID = resolveLayerID(layerObject.getLayerID());

					if (layerID < 0 || !isObjectPlacementObject(layerObject)) {
						continue;
					}

					if (!isObjectComplete(level, layerID, layerObject, worldX, worldY)) {
						String prerequisiteItemID = BlueprintObjectMaterialResolver.getPlacementPrerequisiteItemID(
								layerObject.getObjectID());

						if (prerequisiteItemID != null
								&& !BlueprintObjectMaterialResolver.isPlacementPrerequisite(
										level.getObject(layerID, worldX, worldY).getStringID(), layerObject.getObjectID())) {
							required.merge(prerequisiteItemID, 1, Integer::sum);
						}

						required.merge(BlueprintObjectMaterialResolver.getMaterialItemID(layerObject.getObjectID()), 1, Integer::sum);
					}
				}
			}
		}

		return required;
	}

	public void setBuilderMaterialAllocation(int builderUniqueID, Map<String, Integer> allocation) {
		if (allocation.isEmpty()) {
			clearBuilderMaterialAllocation(builderUniqueID);
		} else {
			builderMaterialAllocations.put(builderUniqueID, new LinkedHashMap<>(allocation));
		}
	}

	public void clearBuilderMaterialAllocation(int builderUniqueID) {
		builderMaterialAllocations.remove(builderUniqueID);
	}

	public Map<String, Integer> getAllocatedMaterialsExcept(int builderUniqueID) {
		Map<String, Integer> allocated = new HashMap<>();

		for (Map.Entry<Integer, Map<String, Integer>> builderEntry : builderMaterialAllocations.entrySet()) {
			if (builderEntry.getKey() == builderUniqueID) {
				continue;
			}

			for (Map.Entry<String, Integer> materialEntry : builderEntry.getValue().entrySet()) {
				allocated.merge(materialEntry.getKey(), materialEntry.getValue(), Integer::sum);
			}
		}

		return allocated;
	}

	public List<String> getOrderedRemainingMaterialIDs(Level level) {
		List<String> materials = new ArrayList<>();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null || element.getTileID() == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;
				GameTile currentTile = level.getTile(worldX, worldY);
				GameTile wantedTile = TileRegistry.getTile(element.getTileID());

				if (currentTile.getID() != wantedTile.getID()) {
					materials.add(element.getTileID());
				}
			}
		}

		addOrderedRemainingObjectMaterialIDs(level, materials, false);
		addOrderedRemainingObjectMaterialIDs(level, materials, true);
		return materials;
	}

	private void addOrderedRemainingObjectMaterialIDs(Level level, List<String> materials, boolean wallObjects) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				BlueprintElement element = blueprintData.getElementAt(x, y);

				if (element == null) {
					continue;
				}

				int worldX = originX + x;
				int worldY = originY + y;

				for (BlueprintLayerObject layerObject : element.getObjectsInPlacementOrder()) {
					int layerID = resolveLayerID(layerObject.getLayerID());

					if (layerID < 0 || isWallPlacementObject(layerID, layerObject) != wallObjects || !isObjectPlacementObject(layerObject)) {
						continue;
					}

					if (!isObjectComplete(level, layerID, layerObject, worldX, worldY)) {
						String prerequisiteItemID = BlueprintObjectMaterialResolver.getPlacementPrerequisiteItemID(
								layerObject.getObjectID());

						if (prerequisiteItemID != null
								&& !BlueprintObjectMaterialResolver.isPlacementPrerequisite(
										level.getObject(layerID, worldX, worldY).getStringID(), layerObject.getObjectID())) {
							materials.add(prerequisiteItemID);
						}

						materials.add(BlueprintObjectMaterialResolver.getMaterialItemID(layerObject.getObjectID()));
					}
				}
			}
		}
	}

	public boolean isConstructionComplete() {
		return constructionComplete;
	}

	public void setConstructionComplete(boolean constructionComplete) {
		this.constructionComplete = constructionComplete;
	}

	public boolean hasAssignedBuilders() {
		return !assignedBuilderIDs.isEmpty();
	}

	public static BlueprintArea fromLoadData(LoadData load) {
		String uniqueID = load.getUnsafeString("uniqueID");
		int settlementUniqueID = load.getInt("settlementUniqueID", 0, false);
		int originX = load.getInt("originX");
		int originY = load.getInt("originY");
		int width = load.getInt("width");
		int height = load.getInt("height");
		String json = load.getSafeString("blueprintData", null, false);

		if (json == null) {
			throw new IllegalStateException("Blueprint area is missing blueprintData");
		}

		BlueprintData blueprintData = BlueprintData.fromJson(json);
		boolean constructionStarted = load.getBoolean("constructionStarted", false, false);
		boolean constructionComplete = load.getBoolean("constructionComplete", false, false);
		String constructionBlockedReason = load.getUnsafeString("constructionBlockedReason", null, false);
		BlueprintArea area = new BlueprintArea(uniqueID, settlementUniqueID, originX, originY, width, height,
				blueprintData, constructionStarted, constructionComplete);
		area.constructionBlockedReason = constructionBlockedReason;
		LoadData buildersSave = load.getFirstLoadDataByName("BUILDERS");

		if (buildersSave != null) {
			for (Object builderObject : buildersSave.getLoadDataByName("BUILDER")) {
				LoadData builderLoad = (LoadData)builderObject;
				int builderUniqueID = builderLoad.getInt("uniqueID");
				area.assignedBuilderIDs.add(builderUniqueID);
				LoadData allocationSave = builderLoad.getFirstLoadDataByName("ALLOCATION");

				if (allocationSave != null) {
					Map<String, Integer> allocation = new LinkedHashMap<>();

					for (Object itemObject : allocationSave.getLoadDataByName("ITEM")) {
						LoadData itemLoad = (LoadData)itemObject;
						String itemID = itemLoad.getUnsafeString("itemID");
						int amount = itemLoad.getInt("amount");

						if (amount > 0) {
							allocation.put(itemID, amount);
						}
					}

					if (!allocation.isEmpty()) {
						area.builderMaterialAllocations.put(builderUniqueID, allocation);
					}
				}
			}
		}

		return area;
	}
}
