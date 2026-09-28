package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.container.object.OEInventoryContainer;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelObjectStatusSupport;

/** Supplies the logical surface settlement to chest containers opened on a linked cave level. */
@ModMethodPatch(target = OEInventoryContainer.class, name = "openAndSendContainer", arguments = {int.class, ServerClient.class, Level.class, int.class, int.class, Packet.class})
public class OEInventoryContainerMultiLevelSettlementPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) int containerID,
			@Advice.Argument(1) ServerClient client,
			@Advice.Argument(2) Level level,
			@Advice.Argument(3) int tileX,
			@Advice.Argument(4) int tileY,
			@Advice.Argument(5) Packet extraContent) {
		if (client == null || level == null || !level.isServer()) return false;
		ServerSettlementData settlement = SettlementLevelObjectStatusSupport.resolveSettlement(level, tileX, tileY);
		if (settlement == null || settlement.getLevel() == null || level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return false;

		Packet packet = new Packet();
		PacketWriter writer = new PacketWriter(packet);
		if (!SettlementLevelObjectStatusSupport.writeCustomContent(settlement, level, tileX, tileY, writer)) return false;
		if (extraContent != null) writer.putNextContentPacket(extraContent);

		ObjectEntity objectEntity = level.entityManager.getObjectEntity(tileX, tileY);
		if (objectEntity == null) return false;
		PacketOpenContainer openPacket = PacketOpenContainer.SettlementObjectEntity(containerID, settlement, objectEntity, packet);
		ContainerRegistry.openAndSendContainer(client, openPacket);
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Opened settlement-aware inventory container settlement=" + settlement.uniqueID + " level=" + level.getIdentifier() + " tile=" + tileX + "," + tileY);
		return true;
	}
}
