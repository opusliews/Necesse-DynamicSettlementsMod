package opusliews.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.util.LevelIdentifier;
import necesse.gfx.HumanLook;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementPlayerBedSystem;
import opusliews.settlement.SettlementPlayerBedSystem.ClientAssignment;
import opusliews.settlement.SettlementPlayerBedSystem.PlayerBedAssignment;
import opusliews.sleep.PlayerSettlementBedUI;

public class PacketPlayerSettlementBedSync extends Packet {
	private final int settlementUniqueID;
	private final List<ClientAssignment> assignments;
	private final boolean hasBedContext;
	private final LevelIdentifier contextLevel;
	private final int contextTileX;
	private final int contextTileY;
	private final boolean currentBedAssignedToPlayer;

	public static PacketPlayerSettlementBedSync noSettlementContext() {
		return new PacketPlayerSettlementBedSync(0, Collections.emptyList(), true, null, 0, 0, false);
	}

	public PacketPlayerSettlementBedSync(int settlementUniqueID, List<PlayerBedAssignment> assignments, boolean hasBedContext,
			LevelIdentifier contextLevel, int contextTileX, int contextTileY, boolean currentBedAssignedToPlayer) {
		this.settlementUniqueID = settlementUniqueID;
		this.assignments = new ArrayList<>();
		if (assignments != null) {
			for (PlayerBedAssignment assignment : assignments) {
				if (assignment == null || assignment.levelIdentifier == null) continue;
				this.assignments.add(new ClientAssignment(assignment.playerName, assignment.look, assignment.levelIdentifier, assignment.tileX, assignment.tileY));
			}
		}
		this.hasBedContext = hasBedContext;
		this.contextLevel = contextLevel;
		this.contextTileX = contextTileX;
		this.contextTileY = contextTileY;
		this.currentBedAssignedToPlayer = currentBedAssignedToPlayer;
		writePacket();
	}

	public PacketPlayerSettlementBedSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		int count = reader.getNextShortUnsigned();
		assignments = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String playerName = reader.getNextString();
			LevelIdentifier levelIdentifier = new LevelIdentifier(reader);
			int tileX = reader.getNextInt();
			int tileY = reader.getNextInt();
			HumanLook look = new HumanLook(reader);
			assignments.add(new ClientAssignment(playerName, look, levelIdentifier, tileX, tileY));
		}
		hasBedContext = reader.getNextBoolean();
		if (hasBedContext) {
			boolean hasContextLevel = reader.getNextBoolean();
			contextLevel = hasContextLevel ? new LevelIdentifier(reader) : null;
			contextTileX = reader.getNextInt();
			contextTileY = reader.getNextInt();
			currentBedAssignedToPlayer = reader.getNextBoolean();
		} else {
			contextLevel = null;
			contextTileX = 0;
			contextTileY = 0;
			currentBedAssignedToPlayer = false;
		}
	}

	private void writePacket() {
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextShortUnsigned(assignments.size());
		for (ClientAssignment assignment : assignments) {
			writer.putNextString(assignment.playerName == null ? "" : assignment.playerName);
			assignment.levelIdentifier.writePacket(writer);
			writer.putNextInt(assignment.tileX);
			writer.putNextInt(assignment.tileY);
			assignment.look.setupContentPacket(writer, true);
		}
		writer.putNextBoolean(hasBedContext);
		if (hasBedContext) {
			writer.putNextBoolean(contextLevel != null);
			if (contextLevel != null) contextLevel.writePacket(writer);
			writer.putNextInt(contextTileX);
			writer.putNextInt(contextTileY);
			writer.putNextBoolean(currentBedAssignedToPlayer);
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		if (settlementUniqueID != 0) SettlementPlayerBedSystem.applyClientSync(settlementUniqueID, assignments);
		if (hasBedContext) {
			PlayerSettlementBedUI.applyServerState(settlementUniqueID, contextLevel, contextTileX, contextTileY, currentBedAssignedToPlayer);
		}
		if (Logging.logEnabled) {
			Logging.logMessage("[PlayerSettlementBed] Client received sync settlement=" + settlementUniqueID + " assignments=" + assignments.size()
					+ " hasBedContext=" + hasBedContext + (hasBedContext ? " context=" + contextLevel + "@" + contextTileX + "," + contextTileY + " own=" + currentBedAssignedToPlayer : ""));
		}
	}
}
