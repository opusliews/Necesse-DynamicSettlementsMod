package opusliews.object;

import java.util.ArrayList;
import necesse.gfx.gameTexture.GameTexture;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.WallTorchObject;
import necesse.level.maps.Level;
import opusliews.item.CrudeTorchItem;
import opusliews.torch.CrudeTorchLevelData;

public class CrudeWallTorchObject extends WallTorchObject {
	public static final String stringID = "crudewalltorch";

	public CrudeWallTorchObject() {
		super();
		this.itemDroppedStringID = CrudeTorchObject.stringID;
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		this.texture = GameTexture.fromFile("objects/" + stringID);
	}

	@Override
	public boolean isActive(Level level, int layerID, int tileX, int tileY) {
		return true;
	}

	@Override
	public ArrayList<InventoryItem> getObjectDroppedItems(Level level, int layerID, int tileX, int tileY, String purpose) {
		ArrayList<InventoryItem> drops = new ArrayList<>();
		CrudeTorchLevelData data = CrudeTorchLevelData.get(level, false);
		CrudeTorchLevelData.TrackedTorch tracked = data == null ? null : data.getTrackedTorch(layerID, tileX, tileY);
		if (tracked == null || level.getWorldTime() >= tracked.expireWorldTime) return drops;

		InventoryItem drop = new InventoryItem(CrudeTorchObject.stringID, 1);
		CrudeTorchItem.setRunningState(drop, tracked.expireWorldTime, tracked.duration);
		drops.add(drop);
		return drops;
	}
}
