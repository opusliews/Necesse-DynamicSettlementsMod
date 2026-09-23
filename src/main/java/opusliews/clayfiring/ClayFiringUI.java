package opusliews.clayfiring;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.stream.Stream;
import necesse.engine.gameTool.GameToolManager;
import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.settlement.CreateOrExpandWorkZoneGameTool;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.ui.ButtonColor;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import opusliews.charcoal.CharcoalProductionUI;
import opusliews.forms.ClayFiringSettingsForm;

public final class ClayFiringUI {
	private ClayFiringUI() {
	}

	public static void addAssignButton(SettlementAssignWorkForm form, FormFlow flow, FormContentBox content) {
		int y = flow.next(40);
		int buttonWidth = content.getWidth() - 32 - 32 - 36;
		FormLocalTextButton assignButton = content.addComponent(new FormLocalTextButton(
				"ui", "settlementassignclayfiring", 16, y, buttonWidth));
		assignButton.onClicked(e -> startAssignTool(form));
		assignButton.setLocalTooltip(new LocalMessage("ui", "settlementclayfiringtip"));

		FormContentIconButton configButton = content.addComponent(new FormContentIconButton(
				assignButton.getX() + assignButton.getWidth(), y + 4,
				FormInputSize.SIZE_32, ButtonColor.BASE,
				form.getInterfaceStyle().config_button_32,
				new LocalMessage("ui", "clayfiringsettings")));
		configButton.onClicked(e -> openSettings(form));

		content.addComponent(form.getHideButton(
				configButton.getX() + configButton.getWidth(), y,
				ClayFiringZone.hideZones,
				new LocalMessage("ui", "hidebutton"),
				new LocalMessage("ui", "showbutton")));

		int requiredHeight = flow.next() + 8;
		if (requiredHeight > form.work.getHeight()) {
			form.work.setHeight(requiredHeight);
			content.setHeight(requiredHeight);
		}
		content.setContentBox(new Rectangle(0, 0, content.getWidth(), requiredHeight));
	}

	public static void openSettings(SettlementAssignWorkForm form) {
		ClayFiringSettingsForm settings = new ClayFiringSettingsForm(form, () -> form.makeCurrent(form.work));
		form.addComponent(settings);
		form.makeCurrent(settings);
	}

	public static void startAssignTool(SettlementAssignWorkForm form) {
		GameToolManager.clearGameTools(form);
		GameToolManager.setGameTool(new CreateOrExpandWorkZoneGameTool(form.client.getLevel()) {
			@Override
			public Stream streamEditZones() {
				return CharcoalProductionUI.getZones(form).values().stream()
						.filter(z -> ((SettlementWorkZone)z).getID() == SettlementWorkZoneRegistry.getZoneID(ClayFiringZone.stringID));
			}

			@Override
			public void onCreatedNewZone(Rectangle rectangle, Point anchor) {
				HashMap zones = CharcoalProductionUI.getZones(form);
				SettlementWorkZone zone = SettlementWorkZoneRegistry.getNewZone(SettlementWorkZoneRegistry.getZoneID(ClayFiringZone.stringID));
				zone.expandZone(level, rectangle, anchor, (x, y) -> zones.values().stream().anyMatch(z -> ((SettlementWorkZone)z).containsTile(x, y)));
				if (!zone.shouldRemove()) {
					zone.generateUniqueID(zones::containsKey);
					zones.put(zone.getUniqueID(), zone);
					form.container.createWorkZone.runAndSend(zone.getID(), zone.getUniqueID(), rectangle, anchor);
					CharcoalProductionUI.updateHud(form);
				}
			}

			@Override
			public void onRemovedZone(SettlementWorkZone zone, Rectangle rectangle) {
				HashMap zones = CharcoalProductionUI.getZones(form);
				if (zone.shrinkZone(level, rectangle)) {
					form.container.shrinkWorkZone.runAndSend(zone.getUniqueID(), rectangle);
					if (zone.shouldRemove()) zones.remove(zone.getUniqueID());
					CharcoalProductionUI.updateHud(form);
				}
			}

			@Override
			public void onExpandedZone(SettlementWorkZone zone, Rectangle rectangle, Point anchor) {
				HashMap zones = CharcoalProductionUI.getZones(form);
				boolean updated = zone.expandZone(level, rectangle, anchor, (x, y) -> zones.values().stream().anyMatch(z -> ((SettlementWorkZone)z).containsTile(x, y)));
				if (updated) {
					form.container.expandWorkZone.runAndSend(zone.getUniqueID(), rectangle, anchor);
					CharcoalProductionUI.updateHud(form);
				}
			}

			@Override
			public void isCancelled() {
				super.isCancelled();
				CharcoalProductionUI.setOverrideShowZones(form, false);
			}

			@Override
			public void isCleared() {
				super.isCleared();
				CharcoalProductionUI.setOverrideShowZones(form, false);
			}
		}, form);
		CharcoalProductionUI.setOverrideShowZones(form, true);
	}
}
