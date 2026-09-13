package opus.forms;

import necesse.engine.window.GameWindow;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.localComponents.FormLocalCheckBox;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.gameFont.FontOptions;
import opus.sleep.SettlementSleepSettings;

import java.util.function.Consumer;

public class SettlementSleepSettingsForm extends Form {
	private final FormCheckBox wakeOnRaid;
	private final FormCheckBox wakeOnBarrierAttack;
	private final FormCheckBox wakeOnBarrierBreach;
	private final Consumer<SettlementSleepSettings> onChanged;

	public SettlementSleepSettingsForm(
			SettlementSleepSettings initial,
			Consumer<SettlementSleepSettings> onChanged,
			Runnable onBack
	) {
		super("settlementSleepSettings", 400, 205);
		this.onChanged = onChanged;

		addComponent(new FormLocalLabel(
				"ui",
				"sleepwakewhen",
				new FontOptions(20),
				0,
				getWidth() / 2,
				10,
				getWidth() - 20
		));

		wakeOnRaid = (FormCheckBox)addComponent(new FormLocalCheckBox(
				"ui", "sleepwakeraid", 30, 50, true, getWidth() - 60));
		wakeOnBarrierAttack = (FormCheckBox)addComponent(new FormLocalCheckBox(
				"ui", "sleepwakebarrierattack", 30, 80, true, getWidth() - 60));
		wakeOnBarrierBreach = (FormCheckBox)addComponent(new FormLocalCheckBox(
				"ui", "sleepwakebarrierbreach", 30, 110, true, getWidth() - 60));

		wakeOnRaid.onClicked(e -> submitChange());
		wakeOnBarrierAttack.onClicked(e -> submitChange());
		wakeOnBarrierBreach.onClicked(e -> submitChange());

		((FormLocalTextButton)addComponent(new FormLocalTextButton(
				"ui", "backbutton", 40, 155, getWidth() - 80
		))).onClicked(e -> onBack.run());

		setSettings(initial);
	}

	public void setSettings(SettlementSleepSettings settings) {
		SettlementSleepSettings value = settings == null ? SettlementSleepSettings.defaults : settings;
		wakeOnRaid.checked = value.wakeOnRaid;
		wakeOnBarrierAttack.checked = value.wakeOnBarrierAttack;
		wakeOnBarrierBreach.checked = value.wakeOnBarrierBreach;
	}

	public SettlementSleepSettings getSettings() {
		return new SettlementSleepSettings(
				wakeOnRaid.checked,
				wakeOnBarrierAttack.checked,
				wakeOnBarrierBreach.checked
		);
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
	}

	private void submitChange() {
		if (onChanged != null) {
			onChanged.accept(getSettings());
		}
	}
}
