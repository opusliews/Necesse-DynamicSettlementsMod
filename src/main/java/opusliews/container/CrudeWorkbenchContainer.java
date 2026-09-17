package opusliews.container;

import necesse.engine.network.NetworkClient;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.object.CraftingStationContainer;
import necesse.level.gameObject.TreeStumpObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import opusliews.earlygame.CrudeWorkbenchFeature;

public class CrudeWorkbenchContainer extends CraftingStationContainer {
	public CrudeWorkbenchContainer(NetworkClient client, int uniqueSeed, SettlementDataEvent settlement, LevelObject stump, PacketReader reader) {
		super(client, uniqueSeed, settlement, makeVirtualStation(stump), reader);
	}

	private static LevelObject makeVirtualStation(LevelObject stump) {
		return LevelObject.custom(
				stump.level,
				stump.layerID,
				stump.tileX,
				stump.tileY,
				CrudeWorkbenchFeature.proxyObject,
				(byte)0,
				false
		);
	}

	@Override
	public boolean isValid(ServerClient client) {
		Level level = client.getLevel();
		if (level == null || !level.isTileWithinBounds(objectX, objectY)) {
			return false;
		}

		return level.getObject(objectX, objectY) instanceof TreeStumpObject
				&& level.getObject(objectX, objectY).isInInteractRange(level, objectX, objectY, client.playerMob);
	}
}
