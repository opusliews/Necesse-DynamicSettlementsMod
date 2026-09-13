package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.GuardHumanMob;
import necesse.level.maps.Level;
import opus.guard.GuardFatigueSystem;

public class PacketGuardFatigueUpdate extends Packet {
	private final int guardUniqueID;
	private final int fatigue;

	public PacketGuardFatigueUpdate(int guardUniqueID, int fatigue) {
		this.guardUniqueID = guardUniqueID;
		this.fatigue = fatigue;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(guardUniqueID);
		writer.putNextInt(fatigue);
	}

	public PacketGuardFatigueUpdate(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		guardUniqueID = reader.getNextInt();
		fatigue = reader.getNextInt();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		Level level = client.getLevel();
		if (level == null) {
			return;
		}

		Mob mob = GameUtils.getLevelMob(guardUniqueID, level);
		if (mob instanceof GuardHumanMob) {
			GuardFatigueSystem.applyClientFatigue((GuardHumanMob)mob, fatigue);
		}
	}
}
