package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ObjectLayerRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.axeToolItem.AxeToolItem;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import opusliews.object.PlacedPlankObject;

public class PacketCutPlacedPlank extends Packet {
	private static final double interactRange = 100.0;

	private final int tileX;
	private final int tileY;

	public PacketCutPlacedPlank(int tileX, int tileY) {
		this.tileX = tileX;
		this.tileY = tileY;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(tileX);
		writer.putNextInt(tileY);
	}

	public PacketCutPlacedPlank(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);
		tileX = reader.getNextInt();
		tileY = reader.getNextInt();
	}

	@Override
	public void processServer(NetworkPacket packet, Server server, ServerClient client) {
		if (!client.checkHasRequestedSelf() || client.isDead()) return;
		client.checkSpawned();

		PlayerMob player = client.playerMob;
		Level level = player.getLevel();
		if (level == null || !level.isTileWithinBounds(tileX, tileY) || level.isProtected(tileX, tileY)) return;

		InventoryItem selectedItem = player.getSelectedItem();
		if (selectedItem == null || !(selectedItem.item instanceof AxeToolItem)) return;

		double distance = player.getPositionPoint().distance(tileX * 32.0 + 16.0, tileY * 32.0 + 16.0);
		if (distance > interactRange) return;

		GameObject object = level.getObject(ObjectLayerRegistry.TILE_LAYER, tileX, tileY);
		if (!(object instanceof PlacedPlankObject)) return;

		((PlacedPlankObject)object).cutWithAxe(level, tileX, tileY);
	}
}
