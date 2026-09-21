package opusliews.network;

import necesse.engine.GlobalData;
import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.inventory.InventoryItem;
import opusliews.container.CrudeAnvilContainer;

public class PacketCrudeAnvilOutput extends Packet {
	private final Packet itemContent;

	public PacketCrudeAnvilOutput(InventoryItem item) {
		itemContent = InventoryItem.getContentPacket(item);
		PacketWriter writer = new PacketWriter(this);
		writer.putNextContentPacket(itemContent);
	}

	public PacketCrudeAnvilOutput(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		itemContent = reader.getNextContentPacket();
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		if (!(client.getContainer() instanceof CrudeAnvilContainer)) return;
		CrudeAnvilContainer container = (CrudeAnvilContainer)client.getContainer();
		container.outputInventory.setItem(0, InventoryItem.fromContentPacket(itemContent));
		container.getSlot(container.OUTPUT_SLOT).markDirty();
		GlobalData.updateCraftable();
	}
}
