package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.packet.PacketObjectInteract;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLadderSystem;

@ModMethodPatch(
		target = PacketObjectInteract.class,
		name = "processServer",
		arguments = {NetworkPacket.class, Server.class, ServerClient.class}
)
public class SettlementLadderVanillaInteractServerPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This PacketObjectInteract packet,
			@Advice.Argument(1) Server server,
			@Advice.Argument(2) ServerClient client
	) {
		if (server == null || client == null || client.playerMob == null) return false;
		if (!SettlementLadderSystem.isHoldingSettlementFlag(client.playerMob)) return false;

		Level level = server.world.getLevel(client);
		if (level == null || level.getIdentifierHashCode() != packet.levelIdentifierHashCode) return false;
		if (!SettlementLadderSystem.isSupportedLadderObject(level.getObject(packet.tileX, packet.tileY))) return false;

		if (Logging.logEnabled) Logging.logMessage("[SettlementLadder] Suppressed vanilla ladder interaction after settlement-ladder toggle request level=" + level.getIdentifier() + " tile=" + packet.tileX + "," + packet.tileY + " player=" + client.authentication);
		return true;
	}
}
