package opusliews.forms;

import java.util.LinkedHashMap;
import java.util.Map;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.FormSwitcher;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormItemIcon;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.localComponents.FormLocalCheckBox;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import opusliews.clayfiring.ClayFiringCatalog;
import opusliews.clayfiring.ClayFiringClientSettings;
import opusliews.network.PacketClayFiringSettingsRequest;
import opusliews.network.PacketClayFiringSettingsUpdate;

public class ClayFiringSettingsForm extends FormSwitcher {
	private final SettlementAssignWorkForm assignWork;
	private final Form configForm;
	private final LinkedHashMap<String, FormTextInput> targetInputs = new LinkedHashMap<>();
	private final FormCheckBox repeatForeverCheckbox;

	public ClayFiringSettingsForm(SettlementAssignWorkForm assignWork, Runnable backPressed) {
		this.assignWork = assignWork;
		configForm = addComponent(new Form("clayFiringSettings", 580, 200));
		FormFlow flow = new FormFlow(10);

		configForm.addComponent(new FormLocalLabel(
				"ui", "clayfiringsettings", new FontOptions(20), 0,
				configForm.getWidth() / 2, flow.next(36)));

		for (ClayFiringCatalog.Entry entry : ClayFiringCatalog.getEntries()) {
			Item firedItem = ItemRegistry.getItem(entry.firedItemStringID);
			if (firedItem == null) continue;

			int rowY = flow.next(36);
			InventoryItem iconItem = new InventoryItem(firedItem);
			configForm.addComponent(new FormItemIcon(18, rowY - 4, iconItem, true));
			configForm.addComponent(new FormLocalLabel(
					new LocalMessage("ui", "clayproduceuntil", "item", firedItem.getLocalization(iconItem)),
					new FontOptions(16), -1, 56, rowY + 5, 330));

			FormTextInput input = configForm.addComponent(new FormTextInput(390, rowY, FormInputSize.SIZE_24, 160, 9));
			input.rightClickToClear = true;
			input.setRegexMatchFull("[0-9]*");
			input.onSubmit(e -> submitTargets());
			targetInputs.put(entry.firedItemStringID, input);
		}

		int helpY = flow.next(28);
		configForm.addComponent(new FormContentIconButton(
				526, helpY, FormInputSize.SIZE_24, ButtonColor.BASE,
				getInterfaceStyle().button_help_20,
				new LocalMessage("ui", "clayfiringzerotip")));

		flow.next(4);
		repeatForeverCheckbox = configForm.addComponent(new FormLocalCheckBox(
				"ui", "clayrepeatforever", 18, flow.next(30), false, configForm.getWidth() - 36));
		repeatForeverCheckbox.onClicked(e -> {
			boolean repeatForever = ((FormCheckBox)e.from).checked;
			updateInputsEnabled(repeatForever);
			sendSettings(getInputTargets(), repeatForever);
		});

		flow.next(10);
		FormLocalTextButton backButton = configForm.addComponent(new FormLocalTextButton(
				"ui", "backbutton", 50, flow.next(30), configForm.getWidth() - 100,
				FormInputSize.SIZE_24, ButtonColor.BASE));
		backButton.onClicked(e -> {
			submitTargets();
			backPressed.run();
		});

		configForm.setHeight(Math.max(200, flow.next(10)));
		centerForm();
		makeCurrent(configForm);
		ClayFiringClientSettings.openForm = this;
		applySettings(ClayFiringClientSettings.targets, ClayFiringClientSettings.repeatForever);
		assignWork.client.network.sendPacket(new PacketClayFiringSettingsRequest(assignWork.container.getSettlementUniqueID()));
	}

	public void applySettings(Map<String, Integer> targets, boolean repeatForever) {
		for (Map.Entry<String, FormTextInput> entry : targetInputs.entrySet()) {
			FormTextInput input = entry.getValue();
			if (!input.isTyping()) input.setText(Integer.toString(Math.max(0, targets.getOrDefault(entry.getKey(), 0))), false);
		}
		repeatForeverCheckbox.checked = repeatForever;
		updateInputsEnabled(repeatForever);
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		centerForm();
	}

	@Override
	public void dispose() {
		if (ClayFiringClientSettings.openForm == this) ClayFiringClientSettings.openForm = null;
		super.dispose();
	}

	private void centerForm() {
		GameWindow window = WindowManager.getWindow();
		configForm.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
	}

	private void submitTargets() {
		if (repeatForeverCheckbox.checked) return;
		LinkedHashMap<String, Integer> targets = getInputTargets();
		for (Map.Entry<String, FormTextInput> entry : targetInputs.entrySet()) {
			entry.getValue().setText(Integer.toString(targets.getOrDefault(entry.getKey(), 0)), false);
		}
		sendSettings(targets, false);
	}

	private LinkedHashMap<String, Integer> getInputTargets() {
		LinkedHashMap<String, Integer> targets = new LinkedHashMap<>();
		for (Map.Entry<String, FormTextInput> entry : targetInputs.entrySet()) {
			int value;
			try {
				String text = entry.getValue().getText();
				value = text.isEmpty() ? 0 : Math.max(0, Integer.parseInt(text));
			} catch (NumberFormatException ignored) {
				value = 0;
			}
			targets.put(entry.getKey(), value);
		}
		return targets;
	}

	private void updateInputsEnabled(boolean repeatForever) {
		for (FormTextInput input : targetInputs.values()) input.setActive(!repeatForever);
	}

	private void sendSettings(Map<String, Integer> targets, boolean repeatForever) {
		ClayFiringClientSettings.apply(targets, repeatForever);
		assignWork.client.network.sendPacket(new PacketClayFiringSettingsUpdate(
				assignWork.container.getSettlementUniqueID(), targets, repeatForever));
	}
}
