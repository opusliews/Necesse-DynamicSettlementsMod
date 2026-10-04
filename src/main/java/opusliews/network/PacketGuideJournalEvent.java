package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;

public class PacketGuideJournalEvent extends Packet {
	public static final int CHARCOAL_ZONE_TOOL_OPENED = 1;
	public static final int CLAY_ZONE_TOOL_OPENED = 2;

	private final int eventID;

	public PacketGuideJournalEvent(int eventID) {
		this.eventID = eventID;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextByteUnsigned(eventID);
	}

	public PacketGuideJournalEvent(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		eventID = reader.getNextByteUnsigned();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (client == null || client.playerMob == null) return;
		switch (eventID) {
			case CHARCOAL_ZONE_TOOL_OPENED:
				GuideProgressionSystem.onCharcoalZoneToolOpened(client);
				break;
			case CLAY_ZONE_TOOL_OPENED:
				GuideProgressionSystem.onClayZoneToolOpened(client);
				break;
			default:
				if (Logging.logEnabled) Logging.logMessage("[GuideProgression] Ignored unknown journal event=" + eventID + " player=" + client.getName());
				break;
		}
	}
}
