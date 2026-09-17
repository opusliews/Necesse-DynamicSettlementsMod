package opusliews.forms;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.FormSwitcher;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.localComponents.FormLocalCheckBox;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import opusliews.charcoal.CharcoalProductionClientSettings;
import opusliews.network.PacketCharcoalProductionSettingsRequest;
import opusliews.network.PacketCharcoalProductionSettingsUpdate;

public class CharcoalProductionSettingsForm extends FormSwitcher {
	private final SettlementAssignWorkForm assignWork;
	private final Form configForm;
	private final FormTextInput targetInput;
	private final FormCheckBox repeatForeverCheckbox;

	public CharcoalProductionSettingsForm(SettlementAssignWorkForm assignWork, Runnable backPressed) {
		this.assignWork = assignWork;
		configForm = addComponent(new Form("charcoalProductionSettings", 500, 190));
		FormFlow flow = new FormFlow(10);

		configForm.addComponent(new FormLocalLabel(
				"ui", "charcoalproductionsettings", new FontOptions(20), 0,
				configForm.getWidth() / 2, flow.next(36)));

		int rowY = flow.next(32);
		configForm.addComponent(new FormLocalLabel(
				"ui", "charcoalproduceuntil", new FontOptions(16), -1, 18, rowY + 5));

		FormContentIconButton helpButton = configForm.addComponent(new FormContentIconButton(
				286, rowY, FormInputSize.SIZE_24, ButtonColor.BASE,
				getInterfaceStyle().button_help_20,
				new LocalMessage("ui", "charcoalproductionzerotip")));

		targetInput = configForm.addComponent(new FormTextInput(320, rowY, FormInputSize.SIZE_24, 160, 9));
		targetInput.rightClickToClear = true;
		targetInput.setRegexMatchFull("[0-9]*");
		targetInput.onSubmit(e -> submitTarget());

		flow.next(10);
		repeatForeverCheckbox = configForm.addComponent(new FormLocalCheckBox(
				"ui", "charcoalrepeatforever", 18, flow.next(30), false, configForm.getWidth() - 36));
		repeatForeverCheckbox.onClicked(e -> {
			boolean repeatForever = ((FormCheckBox)e.from).checked;
			updateInputEnabled(repeatForever);
			sendSettings(getInputTarget(), repeatForever);
		});

		flow.next(10);
		FormLocalTextButton backButton = configForm.addComponent(new FormLocalTextButton(
				"ui", "backbutton", 50, flow.next(30), configForm.getWidth() - 100,
				FormInputSize.SIZE_24, ButtonColor.BASE));
		backButton.onClicked(e -> {
			submitTarget();
			backPressed.run();
		});

		configForm.setHeight(Math.max(190, flow.next(10)));
		centerForm();
		makeCurrent(configForm);
		CharcoalProductionClientSettings.openForm = this;
		applySettings(CharcoalProductionClientSettings.produceUntilUnitsStocked, CharcoalProductionClientSettings.repeatForever);
		assignWork.client.network.sendPacket(new PacketCharcoalProductionSettingsRequest(assignWork.container.getSettlementUniqueID()));
	}

	public void applySettings(int target, boolean repeatForever) {
		if (!targetInput.isTyping()) {
			targetInput.setText(Integer.toString(Math.max(0, target)), false);
		}
		repeatForeverCheckbox.checked = repeatForever;
		updateInputEnabled(repeatForever);
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		centerForm();
	}

	@Override
	public void dispose() {
		if (CharcoalProductionClientSettings.openForm == this) {
			CharcoalProductionClientSettings.openForm = null;
		}
		super.dispose();
	}

	private void centerForm() {
		GameWindow window = WindowManager.getWindow();
		configForm.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
	}

	private void submitTarget() {
		if (repeatForeverCheckbox.checked) {
			return;
		}
		int target = getInputTarget();
		targetInput.setText(Integer.toString(target), false);
		sendSettings(target, false);
	}

	private int getInputTarget() {
		try {
			String text = targetInput.getText();
			return text.isEmpty() ? 0 : Math.max(0, Integer.parseInt(text));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private void updateInputEnabled(boolean repeatForever) {
		targetInput.setActive(!repeatForever);
	}

	private void sendSettings(int target, boolean repeatForever) {
		CharcoalProductionClientSettings.apply(target, repeatForever);
		assignWork.client.network.sendPacket(new PacketCharcoalProductionSettingsUpdate(
				assignWork.container.getSettlementUniqueID(), target, repeatForever));
	}
}
