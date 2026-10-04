package opusliews.settlement;

import java.util.Collection;

import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormContentIconVarToggleButton;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.position.FormPositionContainer;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.ui.ButtonColor;
import opusliews.forms.SettlementChestProtectionForm;
import opusliews.logging.Logging;

public final class SettlementChestProtectionUI {
	private static final int configSlotWidth = 36;
	private static final int iconInsetY = 4;

	private SettlementChestProtectionUI() {
	}

	public static void addStorageConfigButton(SettlementAssignWorkForm form, FormContentBox content) {
		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtectionUI] addStorageConfigButton called form=" + form + " content=" + content);
			logComponents(content);
		}

		if (form == null || content == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionUI] Cannot add storage config button form=" + form + " content=" + content);
			return;
		}

		FormLocalTextButton assignStorage = findFirstTextButton(content);
		if (assignStorage == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionUI] FAILED to find Assign Storage button");
			return;
		}

		int expectedIconY = assignStorage.getY() + iconInsetY;
		int storageRight = assignStorage.getX() + assignStorage.getWidth();
		FormContentIconVarToggleButton hideButton = findHideButton(content, storageRight, expectedIconY);

		if (hideButton == null) {
			hideButton = findHideButton(content, storageRight + configSlotWidth, expectedIconY);
			if (hideButton != null) {
				if (Logging.logEnabled) {
					Logging.logMessage("[ChestProtectionUI] Storage config slot already present; skipping duplicate injection storageX="
							+ assignStorage.getX() + " storageY=" + assignStorage.getY() + " storageWidth=" + assignStorage.getWidth()
							+ " hideX=" + hideButton.getX() + " hideY=" + hideButton.getY());
				}
				return;
			}

			if (Logging.logEnabled) {
				Logging.logMessage("[ChestProtectionUI] FAILED to find storage hide button. storageX=" + assignStorage.getX()
						+ " storageY=" + assignStorage.getY() + " storageWidth=" + assignStorage.getWidth()
						+ " expectedHideX=" + storageRight + " expectedHideY=" + expectedIconY);
				logComponents(content);
			}
			return;
		}

		if (assignStorage.getWidth() <= configSlotWidth) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionUI] Assign Storage button too narrow to insert config button width=" + assignStorage.getWidth());
			return;
		}

		int oldWidth = assignStorage.getWidth();
		assignStorage.setWidth(oldWidth - configSlotWidth);
		int configX = assignStorage.getX() + assignStorage.getWidth();

		FormContentIconButton configButton = content.addComponent(new FormContentIconButton(
				configX + 2, assignStorage.getY() + iconInsetY,
				FormInputSize.SIZE_32, ButtonColor.BASE,
				form.getInterfaceStyle().config_button_32,
				new LocalMessage("ui", "protectsettlementchests")));
		configButton.onClicked(e -> openSettings(form));

		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtectionUI] Added Assign Storage config button storageX=" + assignStorage.getX()
					+ " storageY=" + assignStorage.getY() + " oldStorageWidth=" + oldWidth
					+ " newStorageWidth=" + assignStorage.getWidth() + " configX=" + configButton.getX()
					+ " configY=" + configButton.getY() + " hideX=" + hideButton.getX() + " hideY=" + hideButton.getY());
		}
	}

	public static void openSettings(SettlementAssignWorkForm form) {
		if (form == null) {
			if (Logging.logEnabled) Logging.logMessage("[ChestProtectionUI] Cannot open settings for null Assign Work form");
			return;
		}
		if (Logging.logEnabled) Logging.logMessage("[ChestProtectionUI] Opening Protect Settlement Chests settings settlement=" + form.container.getSettlementUniqueID());
		SettlementChestProtectionForm settings = new SettlementChestProtectionForm(form, () -> form.makeCurrent(form.work));
		form.addComponent(settings);
		form.makeCurrent(settings);
	}

	private static FormLocalTextButton findFirstTextButton(FormContentBox content) {
		FormLocalTextButton result = null;
		Collection components = content.getComponents();
		for (Object object : components) {
			if (!(object instanceof FormLocalTextButton)) continue;
			FormLocalTextButton button = (FormLocalTextButton)object;
			if (result == null || button.getY() < result.getY()) result = button;
		}
		return result;
	}

	private static FormContentIconVarToggleButton findHideButton(FormContentBox content, int expectedX, int expectedY) {
		Collection components = content.getComponents();
		for (Object object : components) {
			if (!(object instanceof FormContentIconVarToggleButton)) continue;
			FormContentIconVarToggleButton button = (FormContentIconVarToggleButton)object;
			if (button.getX() == expectedX && button.getY() == expectedY) return button;
		}
		return null;
	}

	private static void logComponents(FormContentBox content) {
		if (!Logging.logEnabled) return;
		if (content == null) {
			Logging.logMessage("[ChestProtectionUI] Component dump skipped: content=null");
			return;
		}

		Collection components = content.getComponents();
		Logging.logMessage("[ChestProtectionUI] Component dump count=" + components.size() + " contentWidth=" + content.getWidth() + " contentHeight=" + content.getHeight());
		for (Object object : components) {
			if (object == null) {
				Logging.logMessage("[ChestProtectionUI]   component=null");
				continue;
			}

			String position = "";
			if (object instanceof FormPositionContainer) {
				FormPositionContainer positioned = (FormPositionContainer)object;
				position = " x=" + positioned.getX() + " y=" + positioned.getY();
			}
			String bounds = "";
			if (object instanceof FormComponent) {
				bounds = " bounds=" + ((FormComponent)object).getBoundingBox();
			}
			Logging.logMessage("[ChestProtectionUI]   " + object.getClass().getName() + position + bounds);
		}
	}
}
