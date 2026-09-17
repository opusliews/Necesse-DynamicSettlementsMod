package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opusliews.earlygame.FirestarterSystem;

public class PacketFirestarterUse extends Packet {
	private final int tileX;
	private final int tileY;

	public PacketFirestarterUse(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	public PacketFirestarterUse(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!client.checkHasRequestedSelf() || client.isDead()) {
			return;
		}

		client.checkSpawned();
		FirestarterSystem.tryServerUse(client.playerMob, tileX, tileY);
	}
}
