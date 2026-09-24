package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.network.packet.PacketChangeObject;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import opusliews.object.PlacedPlankRegistry;

public class PacketPlacePlank extends Packet {
	public static final double placeRange = 132.0;

	private final int tileX;
	private final int tileY;

	public PacketPlacePlank(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	public PacketPlacePlank(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;
		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		PlayerMob player = client.playerMob;
		Level level = player.getLevel();

		if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) return;

		InventoryItem item = player.getSelectedItem();
		if (item == null || !item.item.isGlobalIngredient("dsanyplank")) return;

		int placedObjectID = PlacedPlankRegistry.getObjectID(item.item.getStringID());
		if (placedObjectID < 0) return;

		int layerID = ObjectLayerRegistry.TILE_LAYER;
		GameObject placedObject = ObjectRegistry.getObject(placedObjectID);
		if (placedObject.canPlace(level, layerID, tileX, tileY, 0, true, false) != null) return;

		double distance = player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0);
		if (distance > placeRange) return;

		level.objectLayer.setObject(layerID, tileX, tileY, placedObjectID);
		level.objectLayer.setObjectRotation(layerID, tileX, tileY, 0);
		level.objectLayer.setIsPlayerPlaced(layerID, tileX, tileY, true);
		level.getServer().network.sendToClientsWithTile(
				new PacketChangeObject(level, layerID, tileX, tileY, placedObjectID, 0, true),
				level,
				tileX,
				tileY
		);
		level.getLevelObject(layerID, tileX, tileY).checkAround();

		item.setAmount(item.getAmount() - 1);
	}
}
