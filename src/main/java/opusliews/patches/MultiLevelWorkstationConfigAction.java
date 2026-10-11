package opusliews.patches;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementOpenWorkstationEvent;
import necesse.inventory.container.settlement.events.SettlementRemovedEvent;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Public action referenced from vanilla-transformed constructor advice. */
public final class MultiLevelWorkstationConfigAction extends EmptyCustomAction {
    private final SettlementContainerObjectStatusManager manager;
    private final SettlementDependantContainer container;
    private final Level level;

    public MultiLevelWorkstationConfigAction(SettlementContainerObjectStatusManager manager, SettlementDependantContainer container, Level level) {
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

				SettlementWorkstation workstation = SettlementLevelStorageManager.assignWorkstation(settlement, level, manager.masterTileX, manager.masterTileY);
				if (workstation != null) {
					new SettlementOpenWorkstationEvent(workstation).applyAndSendToClient(serverClient);
					if (Logging.logEnabled) Logging.logMessage("[MultiLevelWorkstation] Opened object workstation config settlement="
							+ settlement.uniqueID + " level=" + level.getIdentifier() + " tile=" + manager.masterTileX + "," + manager.masterTileY);
				}
    }
}
