package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opusliews.deephole.DeepHoleSystem;

public class PacketDeepHoleInteract extends Packet {
	private final int tileX;
	private final int tileY;
	private final boolean enter;

	public PacketDeepHoleInteract(int tileX, int tileY, boolean enter) {
		this.tileX = tileX;
		this.tileY = tileY;
		this.enter = enter;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
		writer.putNextBoolean(enter);
	}

	public PacketDeepHoleInteract(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
		enter = reader.getNextBoolean();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!client.checkHasRequestedSelf() || client.isDead()) return;
		client.checkSpawned();
		DeepHoleSystem.tryServerInteract(client.playerMob, tileX, tileY, enter);
	}
}
