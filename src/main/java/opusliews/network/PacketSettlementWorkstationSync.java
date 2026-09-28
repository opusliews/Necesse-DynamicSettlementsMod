package opusliews.network;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.multilevelsettlement.SettlementWorkstationAssignUI;

public class PacketSettlementWorkstationSync extends Packet {
	private final int settlementUniqueID;
	private final int levelIdentifierHashCode;
	private final boolean customLevel;
	private final ArrayList<Point> points;

	public PacketSettlementWorkstationSync(int settlementUniqueID, int levelIdentifierHashCode, boolean customLevel, List<Point> points) {
		this.settlementUniqueID = settlementUniqueID;
		this.levelIdentifierHashCode = levelIdentifierHashCode;
		this.customLevel = customLevel;
		this.points = new ArrayList<>(points);
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextInt(levelIdentifierHashCode);
		writer.putNextBoolean(customLevel);
		writer.putNextShortUnsigned(this.points.size());
		for (Point point : this.points) {
			writer.putNextInt(point.x);
			writer.putNextInt(point.y);
		}
	}

	public PacketSettlementWorkstationSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		levelIdentifierHashCode = reader.getNextInt();
		customLevel = reader.getNextBoolean();
		points = new ArrayList<>();
		int count = reader.getNextShortUnsigned();
		for (int i = 0; i < count; i++) points.add(new Point(reader.getNextInt(), reader.getNextInt()));
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		SettlementWorkstationAssignUI.applySync(settlementUniqueID, levelIdentifierHashCode, customLevel, points);
		SettlementWorkstationAssignUI.refreshCurrentForm(client);
	}
}
