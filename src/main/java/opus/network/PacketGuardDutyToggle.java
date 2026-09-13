package opus.network;

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
import opus.guard.GuardDuty;
import opus.guard.GuardDutySystem;

public class PacketGuardDutyToggle extends Packet {
	private final int guardUniqueID;
	private final boolean nightDuty;

	public PacketGuardDutyToggle(int guardUniqueID, boolean nightDuty) {
		this.guardUniqueID = guardUniqueID;
		this.nightDuty = nightDuty;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(guardUniqueID);
		writer.putNextBoolean(nightDuty);
	}

	public PacketGuardDutyToggle(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		guardUniqueID = reader.getNextInt();
		nightDuty = reader.getNextBoolean();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		Level level = client.getLevel();
		if (level == null) {
			return;
		}

		Mob mob = GameUtils.getLevelMob(guardUniqueID, level);
		if (!(mob instanceof GuardHumanMob)) {
			return;
		}

		GuardHumanMob guard = (GuardHumanMob)mob;
		if (!guard.isSettlerOnCurrentLevel() || guard.adventureParty.isInAdventureParty()) {
			return;
		}

		int settlementUniqueID = guard.getSettlementUniqueID();
		CachedSettlementData cached = SettlementsWorldData.getSettlementsData(server).getCachedData(settlementUniqueID);
		if (cached == null || !cached.hasAccess(client)) {
			return;
		}

		GuardDutySystem.setDuty(guard, nightDuty ? GuardDuty.NIGHT : GuardDuty.DAY);
		server.network.sendToClientsWithEntity(new PacketGuardDutyToggle(guardUniqueID, nightDuty), guard);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null) {
			return;
		}

		Mob mob = GameUtils.getLevelMob(guardUniqueID, level);
		if (mob instanceof GuardHumanMob) {
			GuardDutySystem.setDuty(
					(GuardHumanMob)mob,
					nightDuty ? GuardDuty.NIGHT : GuardDuty.DAY
			);
		}
	}
}
