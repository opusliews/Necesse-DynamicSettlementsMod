package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementPlayerBedSystem;

public class PacketPlayerSettlementBedAction extends Packet {
	private final boolean toggle;

	public PacketPlayerSettlementBedAction(boolean toggle) {
		this.toggle = toggle;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextBoolean(toggle);
	}

	public PacketPlayerSettlementBedAction(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		toggle = reader.getNextBoolean();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (client == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBed] Ignored bed action packet with null client");
			return;
		}
		if (toggle) SettlementPlayerBedSystem.handleBedToggle(client);
		else SettlementPlayerBedSystem.handleBedQuery(client);
	}
}
