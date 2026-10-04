package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.inventory.container.settlement.actions.storage.RemoveSettlementStorageAction;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementSingleStorageEvent;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelStorageActionSupport;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.network.PacketSettlementStorageSync;

@ModMethodPatch(target = RemoveSettlementStorageAction.class, name = "executePacket", arguments = {PacketReader.class})
public class RemoveSettlementStorageMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This RemoveSettlementStorageAction action, @Advice.Argument(0) PacketReader reader) {
		SettlementLevelStorageActionSupport.Context context = SettlementLevelStorageActionSupport.getCustomContext(action.container);
		if (context == null) return false;
		int tileX = reader.getNextInt();
		int tileY = reader.getNextInt();
		if (!context.hasAccess) {
			new SettlementDataEvent(context.settlement).applyAndSendToClient(context.client);
			return true;
		}
		SettlementLevelStorageManager.removeStorage(context.settlement, context.level.getIdentifier(), tileX, tileY);

		Packet eventContent = new Packet();
		PacketWriter eventWriter = new PacketWriter(eventContent);
		eventWriter.putNextInt(context.settlement.uniqueID);
		eventWriter.putNextInt(tileX);
		eventWriter.putNextInt(tileY);
		eventWriter.putNextBoolean(false);
		new SettlementSingleStorageEvent(new PacketReader(eventContent)).applyAndSendToClient(context.client);

		context.client.sendPacket(new PacketSettlementStorageSync(context.settlement.uniqueID, context.level.getIdentifierHashCode(), true,
				SettlementLevelStorageManager.getStoragePositions(context.settlement, context.level.getIdentifier())));
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Sent removed-storage state to object status UI settlement="
				+ context.settlement.uniqueID + " level=" + context.level.getIdentifier() + " tile=" + tileX + "," + tileY);
		return true;
	}
}
