package opusliews.network;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import opusliews.multilevelsettlement.SettlementLadderAssignUI;

public class PacketSettlementLadderSync extends Packet {
	private final int settlementUniqueID;
	private final int levelIdentifierHashCode;
	private final ArrayList<Point> points;

	public PacketSettlementLadderSync(int settlementUniqueID, int levelIdentifierHashCode, List<Point> points) {
		this.settlementUniqueID = settlementUniqueID;
		this.levelIdentifierHashCode = levelIdentifierHashCode;
		this.points = new ArrayList<>(points);
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextInt(levelIdentifierHashCode);
		writer.putNextShortUnsigned(this.points.size());
		for (Point point : this.points) {
			writer.putNextInt(point.x);
			writer.putNextInt(point.y);
		}
	}

	public PacketSettlementLadderSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		levelIdentifierHashCode = reader.getNextInt();
		points = new ArrayList<>();
		int count = reader.getNextShortUnsigned();
		for (int i = 0; i < count; i++) points.add(new Point(reader.getNextInt(), reader.getNextInt()));
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		SettlementLadderAssignUI.applySync(settlementUniqueID, levelIdentifierHashCode, points);
		if (client.getContainer() instanceof necesse.inventory.container.settlement.SettlementContainer) {
			necesse.inventory.container.settlement.SettlementContainer container = (necesse.inventory.container.settlement.SettlementContainer)client.getContainer();
			if (container.getSettlementUniqueID() == settlementUniqueID) SettlementLadderAssignUI.refreshCurrentForm(client);
		}
	}
}
