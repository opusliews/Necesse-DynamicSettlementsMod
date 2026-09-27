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
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.CachedSettlementData;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelPreference;
import opusliews.multilevelsettlement.SettlementLevelPreferenceSystem;

public class PacketSettlerLevelPreference extends Packet {
	private final int mobUniqueID;
	private final SettlementLevelPreference preference;

	public PacketSettlerLevelPreference(int mobUniqueID, SettlementLevelPreference preference) {
		this.mobUniqueID = mobUniqueID;
		this.preference = preference == null ? SettlementLevelPreference.AUTO : preference;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(mobUniqueID);
		writer.putNextByteUnsigned(this.preference.ordinal());
	}

	public PacketSettlerLevelPreference(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		mobUniqueID = reader.getNextInt();
		preference = SettlementLevelPreference.fromOrdinal(reader.getNextByteUnsigned());
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		Level level = client.getLevel();
		if (level == null) return;
		Mob mob = GameUtils.getLevelMob(mobUniqueID, level);
		if (!(mob instanceof HumanMob)) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] Rejected update because mob was not a HumanMob mob=" + mobUniqueID);
			return;
		}
		HumanMob human = (HumanMob)mob;
		if (!human.isSettlerOnCurrentLevel() || human.adventureParty.isInAdventureParty()) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] Rejected update because settler is unavailable mob=" + mobUniqueID);
			return;
		}
		CachedSettlementData cached = SettlementsWorldData.getSettlementsData(server).getCachedData(human.getSettlementUniqueID());
		if (cached == null || !cached.hasAccess(client)) {
			if (Logging.logEnabled) Logging.logMessage("[LevelPreference] Rejected update because player lacks settlement access mob=" + mobUniqueID);
			return;
		}
		SettlementLevelPreferenceSystem.setPreference(human, preference);
		server.network.sendToClientsWithEntity(new PacketSettlerLevelPreference(mobUniqueID, preference), human);
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null) return;
		Mob mob = GameUtils.getLevelMob(mobUniqueID, level);
		if (mob instanceof HumanMob) SettlementLevelPreferenceSystem.setPreference((HumanMob)mob, preference);
	}
}
