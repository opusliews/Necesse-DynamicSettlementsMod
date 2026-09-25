package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.progression.EarlyHealthProgressionSystem;

public class PacketEarlyProgressionSync extends Packet {
	private final int hostileKills;
	private final int oreMined;
	private final int uniqueFoods;
	private final long caveTime;
	private final long caveTarget;
	private final boolean hostileKillsRewarded;
	private final boolean oreMinedRewarded;
	private final boolean uniqueFoodsRewarded;
	private final boolean caveTimeRewarded;

	public PacketEarlyProgressionSync(int hostileKills, int oreMined, int uniqueFoods, long caveTime, long caveTarget, boolean hostileKillsRewarded, boolean oreMinedRewarded, boolean uniqueFoodsRewarded, boolean caveTimeRewarded) {
		this.hostileKills = hostileKills;
		this.oreMined = oreMined;
		this.uniqueFoods = uniqueFoods;
		this.caveTime = caveTime;
		this.caveTarget = caveTarget;
		this.hostileKillsRewarded = hostileKillsRewarded;
		this.oreMinedRewarded = oreMinedRewarded;
		this.uniqueFoodsRewarded = uniqueFoodsRewarded;
		this.caveTimeRewarded = caveTimeRewarded;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(hostileKills);
		writer.putNextInt(oreMined);
		writer.putNextInt(uniqueFoods);
		writer.putNextLong(caveTime);
		writer.putNextLong(caveTarget);
		writer.putNextBoolean(hostileKillsRewarded);
		writer.putNextBoolean(oreMinedRewarded);
		writer.putNextBoolean(uniqueFoodsRewarded);
		writer.putNextBoolean(caveTimeRewarded);
	}

	public PacketEarlyProgressionSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		hostileKills = reader.getNextInt();
		oreMined = reader.getNextInt();
		uniqueFoods = reader.getNextInt();
		caveTime = reader.getNextLong();
		caveTarget = reader.getNextLong();
		hostileKillsRewarded = reader.getNextBoolean();
		oreMinedRewarded = reader.getNextBoolean();
		uniqueFoodsRewarded = reader.getNextBoolean();
		caveTimeRewarded = reader.getNextBoolean();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		EarlyHealthProgressionSystem.applyClientSync(hostileKills, oreMined, uniqueFoods, caveTime, caveTarget, hostileKillsRewarded, oreMinedRewarded, uniqueFoodsRewarded, caveTimeRewarded);
	}
}
