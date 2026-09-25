package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.packet.PacketObjectInteract;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.PlayerMob;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.object.TrapdoorObject;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(
		target = PacketObjectInteract.class,
		name = "processServer",
		arguments = {NetworkPacket.class, Server.class, ServerClient.class}
)
public class TrapdoorObjectInteractServerPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This PacketObjectInteract packet,
			@Advice.Argument(1) Server server,
			@Advice.Argument(2) ServerClient client
	) {
		if (client == null || client.playerMob == null) return false;
		PlayerMob player = client.playerMob;
		if (!TrapdoorSystem.isTrapdoorHidden(player)) return false;

		TrapdoorSystem.reconcilePlayerState(player);
		if (!TrapdoorSystem.isTrapdoorHidden(player)) return true;

		Level level = server.world.getLevel(client);
		if (level == null || level.getIdentifierHashCode() != packet.levelIdentifierHashCode) return true;
		if (packet.tileX != player.getTileX() || packet.tileY != player.getTileY()) return true;

		return !(level.getObject(packet.tileX, packet.tileY) instanceof TrapdoorObject);
	}
}
