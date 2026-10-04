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
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import opusliews.logging.Logging;
import opusliews.network.PacketSettlementChestProtectionRequest;
import opusliews.network.PacketSettlementChestProtectionSync;
import opusliews.network.PacketSettlementChestProtectionUpdate;
import opusliews.settlement.SettlementChestProtectionSystem;

public class SettlementChestProtectionForm extends FormSwitcher {
	private final SettlementAssignWorkForm assignWork;
	private final Form configForm;
	private final FormCheckBox protectCheckbox;

	public SettlementChestProtectionForm(SettlementAssignWorkForm assignWork, Runnable backPressed) {
		this.assignWork = assignWork;
		configForm = addComponent(new Form("settlementChestProtection", 500, 150));
		FormFlow flow = new FormFlow(12);

		int rowY = flow.next(34);
		configForm.addComponent(new FormLocalLabel(
				"ui", "protectsettlementchests", new FontOptions(16), -1,
				80, rowY + 5));

		protectCheckbox = configForm.addComponent(new FormCheckBox("", 302, rowY + 8, false));
		protectCheckbox.onClicked(e -> {
			boolean value = ((FormCheckBox)e.from).checked;
			int settlementUniqueID = assignWork.container.getSettlementUniqueID();
			SettlementChestProtectionSystem.setClientProtected(settlementUniqueID, value);
			assignWork.client.network.sendPacket(new PacketSettlementChestProtectionUpdate(settlementUniqueID, value));
			if (Logging.logEnabled) Logging.logMessage("[ChestProtection] UI changed settlement=" + settlementUniqueID + " enabled=" + value);
		});

		configForm.addComponent(new FormContentIconButton(
				360, rowY, FormInputSize.SIZE_24, ButtonColor.BASE,
				getInterfaceStyle().button_help_20,
				new LocalMessage("ui", "protectsettlementcheststip")));

		flow.next(26);
		FormLocalTextButton backButton = configForm.addComponent(new FormLocalTextButton(
				"ui", "backbutton", 50, flow.next(30), configForm.getWidth() - 100,
				FormInputSize.SIZE_24, ButtonColor.BASE));
		backButton.onClicked(e -> backPressed.run());

		configForm.setHeight(Math.max(150, flow.next(10)));
		centerForm();
		makeCurrent(configForm);
		PacketSettlementChestProtectionSync.SettlementChestProtectionFormState.openForm = this;

		int settlementUniqueID = assignWork.container.getSettlementUniqueID();
		applySetting(SettlementChestProtectionSystem.getClientProtected(settlementUniqueID));
		assignWork.client.network.sendPacket(new PacketSettlementChestProtectionRequest(settlementUniqueID));
		if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Opened settings settlement=" + settlementUniqueID);
	}

	public int getSettlementUniqueID() {
		return assignWork.container.getSettlementUniqueID();
	}

	public void applySetting(boolean value) {
		protectCheckbox.checked = value;
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		centerForm();
	}

	@Override
	public void dispose() {
		if (PacketSettlementChestProtectionSync.SettlementChestProtectionFormState.openForm == this) {
			PacketSettlementChestProtectionSync.SettlementChestProtectionFormState.openForm = null;
		}
		super.dispose();
	}

	private void centerForm() {
		GameWindow window = WindowManager.getWindow();
		configForm.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
	}
}
