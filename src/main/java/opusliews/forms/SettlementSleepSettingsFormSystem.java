package opusliews.forms;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.ContainerComponent;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementSettingsForm;
import opusliews.network.PacketSettlementSleepSettingsRequest;
import opusliews.network.PacketSettlementSleepSettingsUpdate;
import opusliews.sleep.SettlementSleepSettings;

public final class SettlementSleepSettingsFormSystem {
	private static final Map<SettlementSettingsForm, State> states =
			Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Integer, SettlementSleepSettings> clientSettings = new HashMap<>();

	private SettlementSleepSettingsFormSystem() {
	}

	public static void onSettingsUpdated(SettlementSettingsForm form, Form settingsForm) {
		if (form == null || settingsForm == null || form.container == null || form.container.settlementData == null) {
			return;
		}

		State state;
		synchronized (states) {
			state = states.get(form);
			if (state == null) {
				int settlementUniqueID = form.container.getSettlementUniqueID();
				SettlementSleepSettingsForm sleepForm = new SettlementSleepSettingsForm(
						getCachedSettings(settlementUniqueID),
						value -> sendUpdate(form, value),
						() -> form.makeCurrent(settingsForm)
				);
				form.addComponent(sleepForm);
				sleepForm.onWindowResized(WindowManager.getWindow());
				state = new State(sleepForm);
				states.put(form, state);
			}
		}

		boolean isOwner = form.container.isSettlementOwner(form.client);
		int buttonY = settingsForm.getHeight() + 5;
		FormLocalTextButton button = (FormLocalTextButton)settingsForm.addComponent(
				new FormLocalTextButton("ui", "sleepsettingsbutton", 40, buttonY, settingsForm.getWidth() - 80)
		);
		button.setActive(isOwner);
		if (!isOwner) {
			button.setLocalTooltip(new LocalMessage(
					"ui",
					form.container.hasSettlementOwner() ? "settlementowneronly" : "settlementclaimfirst"
			));
		}
		button.onClicked(e -> open(form));

		settingsForm.setHeight(buttonY + 45);
		ContainerComponent.setPosInventory(settingsForm);

		if (!isOwner && form.isCurrent(state.sleepForm)) {
			form.makeCurrent(settingsForm);
		}
	}

	public static void onDisposed(SettlementSettingsForm form) {
		if (form == null) {
			return;
		}

		synchronized (states) {
			states.remove(form);
		}

		if (form.container != null) {
			int settlementUniqueID = form.container.getSettlementUniqueID();
			synchronized (clientSettings) {
				clientSettings.remove(settlementUniqueID);
			}
		}
	}

	public static void applyServerSettings(int settlementUniqueID, SettlementSleepSettings settings) {
		if (settlementUniqueID == 0 || settings == null) {
			return;
		}

		synchronized (clientSettings) {
			clientSettings.put(settlementUniqueID, settings);
		}

		synchronized (states) {
			for (Map.Entry<SettlementSettingsForm, State> entry : states.entrySet()) {
				SettlementSettingsForm form = entry.getKey();
				if (form != null && form.container != null
						&& form.container.getSettlementUniqueID() == settlementUniqueID) {
					entry.getValue().sleepForm.setSettings(settings);
				}
			}
		}
	}

	private static void open(SettlementSettingsForm form) {
		State state;
		synchronized (states) {
			state = states.get(form);
		}
		if (state == null || !form.container.isSettlementOwner(form.client)) {
			return;
		}

		int settlementUniqueID = form.container.getSettlementUniqueID();
		state.sleepForm.setSettings(getCachedSettings(settlementUniqueID));
		form.makeCurrent(state.sleepForm);
		form.client.network.sendPacket(new PacketSettlementSleepSettingsRequest(settlementUniqueID));
	}

	private static void sendUpdate(SettlementSettingsForm form, SettlementSleepSettings settings) {
		if (form == null || form.container == null || !form.container.isSettlementOwner(form.client)) {
			return;
		}

		int settlementUniqueID = form.container.getSettlementUniqueID();
		synchronized (clientSettings) {
			clientSettings.put(settlementUniqueID, settings);
		}
		form.client.network.sendPacket(new PacketSettlementSleepSettingsUpdate(settlementUniqueID, settings));
	}

	private static SettlementSleepSettings getCachedSettings(int settlementUniqueID) {
		synchronized (clientSettings) {
			return clientSettings.getOrDefault(settlementUniqueID, SettlementSleepSettings.defaults);
		}
	}

	private static final class State {
		private final SettlementSleepSettingsForm sleepForm;

		private State(SettlementSleepSettingsForm sleepForm) {
			this.sleepForm = sleepForm;
		}
	}
}
