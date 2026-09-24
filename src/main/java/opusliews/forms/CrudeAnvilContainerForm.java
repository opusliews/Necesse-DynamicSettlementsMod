package opusliews.forms;

import necesse.engine.GlobalData;
import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.MainGameFormManager;
import necesse.gfx.forms.components.FormProgressBar;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.engine.gameTool.GameToolManager;
import opusliews.crafting.CrudeAnvilForgeSelectTool;
import opusliews.hud.CraftingStationLinkHud;
import necesse.gfx.forms.presets.containerComponent.object.CraftingStationContainerForm;
import opusliews.container.CrudeAnvilContainer;

public class CrudeAnvilContainerForm extends CraftingStationContainerForm {
	protected final Client client;
	protected final CrudeAnvilContainer stationContainer;
	private static final int OUTPUT_PANEL_WIDTH = 62;
	private static final int OUTPUT_PANEL_HEIGHT = 76;
	private static final int OUTPUT_PANEL_GAP = 12;

	public CrudeAnvilContainerForm(Client client, CrudeAnvilContainer container) {
		super(client, container);
		this.client = client;
		this.stationContainer = container;

		int vanillaWidth = craftingForm.getWidth();
		craftingForm.setWidth(vanillaWidth + 90);
		craftingForm.setHeight(Math.max(craftingForm.getHeight(), 230));

		int outputPanelX = vanillaWidth + OUTPUT_PANEL_GAP;
		FormTwoLineTextButton forgeButton = craftingForm.addComponent(new FormTwoLineTextButton(
				Localization.translate("ui", "linkforge"), Localization.translate("ui", "forge"),
				outputPanelX - 8, 174, 78, 48));
		forgeButton.onClicked(event -> startForgeSelection());

		Form outputPanel = craftingForm.addComponent(new Form("crudeAnvilOutput", OUTPUT_PANEL_WIDTH, OUTPUT_PANEL_HEIGHT));
		outputPanel.setPosition(new FormFixedPosition(outputPanelX, 92));
		outputPanel.addComponent(new FormContainerSlot(client, container, container.OUTPUT_SLOT, 11, 8));
		outputPanel.addComponent(new FormProgressBar(9, 54, 44, false) {
			@Override
			public float getProgress() {
				return container.getCraftProgress();
			}
		});
	}

	@Override
	protected void init() {
		super.init();
		CraftingStationLinkHud.setOpenStation(client.getLevel(), stationContainer.stationEntity.tileX, stationContainer.stationEntity.tileY);
	}

	private void startForgeSelection() {
		GameToolManager.clearGameTools(this);
		stationContainer.setSelectingForge.runAndSend(true);
		craftingForm.setHidden(true);
		MainGameFormManager formManager = (MainGameFormManager)GlobalData.getCurrentState().getFormManager();
		formManager.inventory.setHidden(true);
		formManager.toolbar.setHidden(true);
		GameToolManager.setGameTool(new CrudeAnvilForgeSelectTool(stationContainer, client.getLevel(), this::finishForgeSelection), this);
	}

	private void finishForgeSelection() {
		stationContainer.setSelectingForge.runAndSend(false);
		craftingForm.setHidden(false);
		MainGameFormManager formManager = (MainGameFormManager)GlobalData.getCurrentState().getFormManager();
		formManager.inventory.setHidden(false);
		formManager.toolbar.setHidden(false);
	}

	@Override
	public void dispose() {
		CraftingStationLinkHud.clearOpenStation(client.getLevel(), stationContainer.stationEntity.tileX, stationContainer.stationEntity.tileY);
		GameToolManager.clearGameTools(this);
		super.dispose();
	}
}
