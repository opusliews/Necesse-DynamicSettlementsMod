package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.customAction.EmptyCustomAction;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.inventory.container.settlement.events.SettlementDataEvent;
import necesse.inventory.container.settlement.events.SettlementOpenWorkstationEvent;
import necesse.inventory.container.settlement.events.SettlementRemovedEvent;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.SettlementWorkstation;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelStorageManager;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

/** Replaces object-container workstation configuration with the level-aware cave workstation manager. */
@ModMethodPatch(target = SettlementContainerObjectStatusManager.class, name = "<init>", arguments = {SettlementDependantContainer.class, necesse.level.maps.Level.class, int.class, int.class, necesse.engine.network.PacketReader.class})
public class SettlementContainerObjectWorkstationActionMultiLevelPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementContainerObjectStatusManager manager,
			@Advice.Argument(0) SettlementDependantContainer container,
			@Advice.Argument(1) necesse.level.maps.Level level) {
		if (manager == null || container == null || level == null || container.getSettlementUniqueID() == 0 || !manager.canSettlementWorkstationConfigure) return;
		if (SettlementMultiLevelSystem.getSurfaceIdentifier(level.getIdentifier()) == null
				|| level.getIdentifier().equals(SettlementMultiLevelSystem.getSurfaceIdentifier(level.getIdentifier()))) return;

		manager.openWorkstationConfig = (EmptyCustomAction)container.registerAction(
				new MultiLevelWorkstationConfigAction(manager, container, level));
	}
}
