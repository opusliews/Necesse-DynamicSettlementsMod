package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.util.GameUtils;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import opusliews.guard.GuardDutyDialogueRefresh;
import opusliews.guard.GuardLevelAssignment;
import opusliews.guard.GuardLevelAssignmentSystem;
import opusliews.logging.Logging;
import opusliews.progression.GuideProgressionSystem;

public class PacketGuardLevelAssignment extends Packet {
	private final int guardUniqueID;
	private final GuardLevelAssignment assignment;

	public PacketGuardLevelAssignment(int guardUniqueID, GuardLevelAssignment assignment) {
		this.guardUniqueID = guardUniqueID;
		this.assignment = assignment == null ? GuardLevelAssignment.SURFACE : assignment;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(guardUniqueID);
		writer.putNextByteUnsigned(this.assignment.ordinal());
	}

	public PacketGuardLevelAssignment(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		guardUniqueID = reader.getNextInt();
		assignment = GuardLevelAssignment.fromOrdinal(reader.getNextByteUnsigned());
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		Level level = client.getLevel();
		if (level == null) {
			if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Rejected update because client level is unavailable guard=" + guardUniqueID);
			return;
		}
		Mob mob = GameUtils.getLevelMob(guardUniqueID, level);
		if (!(mob instanceof GuardHumanMob)) {
			if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Rejected update because guard is unavailable on client level guard=" + guardUniqueID);
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!guard.isSettlerOnCurrentLevel() || guard.adventureParty.isInAdventureParty()) {
			if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Rejected update because guard is not an available settlement guard guard=" + guardUniqueID);
			return;
		}

		CachedSettlementData cached = SettlementsWorldData.getSettlementsData(server).getCachedData(guard.getSettlementUniqueID());
		if (cached == null || !cached.hasAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[GuardLevel] Rejected update because player lacks settlement access guard=" + guardUniqueID);
			return;
		}

		GuardLevelAssignmentSystem.setAssignment(guard, assignment);
		if (assignment == GuardLevelAssignment.CAVE) GuideProgressionSystem.onGuardAssignedCave(client);
		server.network.sendToClientsWithEntity(new PacketGuardLevelAssignment(guardUniqueID, assignment), guard);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null) return;
		Mob mob = GameUtils.getLevelMob(guardUniqueID, level);
		if (mob instanceof GuardHumanMob) {
			GuardLevelAssignmentSystem.setAssignment((GuardHumanMob)mob, assignment);
			GuardDutyDialogueRefresh.refresh(guardUniqueID);
		}
	}
}
