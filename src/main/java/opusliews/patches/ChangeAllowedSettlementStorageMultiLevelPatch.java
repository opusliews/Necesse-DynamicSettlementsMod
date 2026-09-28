package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.ItemRegistry;
import necesse.inventory.container.settlement.actions.storage.ChangeAllowedSettlementStorageAction;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementStorageChangeAllowedEvent;
import necesse.inventory.item.Item;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageActionSupport;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = ChangeAllowedSettlementStorageAction.class, name = "executePacket", arguments = {PacketReader.class})
public class ChangeAllowedSettlementStorageMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ChangeAllowedSettlementStorageAction action, @Advice.Argument(0) PacketReader reader) {
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
		boolean allowed = reader.getNextBoolean();
		boolean isItems = reader.getNextBoolean();
		if (isItems) {
			int count = reader.getNextShortUnsigned();
			Item[] items = new Item[count];
			for (int i = 0; i < count; i++) {
				Item item = ItemRegistry.getItem(reader.getNextShortUnsigned());
				items[i] = item;
				storage.filter.setItemAllowed(item, allowed);
			}
			new SettlementStorageChangeAllowedEvent(context.settlement, tileX, tileY, items, allowed).applyAndSendToClientsAt(context.level);
		}
		else {
			int categoryID = reader.getNextShortUnsigned();
			ItemCategoriesFilter.ItemCategoryFilter category = storage.filter.getItemCategory(categoryID);
			category.setAllowed(allowed);
			new SettlementStorageChangeAllowedEvent(context.settlement, tileX, tileY, category.category, allowed).applyAndSendToClientsAt(context.level);
		}
		SettlementLevelStorageManager.persistStorageConfig(context.settlement, storage);
		return true;
	}
}
