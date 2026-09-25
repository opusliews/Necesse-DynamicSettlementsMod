package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketPlayerInventorySlot;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventory;
import necesse.inventory.PlayerInventorySlot;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import opusliews.object.PlacedLogRegistry;

public class PacketPlaceLog extends Packet {
	private static final double placeRange = 132.0;

	private final int tileX;
	private final int tileY;

	public PacketPlaceLog(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	public PacketPlaceLog(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	@Override
	public void processServer(
			NetworkPacket packet,
			Server server,
			ServerClient client) {
		PlayerMob player = client.playerMob;
		Level level = player.getLevel();
		PlayerInventorySlot selectedSlot = player.getSelectedItemSlot();

		try {
			if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) {
				return;
			}

			InventoryItem item = selectedSlot.getItem(player.getInv());
			if (item == null || !item.item.isGlobalIngredient("anylog")) {
				return;
			}

			int placedObjectID = PlacedLogRegistry.getObjectID(item.item.getStringID());
			if (placedObjectID < 0) {
				return;
			}

			GameObject placedObject = ObjectRegistry.getObject(placedObjectID);
			if (placedObject.canPlace(level, 0, tileX, tileY, 0, true, false) != null) {
				return;
			}

			double distance = player.getPositionPoint().distance(
					tileX * 32.0 + 16.0,
					tileY * 32.0 + 16.0
			);

			if (distance > placeRange) {
				return;
			}

			level.setObject(tileX, tileY, placedObjectID, 0);
			level.objectLayer.setIsPlayerPlaced(tileX, tileY, true);
			level.sendObjectUpdatePacket(tileX, tileY);
			level.getLevelObject(tileX, tileY).checkAround();

			PlayerInventory inventory = selectedSlot.getInv(player.getInv());
			if (inventory != null) {
				inventory.setAmount(selectedSlot.slot, item.getAmount() - 1);
			}
		} finally {
			client.sendPacket(new PacketPlayerInventorySlot(client, selectedSlot));
		}
	}
}
