package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.packet.PacketSettlementOpen;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Allows the normal settlement hotkey to open the canonical settlement while on its cave level. */
@ModMethodPatch(target = PacketSettlementOpen.class, name = "processServer", arguments = {NetworkPacket.class, Server.class, ServerClient.class})
public class PacketSettlementOpenMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.Argument(1) Server server, @Advice.Argument(2) ServerClient client) {
		if (server == null || client == null || client.playerMob == null) return false;
		Level level = server.world.getLevel(client);
		if (level == null) return false;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(server, level.getIdentifier(), client.playerMob.getTileX(), client.playerMob.getTileY());
		if (domain == null || domain.getSettlement() == null || domain.getSettlement().getLevel() == null) return false;
		ServerSettlementData settlement = domain.getSettlement();
		if (level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return false;

		PacketOpenContainer openPacket = PacketOpenContainer.Settlement(ContainerRegistry.SETTLEMENT_CONTAINER, settlement);
		ContainerRegistry.openAndSendContainer(client, openPacket);
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Opened settlement config from linked level settlement=" + settlement.uniqueID + " level=" + level.getIdentifier());
		return true;
	}
}
