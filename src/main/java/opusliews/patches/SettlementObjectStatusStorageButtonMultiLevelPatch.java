package opusliews.patches;

import java.lang.reflect.Field;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.client.Client;
import necesse.engine.util.LevelIdentifier;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormButton;
import necesse.gfx.forms.events.FormEventsHandler;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementObjectStatusFormManager;
import necesse.inventory.container.settlement.SettlementContainerObjectStatusManager;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.network.PacketOpenSettlementStorage;

/** Uses the level-aware storage packet for the storage button on linked cave inventory forms. */
@ModMethodPatch(target = SettlementObjectStatusFormManager.class, name = "addStorageConfigButton", arguments = {Form.class, int.class, int.class})
public class SettlementObjectStatusStorageButtonMultiLevelPatch {
	private static final Field clickedEventsField;

	static {
		try {
			clickedEventsField = FormButton.class.getDeclaredField("clickedEvents");
			clickedEventsField.setAccessible(true);
		}
		catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This SettlementObjectStatusFormManager formManager,
			@Advice.Return boolean added,
			@Advice.FieldValue("container") SettlementDependantContainer container,
			@Advice.FieldValue("manager") SettlementContainerObjectStatusManager manager,
			@Advice.FieldValue("client") Client client) {
		replaceLinkedLevelStorageButton(formManager, added, container, manager, client);
	}

	// Advice is inlined into SettlementObjectStatusFormManager. Keep all reflective state
	// access inside this public helper so the transformed vanilla class never directly
	// touches private fields on the patch class.
	public static void replaceLinkedLevelStorageButton(
			SettlementObjectStatusFormManager formManager,
			boolean added,
			SettlementDependantContainer container,
			SettlementContainerObjectStatusManager manager,
			Client client) {
		if (!added || formManager == null || container == null || manager == null || client == null || client.getLevel() == null) return;
		if (formManager.configureStorageButton == null || container.getSettlementUniqueID() == 0) return;
		if (client.getLevel().getIdentifier().equals(LevelIdentifier.SURFACE_IDENTIFIER)) return;

		try {
			FormEventsHandler handlers = (FormEventsHandler)clickedEventsField.get(formManager.configureStorageButton);
			handlers.clearListeners();
			formManager.configureStorageButton.onClicked(event -> {
				formManager.openStorageConfig = true;
				client.network.sendPacket(new PacketOpenSettlementStorage(
						client.getLevel().getIdentifierHashCode(),
						manager.masterTileX,
						manager.masterTileY,
						container.getSettlementUniqueID()
				));
				event.preventDefault();
			});
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Replaced linked-level inventory storage button level=" + client.getLevel().getIdentifier() + " tile=" + manager.masterTileX + "," + manager.masterTileY);
		}
		catch (IllegalAccessException e) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Failed to replace linked-level inventory storage button error=" + e.getMessage());
		}
	}
}
