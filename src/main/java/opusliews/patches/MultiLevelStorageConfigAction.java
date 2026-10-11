package opusliews.patches;

import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementOpenStorageConfigEvent;
import necesse.inventory.container.settlement.events.SettlementRemovedEvent;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementInventory;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Public action referenced from vanilla-transformed constructor advice. */
public final class MultiLevelStorageConfigAction extends EmptyCustomAction {
    private final SettlementContainerObjectStatusManager manager;
    private final SettlementDependantContainer container;
    private final Level level;

    public MultiLevelStorageConfigAction(SettlementContainerObjectStatusManager manager, SettlementDependantContainer container, Level level) {
        this.manager = manager;
        this.container = container;
        this.level = level;
    }

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
}
