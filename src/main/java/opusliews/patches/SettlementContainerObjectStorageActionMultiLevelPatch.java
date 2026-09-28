package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementOpenStorageConfigEvent;
import necesse.inventory.container.settlement.events.SettlementRemovedEvent;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Replaces the chest storage-config action on linked cave levels with the level-aware manager. */
@ModMethodPatch(target = SettlementContainerObjectStatusManager.class, name = "<init>", arguments = {SettlementDependantContainer.class, necesse.level.maps.Level.class, int.class, int.class, necesse.engine.network.PacketReader.class})
public class SettlementContainerObjectStorageActionMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementContainerObjectStatusManager manager,
			@Advice.Argument(0) SettlementDependantContainer container,
			@Advice.Argument(1) necesse.level.maps.Level level) {
		if (manager == null || container == null || level == null || container.getSettlementUniqueID() == 0 || !manager.canSettlementStorageConfigure) return;
		if (SettlementMultiLevelSystem.getSurfaceIdentifier(level.getIdentifier()) == null
				|| level.getIdentifier().equals(SettlementMultiLevelSystem.getSurfaceIdentifier(level.getIdentifier()))) return;

		manager.openSettlementStorageConfig = (EmptyCustomAction)container.registerAction(new EmptyCustomAction() {
			@Override
			protected void run() {
				if (!container.client.isServer()) return;
				ServerClient serverClient = container.client.getServerClient();
				ServerSettlementData settlement = container.getServerData();
				if (serverClient == null || settlement == null) {
					if (serverClient != null) new SettlementRemovedEvent(0).applyAndSendToClient(serverClient);
					return;
				}

				SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
				if (domain == null || domain.getLevelType(level.getIdentifier()) == null) return;
				if (!settlement.networkData.doesClientHaveAccess(serverClient)) {
					new SettlementDataEvent(settlement).applyAndSendToClient(serverClient);
					return;
				}

				SettlementInventory inventory = SettlementLevelStorageManager.assignStorage(settlement, level, manager.masterTileX, manager.masterTileY);
				if (inventory != null) {
					new SettlementOpenStorageConfigEvent(inventory).applyAndSendToClient(serverClient);
					if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Opened object storage config settlement=" + settlement.uniqueID + " level=" + level.getIdentifier() + " tile=" + manager.masterTileX + "," + manager.masterTileY);
				}
			}
		});
	}
}
