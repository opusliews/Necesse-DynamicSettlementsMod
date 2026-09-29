package opusliews.patches;

import java.awt.Rectangle;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.inventory.Inventory;
import necesse.inventory.InventoryItem;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementRaidOptions;
import necesse.level.maps.levelData.settlementData.SettlementWealthCounter;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "getRaidOptions", arguments = {boolean.class})
public class SettlementRaidWealthMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement,
			@Advice.Return SettlementRaidOptions options) {
		if (options != null) addCaveInventoryWealth(settlement, options.wealthCounter);
	}

	public static void addCaveInventoryWealth(ServerSettlementData settlement, SettlementWealthCounter counter) {
		if (settlement == null || counter == null || settlement.getServer() == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return;
		LevelIdentifier caveIdentifier = domain.getLevelIdentifier(SettlementLevelType.CAVE);
		if (caveIdentifier == null) return;
		Level cave = settlement.getServer().world.levelManager.getLevel(caveIdentifier);
		if (cave == null) return;
		Rectangle bounds = domain.getTileBounds(SettlementLevelType.CAVE);
		if (bounds == null) return;

		for (int tileX = bounds.x; tileX < bounds.x + bounds.width; tileX++) {
			for (int tileY = bounds.y; tileY < bounds.y + bounds.height; tileY++) {
				ObjectEntity objectEntity = cave.entityManager.getObjectEntity(tileX, tileY);
				if (!(objectEntity instanceof OEInventory)) continue;
				Inventory inventory = ((OEInventory)objectEntity).getInventory();
				if (inventory == null) continue;
				for (int slot = 0; slot < inventory.getSize(); slot++) {
					InventoryItem item = inventory.getItem(slot);
					if (item != null) counter.addStoredItem(Integer.MIN_VALUE, Integer.MIN_VALUE, item);
				}
			}
		}
	}
}
