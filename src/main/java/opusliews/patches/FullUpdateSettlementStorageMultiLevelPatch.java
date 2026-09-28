package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.inventory.container.settlement.actions.storage.FullUpdateSettlementStorageAction;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementStorageFullUpdateEvent;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.multilevelsettlement.SettlementLevelStorageActionSupport;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;

@ModMethodPatch(target = FullUpdateSettlementStorageAction.class, name = "executePacket", arguments = {PacketReader.class})
public class FullUpdateSettlementStorageMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This FullUpdateSettlementStorageAction action, @Advice.Argument(0) PacketReader reader) {
		SettlementLevelStorageActionSupport.Context context = SettlementLevelStorageActionSupport.getCustomContext(action.container);
		if (context == null) return false;
		int tileX = reader.getNextInt();
		int tileY = reader.getNextInt();
		int priority = reader.getNextInt();
		if (!context.hasAccess) {
			new SettlementDataEvent(context.settlement).applyAndSendToClient(context.client);
			return true;
		}
		SettlementInventory storage = SettlementLevelStorageManager.getStorage(context.settlement, context.level.getIdentifier(), tileX, tileY);
		if (storage == null) return true;
		storage.priority = priority;
		storage.filter.readPacket(reader);
		new SettlementStorageFullUpdateEvent(context.settlement, tileX, tileY, storage.filter, storage.priority).applyAndSendToClientsAt(context.level);
		SettlementLevelStorageManager.persistStorageConfig(context.settlement, storage);
		return true;
	}
}
