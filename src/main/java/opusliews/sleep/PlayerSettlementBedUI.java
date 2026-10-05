package opusliews.sleep;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.network.client.Client;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.SleepContainerForm;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.container.BedContainer;
import opusliews.logging.Logging;
import opusliews.network.PacketPlayerSettlementBedAction;

public final class PlayerSettlementBedUI {
	private static final Map<SleepContainerForm, FormState> states = Collections.synchronizedMap(new WeakHashMap<>());

	private PlayerSettlementBedUI() {
	}


	public static void onFormDraw(SleepContainerForm form) {
		if (form == null) return;
		if (!(form.getContainer() instanceof BedContainer)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedUI] Sleep form has unexpected container type=" + (form.getContainer() == null ? "null" : form.getContainer().getClass().getName()));
			return;
		}

		if (!states.containsKey(form)) {
			onFormCreated(form, form.getClient(), (BedContainer)form.getContainer());
		}
		else {
			position(form);
		}
	}

	public static void onFormCreated(SleepContainerForm form, Client client, BedContainer container) {
		if (form == null || client == null || container == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedUI] Cannot add sleep button because form/client/container is null");
			return;
		}
		if (states.containsKey(form)) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedUI] Ignored duplicate sleep form setup");
			return;
		}

		Form buttonForm = form.components.addComponent(new Form(300, 32));
		FormLocalTextButton button = buttonForm.addComponent(new FormLocalTextButton(
				new LocalMessage("ui", "setplayersettlementbed"),
				0,
				0,
				buttonForm.getWidth(),
				FormInputSize.SIZE_32,
				ButtonColor.BASE));
		button.setActive(false);
		button.onClicked(event -> {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedUI] Player clicked settlement bed button tile=" + container.tileX + "," + container.tileY);
			client.network.sendPacket(new PacketPlayerSettlementBedAction(true));
			button.startCooldown(250);
		});

		states.put(form, new FormState(container, buttonForm, button));
		position(form);
		client.network.sendPacket(new PacketPlayerSettlementBedAction(false));
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedUI] Added settlement bed button tile=" + container.tileX + "," + container.tileY);
	}

	public static void applyServerState(int settlementUniqueID, LevelIdentifier contextLevel, int tileX, int tileY, boolean currentBedAssignedToPlayer) {
		synchronized (states) {
			for (Map.Entry<SleepContainerForm, FormState> entry : states.entrySet()) {
				FormState state = entry.getValue();
				if (state == null || state.container == null) continue;

				if (settlementUniqueID == 0 && contextLevel == null) {
					state.setState(false, false);
					continue;
				}

				LevelIdentifier formLevel = state.container.objectEntity == null || state.container.objectEntity.getLevel() == null
						? null : state.container.objectEntity.getLevel().getIdentifier();
				if (formLevel == null || contextLevel == null || !formLevel.equals(contextLevel)
						|| state.container.tileX != tileX || state.container.tileY != tileY) continue;

				state.setState(true, currentBedAssignedToPlayer);
				if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedUI] Applied sleep button state settlement=" + settlementUniqueID + " bed=" + contextLevel + "@" + tileX + "," + tileY + " own=" + currentBedAssignedToPlayer);
			}
		}
	}

	private static void position(SleepContainerForm form) {
		if (WindowManager.getWindow() == null) return;
		FormState state = states.get(form);
		if (state == null) return;
		int centerX = WindowManager.getWindow().getHudWidth() / 2;
		int centerY = WindowManager.getWindow().getHudHeight() / 2 + 50 + form.wakeUpButtonForm.getHeight() + form.getInterfaceStyle().formSpacing;
		int playerBedY = centerY + form.spawnButtonForm.getHeight() + form.getInterfaceStyle().formSpacing;
		state.buttonForm.setPosMiddle(centerX, playerBedY);
	}

	private static final class FormState {
		private final BedContainer container;
		private final Form buttonForm;
		private final FormLocalTextButton button;

		private FormState(BedContainer container, Form buttonForm, FormLocalTextButton button) {
			this.container = container;
			this.buttonForm = buttonForm;
			this.button = button;
		}

		private void setState(boolean available, boolean currentBedAssigned) {
			button.setActive(available);
			button.setLocalization(new LocalMessage("ui", currentBedAssigned ? "clearplayersettlementbed" : "setplayersettlementbed"));
		}
	}
}
