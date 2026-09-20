package opusliews.forms;

import java.util.ArrayList;
import java.util.Collection;
import necesse.engine.GlobalData;
import necesse.engine.gameTool.GameToolManager;
import necesse.engine.network.client.Client;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.MainGameFormManager;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.forms.components.FormProgressBar;
import necesse.gfx.forms.components.containerSlot.FormContainerSlot;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.forms.position.FormPositionContainer;
import necesse.gfx.forms.presets.containerComponent.object.CraftingStationContainerForm;
import opusliews.container.DynamicCraftingStationContainer;
import opusliews.crafting.CraftingStorageSelectTool;
import opusliews.crafting.CraftingTaskBoardSelectTool;
import opusliews.hud.CraftingStationLinkHud;

public class DynamicCraftingStationContainerForm extends CraftingStationContainerForm {
	private static final int LEFT_PANEL_WIDTH = 118;
	private static final int LEFT_PANEL_GAP = 8;
	private static final int OUTPUT_PANEL_WIDTH = 62;
	private static final int OUTPUT_PANEL_HEIGHT = 76;
	private static final int OUTPUT_PANEL_GAP = 12;

	protected final Client client;
	protected final DynamicCraftingStationContainer stationContainer;

	public DynamicCraftingStationContainerForm(Client client, DynamicCraftingStationContainer container) {
		super(client, container);
		this.client = client;
		this.stationContainer = container;

		int vanillaWidth = craftingForm.getWidth();
		int leftOffset = LEFT_PANEL_WIDTH + LEFT_PANEL_GAP;

		Collection components = new ArrayList(craftingForm.getComponents());
		ArrayList<FormPositionContainer> positioned = new ArrayList<>();
		ArrayList<Integer> originalX = new ArrayList<>();
		ArrayList<Integer> originalY = new ArrayList<>();

		for (Object componentObject : components) {
			FormComponent component = (FormComponent)componentObject;
			if (component instanceof FormPositionContainer) {
				FormPositionContainer position = (FormPositionContainer)component;
				positioned.add(position);
				originalX.add(position.getX());
				originalY.add(position.getY());
			}
		}

		for (int i = 0; i < positioned.size(); i++) {
			positioned.get(i).setPosition(originalX.get(i) + leftOffset, originalY.get(i));
		}

		craftingForm.setWidth(vanillaWidth + leftOffset + OUTPUT_PANEL_GAP + OUTPUT_PANEL_WIDTH + 4);

		FormTwoLineTextButton inputButton = craftingForm.addComponent(new FormTwoLineTextButton(
				"Set Input", "Storage",
				7, 104,
				LEFT_PANEL_WIDTH - 14, 48));
		inputButton.onClicked(event -> startStorageSelection(true));

		FormTwoLineTextButton outputButton = craftingForm.addComponent(new FormTwoLineTextButton(
				"Set Output", "Storage",
				7, 164,
				LEFT_PANEL_WIDTH - 14, 48));
		outputButton.onClicked(event -> startStorageSelection(false));

		FormTwoLineTextButton taskBoardButton = craftingForm.addComponent(new FormTwoLineTextButton(
				"Select Task", "Board",
				7, 224,
				LEFT_PANEL_WIDTH - 14, 48));
		taskBoardButton.onClicked(event -> startTaskBoardSelection());

		int outputPanelX = leftOffset + vanillaWidth + OUTPUT_PANEL_GAP;
		Form outputPanel = craftingForm.addComponent(new Form("craftingStationOutput", OUTPUT_PANEL_WIDTH, OUTPUT_PANEL_HEIGHT));
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
		CraftingStationLinkHud.setOpenStation(
				client.getLevel(),
				stationContainer.stationEntity.tileX,
				stationContainer.stationEntity.tileY
		);
	}

	protected void startStorageSelection(boolean input) {
		startLinkSelection();
		GameToolManager.setGameTool(
				new CraftingStorageSelectTool(
						stationContainer,
						client.getLevel(),
						input,
						this::finishLinkSelection
				),
				this
		);
	}

	protected void startTaskBoardSelection() {
		startLinkSelection();
		GameToolManager.setGameTool(
				new CraftingTaskBoardSelectTool(
						stationContainer,
						client.getLevel(),
						this::finishLinkSelection
				),
				this
		);
	}

	protected void startLinkSelection() {
		GameToolManager.clearGameTools(this);
		stationContainer.setSelectingLinkedElement.runAndSend(true);
		craftingForm.setHidden(true);

		MainGameFormManager formManager = (MainGameFormManager)GlobalData.getCurrentState().getFormManager();
		formManager.inventory.setHidden(true);
		formManager.toolbar.setHidden(true);
	}

	protected void finishLinkSelection() {
		stationContainer.setSelectingLinkedElement.runAndSend(false);
		craftingForm.setHidden(false);

		MainGameFormManager formManager = (MainGameFormManager)GlobalData.getCurrentState().getFormManager();
		formManager.inventory.setHidden(false);
		formManager.toolbar.setHidden(false);
	}

	@Override
	public void dispose() {
		CraftingStationLinkHud.clearOpenStation(
				client.getLevel(),
				stationContainer.stationEntity.tileX,
				stationContainer.stationEntity.tileY
		);
		if (stationContainer.isSelectingStorage()) finishLinkSelection();
		GameToolManager.clearGameTools(this);
		super.dispose();
	}
}
