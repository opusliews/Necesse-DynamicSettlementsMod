package opusliews.network;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ContainerRegistry;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Opens the canonical settlement container while the player is on a linked settlement level. */
public class PacketOpenMultiLevelSettlement extends Packet {
	public PacketOpenMultiLevelSettlement() {
	}

	public PacketOpenMultiLevelSettlement(byte[] data) {
		super(data);
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (server == null || client == null || client.playerMob == null) return;
		Level level = server.world.getLevel(client);
		if (level == null) return;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomain(
				server,
				level.getIdentifier(),
				client.playerMob.getTileX(),
				client.playerMob.getTileY()
		);
		if (domain == null || domain.getSettlement() == null) {
			client.sendChatMessage((GameMessage)new LocalMessage("ui", "settlementsurface"));
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Settlement hotkey failed: no linked settlement level=" + level.getIdentifier() + " tile=" + client.playerMob.getTileX() + "," + client.playerMob.getTileY());
			return;
		}

		ServerSettlementData settlement = domain.getSettlement();
		if (settlement.networkData.isDisbandingPrevented() && !settlement.hasFlag()) {
			client.sendChatMessage((GameMessage)new LocalMessage("ui", "settlementnone"));
			return;
		}

		PacketOpenContainer openPacket = PacketOpenContainer.Settlement(ContainerRegistry.SETTLEMENT_CONTAINER, settlement);
		ContainerRegistry.openAndSendContainer(client, openPacket);
		GuideProgressionSystem.onCaveSettlementManagementOpened(client);
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Opened settlement config through custom linked-level packet settlement=" + settlement.uniqueID + " level=" + level.getIdentifier());
	}
}
