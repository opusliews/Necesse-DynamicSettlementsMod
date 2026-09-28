package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.container.settlement.actions.storage.ChangeLimitsSettlementStorageAction;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementStorageLimitsEvent;
import necesse.inventory.item.Item;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageActionSupport;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = ChangeLimitsSettlementStorageAction.class, name = "executePacket", arguments = {PacketReader.class})
public class ChangeLimitsSettlementStorageMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ChangeLimitsSettlementStorageAction action, @Advice.Argument(0) PacketReader reader) {
		SettlementLevelStorageActionSupport.Context context = SettlementLevelStorageActionSupport.getCustomContext(action.container);
		if (context == null) return false;
		int tileX = reader.getNextInt();
		int tileY = reader.getNextInt();
		if (!context.hasAccess) {
			new SettlementDataEvent(context.settlement).applyAndSendToClient(context.client);
			return true;
		}
		SettlementInventory storage = SettlementLevelStorageManager.getStorage(context.settlement, context.level.getIdentifier(), tileX, tileY);
		if (storage == null) return true;
		boolean isItems = reader.getNextBoolean();
		if (isItems) {
			Item item = ItemRegistry.getItem(reader.getNextShortUnsigned());
			ItemCategoriesFilter.ItemLimits limits = new ItemCategoriesFilter.ItemLimits();
			limits.readPacket(reader);
			storage.filter.setItemAllowed(item, limits);
			new SettlementStorageLimitsEvent(context.settlement, tileX, tileY, item, limits).applyAndSendToClientsAt(context.level);
		}
		else {
			int categoryID = reader.getNextShortUnsigned();
			int maxAmount = reader.getNextInt();
			ItemCategoriesFilter.ItemCategoryFilter category = storage.filter.getItemCategory(categoryID);
			category.setMaxItems(maxAmount);
			new SettlementStorageLimitsEvent(context.settlement, tileX, tileY, category.category, maxAmount).applyAndSendToClientsAt(context.level);
		}
		SettlementLevelStorageManager.persistStorageConfig(context.settlement, storage);
		return true;
	}
}
