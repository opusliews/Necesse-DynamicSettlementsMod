package opusliews.charcoal;

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
import opusliews.forms.CharcoalProductionSettingsForm;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.stream.Stream;

public final class CharcoalProductionUI {
	private static final Field zonesField;
	private static final Field overrideShowZonesField;
	private static final Method updateHudElementsMethod;

	static {
		try {
			zonesField = SettlementAssignWorkForm.class.getDeclaredField("settlementWorkZones");
			zonesField.setAccessible(true);
			overrideShowZonesField = SettlementAssignWorkForm.class.getDeclaredField("overrideShowZones");
			overrideShowZonesField.setAccessible(true);
			updateHudElementsMethod = SettlementAssignWorkForm.class.getDeclaredMethod("updateHudElements");
			updateHudElementsMethod.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private CharcoalProductionUI() {
	}

	public static void addAssignButton(SettlementAssignWorkForm form, FormFlow flow, FormContentBox content) {
		int y = flow.next(40);
		int buttonWidth = content.getWidth() - 32 - 32 - 36;
		FormLocalTextButton assignButton = content.addComponent(new FormLocalTextButton(
				"ui", "settlementassigncharcoalproduction", 16, y, buttonWidth));
		assignButton.onClicked(e -> startAssignTool(form));
		assignButton.setLocalTooltip(new LocalMessage("ui", "settlementcharcoalproductiontip"));

		FormContentIconButton configButton = content.addComponent(new FormContentIconButton(
				assignButton.getX() + assignButton.getWidth(), y + 4,
				FormInputSize.SIZE_32, ButtonColor.BASE,
				form.getInterfaceStyle().config_button_32,
				new LocalMessage("ui", "charcoalproductionsettings")));
		configButton.onClicked(e -> openSettings(form));

		content.addComponent(form.getHideButton(
				configButton.getX() + configButton.getWidth(), y,
				CharcoalProductionZone.hideZones,
				new LocalMessage("ui", "hidebutton"),
				new LocalMessage("ui", "showbutton")));
	}

	public static void openSettings(SettlementAssignWorkForm form) {
		CharcoalProductionSettingsForm settings = new CharcoalProductionSettingsForm(form, () -> form.makeCurrent(form.work));
		form.addComponent(settings);
		form.makeCurrent(settings);
	}

	public static void startAssignTool(SettlementAssignWorkForm form) {
		GameToolManager.clearGameTools(form);
		GameToolManager.setGameTool(new CreateOrExpandWorkZoneGameTool(form.client.getLevel()) {
			@Override
			public Stream streamEditZones() {
				return getZones(form).values().stream().filter(z -> ((SettlementWorkZone)z).getID() == SettlementWorkZoneRegistry.getZoneID(CharcoalProductionZone.stringID));
			}

			@Override
			public void onCreatedNewZone(Rectangle rectangle, Point anchor) {
				HashMap zones = getZones(form);
				SettlementWorkZone zone = SettlementWorkZoneRegistry.getNewZone(SettlementWorkZoneRegistry.getZoneID(CharcoalProductionZone.stringID));
				zone.expandZone(level, rectangle, anchor, (x, y) -> zones.values().stream().anyMatch(z -> ((SettlementWorkZone)z).containsTile(x, y)));
				if (!zone.shouldRemove()) {
					zone.generateUniqueID(zones::containsKey);
					zones.put(zone.getUniqueID(), zone);
					form.container.createWorkZone.runAndSend(zone.getID(), zone.getUniqueID(), rectangle, anchor);
					updateHud(form);
				}
			}

			@Override
			public void onRemovedZone(SettlementWorkZone zone, Rectangle rectangle) {
				HashMap zones = getZones(form);
				if (zone.shrinkZone(level, rectangle)) {
					form.container.shrinkWorkZone.runAndSend(zone.getUniqueID(), rectangle);
					if (zone.shouldRemove()) {
						zones.remove(zone.getUniqueID());
					}
					updateHud(form);
				}
			}

			@Override
			public void onExpandedZone(SettlementWorkZone zone, Rectangle rectangle, Point anchor) {
				HashMap zones = getZones(form);
				boolean updated = zone.expandZone(level, rectangle, anchor, (x, y) -> zones.values().stream().anyMatch(z -> ((SettlementWorkZone)z).containsTile(x, y)));
				if (updated) {
					form.container.expandWorkZone.runAndSend(zone.getUniqueID(), rectangle, anchor);
					updateHud(form);
				}
			}

			@Override
			public void isCancelled() {
				super.isCancelled();
				setOverrideShowZones(form, false);
			}

			@Override
			public void isCleared() {
				super.isCleared();
				setOverrideShowZones(form, false);
			}
		}, form);
		setOverrideShowZones(form, true);
	}

	public static HashMap getZones(SettlementAssignWorkForm form) {
		try {
			return (HashMap)zonesField.get(form);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static void setOverrideShowZones(SettlementAssignWorkForm form, boolean value) {
		try {
			overrideShowZonesField.setBoolean(form, value);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static void updateHud(SettlementAssignWorkForm form) {
		try {
			updateHudElementsMethod.invoke(form);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
