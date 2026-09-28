package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.storage.PriorityLimitSettlementStorageAction;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementStoragePriorityLimitEvent;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageActionSupport;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = PriorityLimitSettlementStorageAction.class, name = "executePacket", arguments = {PacketReader.class})
public class PriorityLimitSettlementStorageMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This PriorityLimitSettlementStorageAction action, @Advice.Argument(0) PacketReader reader) {
		SettlementLevelStorageActionSupport.Context context = SettlementLevelStorageActionSupport.getCustomContext(action.container);
		if (context == null) return false;
		int tileX = reader.getNextInt();
		int tileY = reader.getNextInt();
		boolean isPriority = reader.getNextBoolean();
		if (!context.hasAccess) {
			new SettlementDataEvent(context.settlement).applyAndSendToClient(context.client);
			return true;
		}
		SettlementInventory storage = SettlementLevelStorageManager.getStorage(context.settlement, context.level.getIdentifier(), tileX, tileY);
		if (storage == null) return true;
		if (isPriority) {
			int priority = reader.getNextInt();
			storage.priority = priority;
			new SettlementStoragePriorityLimitEvent(context.settlement, tileX, tileY, priority).applyAndSendToClientsAt(context.level);
		}
		else {
			ItemCategoriesFilter.ItemLimitMode mode = reader.getNextEnum(ItemCategoriesFilter.ItemLimitMode.class);
			int limit = reader.getNextInt();
			storage.filter.limitMode = mode;
			storage.filter.maxAmount = limit;
			new SettlementStoragePriorityLimitEvent(context.settlement, tileX, tileY, mode, limit).applyAndSendToClientsAt(context.level);
		}
		SettlementLevelStorageManager.persistStorageConfig(context.settlement, storage);
		return true;
	}
}
