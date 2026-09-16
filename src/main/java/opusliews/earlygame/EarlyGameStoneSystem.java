package opusliews.earlygame;

import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.SingleRockSmall;
import necesse.level.maps.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EarlyGameStoneSystem {
	static private final List<String> rockObjectIds = new ArrayList<>(Arrays.asList(
			"surfacerocksmall",
			"caverocksmall",
			"charredrocksmall",
			"snowsurfacerocksmall",
			"snowcaverocksmall",
			"sandsurfacerocksmall",
			"sandcaverocksmall",
			"swampsurfacerocksmall",
			"swampcaverocksmall",
			"granitecaverocksmall",
			"deepcaverocksmall",
			"deepsnowcaverocksmall",
			"basaltcaverocksmall",
			"deepswampcaverocksmall",
			"deepsandcaverocksmall",
			"slimecaverocksmall",
			"spidercaverocksmall",
			"incursioncavelingcaverocks"));

	private EarlyGameStoneSystem() {
	}

	public static boolean isHarvestableSmallRock(GameObject object, Level level, int tileX, int tileY) {
		if (!(object instanceof SingleRockSmall) || level == null || level.isProtected(tileX, tileY) || level.objectLayer.isPlayerPlaced(tileX, tileY)) {
			return false;
		}

		EarlyGameLevelData data = EarlyGameLevelData.get(level, false);
		return data == null || !data.hasHarvestedSmallRock(tileX, tileY);
	}

	public static void harvestSmallRock(GameObject object, Level level, int tileX, int tileY, PlayerMob player) {
		if (!isHarvestableSmallRock(object, level, tileX, tileY)) {
			return;
		}

		String stoneItemID = getStoneItemID(object.getStringID());
		if (stoneItemID == null || ItemRegistry.getItemID(stoneItemID) == -1) {
			return;
		}

		EarlyGameLevelData.get(level, true).markSmallRockHarvested(tileX, tileY);
		if (level.isServer()) {
			InventoryItem stone = new InventoryItem(stoneItemID, 1);
			level.entityManager.pickups.add(stone.getPickupEntity(level, tileX * 32.0F + 16.0F, tileY * 32.0F + 16.0F));
		}
	}

	private static String getStoneItemID(String objectStringID) {
		if (objectStringID == null) {
			return null;
		}

		switch (objectStringID) {
			case "surfacerocksmall":
			case "caverocksmall":
			case "charredrocksmall":
				return "stone";
			case "snowsurfacerocksmall":
			case "snowcaverocksmall":
				return "snowstone";
			case "sandsurfacerocksmall":
			case "sandcaverocksmall":
				return "sandstone";
			case "swampsurfacerocksmall":
			case "swampcaverocksmall":
				return "swampstone";
			case "granitecaverocksmall":
				return "granite";
			case "deepcaverocksmall":
				return "deepstone";
			case "deepsnowcaverocksmall":
				return "deepsnowstone";
			case "basaltcaverocksmall":
				return "basalt";
			case "deepswampcaverocksmall":
				return "deepswampstone";
			case "deepsandcaverocksmall":
				return "deepsandstone";
			case "slimecaverocksmall":
				return "slimestone";
			case "spidercaverocksmall":
				return "spiderstone";
			case "incursioncavelingcaverocks":
				return "deepstone";
			default:
				return null;
		}
	}

	public static boolean isRockObject(GameObject gameObject) {
		if (gameObject == null) {
			return false;
		}

        return rockObjectIds.contains(gameObject.getStringID());
    }
}
