package opusliews.forms;

import necesse.engine.localization.message.LocalMessage;
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
		configForm = addComponent(new Form("charcoalProductionSettings", 400, 170));
		FormFlow flow = new FormFlow(8);

		configForm.addComponent(new FormLocalLabel(
				"ui", "charcoalproductionsettings", new FontOptions(20), 0,
				configForm.getWidth() / 2, flow.next(32)));

		int rowY = flow.next(30);
		configForm.addComponent(new FormLocalLabel(
				"ui", "charcoalproduceuntil", new FontOptions(16), -1, 12, rowY + 5));

		FormContentIconButton helpButton = configForm.addComponent(new FormContentIconButton(
				234, rowY, FormInputSize.SIZE_24, ButtonColor.BASE,
				getInterfaceStyle().button_help_20,
				new LocalMessage("ui", "charcoalproductionzerotip")));

		targetInput = configForm.addComponent(new FormTextInput(264, rowY, FormInputSize.SIZE_24, 124, 9));
		targetInput.rightClickToClear = true;
		targetInput.setRegexMatchFull("[0-9]*");
		targetInput.onSubmit(e -> submitTarget());

		flow.next(8);
		repeatForeverCheckbox = configForm.addComponent(new FormLocalCheckBox(
				"ui", "charcoalrepeatforever", 12, flow.next(28), false, configForm.getWidth() - 24));
		repeatForeverCheckbox.onClicked(e -> {
			boolean repeatForever = ((FormCheckBox)e.from).checked;
			updateInputEnabled(repeatForever);
			sendSettings(getInputTarget(), repeatForever);
		});

		flow.next(8);
		FormLocalTextButton backButton = configForm.addComponent(new FormLocalTextButton(
				"ui", "backbutton", 40, flow.next(28), configForm.getWidth() - 80,
				FormInputSize.SIZE_24, ButtonColor.BASE));
		backButton.onClicked(e -> {
			submitTarget();
			backPressed.run();
		});

		configForm.setHeight(flow.next(8));
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
	public void dispose() {
		if (CharcoalProductionClientSettings.openForm == this) {
			CharcoalProductionClientSettings.openForm = null;
		}
		super.dispose();
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
