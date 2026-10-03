package opusliews.forms;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.gameTool.GameToolManager;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.util.GameUtils;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.fairType.FairType;
import necesse.gfx.forms.ContainerComponent;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.FormSwitcher;
import necesse.gfx.forms.components.FormButton;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormContentButton;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormDropdownSelectionButton;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormLabelEdit;
import necesse.gfx.forms.components.FormMouseHover;
import necesse.gfx.forms.components.FormSettlerDataIcon;
import necesse.gfx.forms.components.FormSettlerDataNameLabel;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.floatMenu.ColorHueSelectorFloatMenu;
import necesse.gfx.forms.events.FormValueEvent;
import necesse.gfx.forms.presets.ConfirmationForm;
import necesse.gfx.forms.presets.containerComponent.settlement.CreateOrExpandGlobalZoneGameTool;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementSettlersForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.container.settlement.data.SettlementSettlerData;
import necesse.level.maps.hudManager.HudDrawElement;
import opusliews.fishing.FishingAreaAssignUI;
import opusliews.fishing.FishingAreaLevelData;
import opusliews.network.PacketFishingAreaAction;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class FishingAreasForm extends FormSwitcher {
	private static String lastSearch = "";
	private final SettlementAssignWorkForm parent;
	private final Client client;
	private final int settlementUniqueID;
	private final Form settlersForm;
	private final Form manageForm;
	private final FormContentBox settlersContent;
	private final FormContentBox manageContent;
	private final FormTextInput searchInput;
	private final FormLocalTextButton manageButton;
	private final FormLocalTextButton createButton;
	private final FormLocalTextButton backButton;
	private final ConfirmationForm deleteConfirm;
	private final ArrayList<SelectButton> settlerButtons = new ArrayList<>();
	private final ArrayList<HudDrawElement> settlerHudElements = new ArrayList<>();
	private final ArrayList<HudDrawElement> manageHudElements = new ArrayList<>();
	private final HashMap<Integer, FishingAreaLevelData.FishingArea> areas = new HashMap<>();
	private final HashMap<Integer, Integer> assignments = new HashMap<>();
	private int settlersContentHeight = 100;
	private int manageContentHeight = 100;
	private int currentEditAreaUniqueID;
	private int currentColorAreaUniqueID;

	public FishingAreasForm(SettlementAssignWorkForm parent) {
		this.parent = parent;
		this.client = parent.client;
		this.settlementUniqueID = parent.container.getSettlementUniqueID();
		this.deleteConfirm = addComponent(new ConfirmationForm("deleteFishingArea", 300, 200));

		this.settlersForm = addComponent(new Form("fishingAreaSettlers", 500, 300));
		FormFlow flow = new FormFlow(5);
		settlersForm.addComponent(new FormLocalLabel("ui", "settlementassignfishingareas", new FontOptions(20), 0, settlersForm.getWidth() / 2, flow.next(25)));
		searchInput = settlersForm.addComponent(new FormTextInput(4, flow.next(28), FormInputSize.SIZE_24, settlersForm.getWidth() - 8, -1, 500));
		searchInput.placeHolder = new LocalMessage("ui", "searchtip");
		searchInput.rightClickToClear = true;
		searchInput.rightClickToClearTooltip = new LocalMessage("controls", "clearsearchtip");
		searchInput.setText(lastSearch, false);
		searchInput.onChange(event -> {
			lastSearch = searchInput.getText();
			updateSettlersContent();
		});
		settlersContent = settlersForm.addComponent(new FormContentBox(0, flow.next(210), settlersForm.getWidth(), 210));

		this.manageForm = addComponent(new Form("manageFishingAreas", 500, 300));
		manageButton = settlersForm.addComponent(new FormLocalTextButton("ui", "settlementmanagefishingareas", 4, settlersForm.getHeight() - 28, settlersForm.getWidth() - 8, FormInputSize.SIZE_24, ButtonColor.BASE));
		manageButton.onClicked(event -> makeCurrent(manageForm));
		manageContent = manageForm.addComponent(new FormContentBox(0, 0, manageForm.getWidth(), manageForm.getHeight() - 30));
		int backWidth = 150;
		createButton = manageForm.addComponent(new FormLocalTextButton("ui", "settlementfishingareanew", 4, manageForm.getHeight() - 28, manageForm.getWidth() - backWidth - 6, FormInputSize.SIZE_24, ButtonColor.BASE));
		createButton.setCooldown(500);
		createButton.onClicked(event -> client.network.sendPacket(PacketFishingAreaAction.create(settlementUniqueID)));
		backButton = manageForm.addComponent(new FormLocalTextButton("ui", "backbutton", manageForm.getWidth() - backWidth + 2, manageForm.getHeight() - 28, backWidth - 6, FormInputSize.SIZE_24, ButtonColor.BASE));
		backButton.onClicked(event -> {
			GameToolManager.clearGameTools(this);
			currentEditAreaUniqueID = 0;
			makeCurrent(settlersForm);
		});

		FishingAreaLevelData.Snapshot snapshot = FishingAreaAssignUI.getSnapshot(settlementUniqueID);
		if (snapshot != null) applySnapshot(snapshot);
		else {
			updateSettlersContent();
			updateManageContent();
		}
		makeCurrent(settlersForm);
	}

	public Client getClient() {
		return client;
	}

	public int getSettlementUniqueID() {
		return settlementUniqueID;
	}

	public boolean isActive() {
		return parent != null
				&& parent.containerForm != null
				&& parent.containerForm.isCurrent(parent)
				&& parent.isCurrent(this);
	}

	public void applySnapshot(FishingAreaLevelData.Snapshot snapshot) {
		areas.clear();
		assignments.clear();
		if (snapshot != null) {
			for (FishingAreaLevelData.FishingArea area : snapshot.areas) areas.put(area.uniqueID, area);
			assignments.putAll(snapshot.assignments);
		}
		updateSettlersContent();
		updateManageContent();
		updateSize();
	}

	private void updateSettlersContent() {
		settlerButtons.clear();
		settlersContent.clearComponents();
		clearSettlerHudElements();
		FormFlow flow = new FormFlow(0);
		String searchLower = lastSearch == null ? "" : lastSearch.toLowerCase();
		List<SettlementSettlerData> anglers = parent.containerForm.dataManager.streamSettlers()
				.filter(data -> data.settler != null && "angler".equals(data.settler.getStringID()))
				.filter(data -> {
					int assigned = assignments.getOrDefault(data.mobUniqueID, 0);
					FishingAreaLevelData.FishingArea area = areas.get(assigned);
					return data.matchesSearch(searchLower, client.getLevel(), client.getPlayer())
							|| assigned == 0 && Localization.translate("ui", "settlementfishanywhere").toLowerCase().contains(searchLower)
							|| area != null && area.name.toLowerCase().contains(searchLower);
				})
				.collect(Collectors.toList());

		for (SettlementSettlerData data : anglers) addSettlerRow(flow, data);
		if (anglers.isEmpty()) {
			flow.next(16);
			settlersContent.addComponent((FormLocalLabel)flow.nextY(new FormLocalLabel("ui", "settlementnofishinganglers", new FontOptions(16), 0, settlersForm.getWidth() / 2, 0, settlersForm.getWidth() - 20), 16));
		}
		settlersContentHeight = Math.max(flow.next(), 100);
		settlersContent.alwaysShowVerticalScrollBar = true;
		updateSelectButtons();
		updateSize();
		ControllerInput.submitNextRefreshFocusEvent();
	}

	private void addSettlerRow(FormFlow flow, SettlementSettlerData data) {
		int padding = SettlementSettlersForm.SETTLER_LIST_PADDING;
		int height = 32 + padding * 2;
		int startY = flow.next(height);
		int y = startY + padding;
		FormMouseHover mouseHover = settlersContent.addComponent(new FormMouseHover(0, y, settlersContent.getWidth(), height), Integer.MAX_VALUE);
		int selectWidth = 250;
		int buttonX = settlersForm.getWidth() - selectWidth - settlersContent.getScrollBarWidth() - 2;
		FormDropdownSelectionButton selection = (FormDropdownSelectionButton)settlersContent.addComponent(new FormDropdownSelectionButton(buttonX, y + 3, FormInputSize.SIZE_24, ButtonColor.BASE, selectWidth));
		settlerButtons.add(new SelectButton(data, selection));
		selection.onSelected(event -> {
			int areaUniqueID = (Integer)((FormValueEvent)event).value;
			assignments.put(data.mobUniqueID, areaUniqueID);
			client.network.sendPacket(PacketFishingAreaAction.assign(settlementUniqueID, data.mobUniqueID, areaUniqueID));
		});
		selection.controllerFocusHashcode = "fishingareaselection" + data.mobUniqueID;

		HudDrawElement hover = new HudDrawElement() {
			@Override
			public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
				if (!mouseHover.isHovering() && !FishingAreasForm.this.isControllerFocus(selection)) return;
				FishingAreaLevelData.FishingArea area = areas.get(assignments.getOrDefault(data.mobUniqueID, 0));
				addAreaDrawables(area, list, camera);
			}
		};
		client.getLevel().hudManager.addElement(hover);
		settlerHudElements.add(hover);

		settlersContent.addComponent(new FormSettlerDataIcon(5, y, data, parent.containerForm));
		int namesX = 37;
		int namesWidth = buttonX - namesX;
		FontOptions nameFont = new FontOptions(16);
		settlersContent.addComponent(new FormSettlerDataNameLabel(parent.containerForm.getClient(), data, nameFont, FairType.TextAlign.LEFT, namesX, y, namesWidth));
		FontOptions typeFont = new FontOptions(12);
		settlersContent.addComponent(new FormLabel(GameUtils.maxString(data.settler.getGenericMobName(), typeFont, namesWidth), typeFont, -1, namesX, y + 16));
	}

	private void updateSelectButtons() {
		for (SelectButton select : settlerButtons) {
			select.button.options.clear();
			select.button.options.add(0, new LocalMessage("ui", "settlementfishanywhere"));
			areas.values().stream().sorted(Comparator.comparingInt(area -> area.index)).forEach(area -> select.button.options.add(area.uniqueID, new StaticMessage(area.name)));
			int assigned = assignments.getOrDefault(select.data.mobUniqueID, 0);
			if (assigned == 0) select.button.setSelected(0, new LocalMessage("ui", "settlementfishanywhere"));
			else {
				FishingAreaLevelData.FishingArea area = areas.get(assigned);
				select.button.setSelected(assigned, area == null ? new LocalMessage("ui", "settlementunknownarea") : new StaticMessage(area.name));
			}
		}
	}

	private void updateManageContent() {
		manageContent.clearComponents();
		clearManageHudElements();
		FormFlow flow = new FormFlow(5);
		manageContent.addComponent(new FormLocalLabel("ui", "settlementmanagefishingareas", new FontOptions(20), 0, manageForm.getWidth() / 2, flow.next(25)));
		ArrayList<FishingAreaLevelData.FishingArea> sorted = new ArrayList<>(areas.values());
		sorted.sort(Comparator.comparingInt(area -> area.index));

		for (FishingAreaLevelData.FishingArea area : sorted) {
			int height = 24;
			FormMouseHover mouseHover = manageContent.addComponent(new FormMouseHover(0, flow.next(), manageContent.getWidth(), height), Integer.MAX_VALUE);
			FontOptions nameOptions = new FontOptions(16);
			FormLabelEdit label = manageContent.addComponent(new FormLabelEdit(area.name, nameOptions, getInterfaceStyle().activeTextColor, 5, flow.next() + 4, 100, 30), -1000);
			int buttonX = manageContent.getWidth() - 24 - manageContent.getScrollBarWidth() - 2;

			FormContentIconButton deleteButton = manageContent.addComponent(new FormContentIconButton(buttonX, flow.next(), FormInputSize.SIZE_24, ButtonColor.RED, getInterfaceStyle().container_storage_remove, new GameMessage[]{new LocalMessage("ui", "deletebutton")}));
			deleteButton.onClicked(event -> {
				deleteConfirm.setupConfirmation(new LocalMessage("ui", "settlementfishingareadeleteconfirm", "area", area.name), () -> {
					client.network.sendPacket(PacketFishingAreaAction.delete(settlementUniqueID, area.uniqueID));
					makeCurrent(manageForm);
				}, () -> makeCurrent(manageForm));
				makeCurrent(deleteConfirm);
			});
			buttonX -= 24;

			FormContentIconButton configureButton = manageContent.addComponent(new FormContentIconButton(buttonX, flow.next(), FormInputSize.SIZE_24, ButtonColor.BASE, getInterfaceStyle().container_storage_config, new GameMessage[]{new LocalMessage("ui", "configurebutton")}));
			configureButton.onClicked(event -> startEditAreaTool(area));
			buttonX -= 24;

			FormContentButton colorButton = manageContent.addComponent(new FormContentButton(buttonX, flow.next(), 24, FormInputSize.SIZE_24, ButtonColor.BASE) {
				@Override
				protected void drawContent(int x, int y, int width, int height) {
					Color drawColor = getDrawColor();
					float[] hsb = Color.RGBtoHSB(drawColor.getRed(), drawColor.getGreen(), drawColor.getBlue(), null);
					Renderer.initQuadDraw(width, height).color(Color.getHSBColor((float)area.colorHue / 360.0F, 0.8F, hsb[2])).draw(x, y);
				}

				@Override
				protected void addTooltips(PlayerMob perspective) {
					super.addTooltips(perspective);
					GameTooltipManager.addTooltip(new StringTooltips(Localization.translate("ui", "changecolorbutton")), TooltipLocation.FORM_FOCUS);
				}
			});
			colorButton.onClicked(event -> {
				currentColorAreaUniqueID = area.uniqueID;
				int startHue = area.colorHue;
				((FormButton)event.from).getManager().openFloatMenu(new ColorHueSelectorFloatMenu(event.from, 150, 24, (float)area.colorHue / 360.0F) {
					@Override
					public void onChanged(float hue) {
						area.colorHue = (int)(hue * 360.0F);
					}

					@Override
					public void dispose() {
						super.dispose();
						int nextHue = (int)(picker.getSelectedHue() * 360.0F);
						if (startHue != nextHue) client.network.sendPacket(PacketFishingAreaAction.recolor(settlementUniqueID, area.uniqueID, nextHue));
						currentColorAreaUniqueID = 0;
					}
				}, colorButton, event.event, 0, 0);
			});
			buttonX -= 24;

			FormContentIconButton renameButton = manageContent.addComponent(new FormContentIconButton(buttonX, flow.next(), FormInputSize.SIZE_24, ButtonColor.BASE, getInterfaceStyle().container_rename, new GameMessage[]{new LocalMessage("ui", "renamebutton")}));
			AtomicBoolean typing = new AtomicBoolean(false);
			label.onMouseChangedTyping(event -> {
				typing.set(label.isTyping());
				runRenameUpdate(area, label, renameButton);
			});
			label.onSubmit(event -> {
				typing.set(label.isTyping());
				runRenameUpdate(area, label, renameButton);
			});
			renameButton.onClicked(event -> {
				typing.set(!label.isTyping());
				label.setTyping(!label.isTyping());
				runRenameUpdate(area, label, renameButton);
			});
			runRenameUpdate(area, label, renameButton);
			label.setWidth(buttonX);

			HudDrawElement hover = new HudDrawElement() {
				@Override
				public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
					if (mouseHover.isHovering() || FishingAreasForm.this.isControllerFocus(deleteButton) || FishingAreasForm.this.isControllerFocus(configureButton)
							|| FishingAreasForm.this.isControllerFocus(colorButton) || FishingAreasForm.this.isControllerFocus(renameButton)
							|| currentEditAreaUniqueID == area.uniqueID || currentColorAreaUniqueID == area.uniqueID) addAreaDrawables(area, list, camera);
				}
			};
			client.getLevel().hudManager.addElement(hover);
			manageHudElements.add(hover);
			flow.next(height);
		}

		manageContentHeight = Math.max(flow.next(), 100);
		manageContent.alwaysShowVerticalScrollBar = true;
		createButton.setActive(areas.size() < FishingAreaLevelData.maxAreas);
		updateSize();
		ControllerInput.submitNextRefreshFocusEvent();
	}

	private void runRenameUpdate(FishingAreaLevelData.FishingArea area, FormLabelEdit label, FormContentIconButton renameButton) {
		if (label.isTyping()) {
			renameButton.setIcon(getInterfaceStyle().container_rename_save);
			renameButton.setTooltips(new LocalMessage("ui", "savebutton"));
			return;
		}
		if (!label.getText().equals(area.name)) {
			if (label.getText().isEmpty()) label.setText(area.name);
			else {
				area.name = label.getText();
				client.network.sendPacket(PacketFishingAreaAction.rename(settlementUniqueID, area.uniqueID, area.name));
				updateSelectButtons();
			}
		}
		renameButton.setIcon(getInterfaceStyle().container_rename);
		renameButton.setTooltips(new LocalMessage("ui", "renamebutton"));
	}

	private void startEditAreaTool(FishingAreaLevelData.FishingArea area) {
		if (client.getLevel() == null || !area.levelIdentifier.equals(client.getLevel().getIdentifier())) {
			client.chat.addMessage(Localization.translate("ui", "settlementfishingareaotherlevel"));
			return;
		}
		currentEditAreaUniqueID = area.uniqueID;
		GameToolManager.clearGameTools(this);
		GameToolManager.setGameTool(new CreateOrExpandGlobalZoneGameTool(client.getLevel()) {
			@Override
			public void onExpandedZone(Rectangle rectangle) {
				area.zoning.addRectangle(rectangle);
				client.network.sendPacket(PacketFishingAreaAction.changeZone(settlementUniqueID, area.uniqueID, rectangle, true));
			}

			@Override
			public void onShrankZone(Rectangle rectangle) {
				area.zoning.removeRectangle(rectangle);
				client.network.sendPacket(PacketFishingAreaAction.changeZone(settlementUniqueID, area.uniqueID, rectangle, false));
			}

			@Override
			public void isCancelled() {
				super.isCancelled();
				if (currentEditAreaUniqueID == area.uniqueID) currentEditAreaUniqueID = 0;
			}

			@Override
			public void isCleared() {
				super.isCleared();
				if (currentEditAreaUniqueID == area.uniqueID) currentEditAreaUniqueID = 0;
			}
		}, this);
	}

	private void addAreaDrawables(FishingAreaLevelData.FishingArea area, List list, GameCamera camera) {
		if (area == null || client.getLevel() == null || !area.levelIdentifier.equals(client.getLevel().getIdentifier())) return;
		Color edgeColor = Color.getHSBColor((float)area.colorHue / 360.0F, 0.8F, 0.6F);
		Color fillColor = Color.getHSBColor((float)area.colorHue / 360.0F, 0.8F, 0.8F);
		synchronized (area.zoning) {
			SharedTextureDrawOptions options = area.zoning.getDrawOptions(edgeColor, new Color(fillColor.getRed(), fillColor.getGreen(), fillColor.getBlue(), 75), camera);
			if (options != null) list.add(new SortedDrawable() {
				@Override public int getPriority() { return 2147482647; }
				@Override public void draw(TickManager tickManager) { options.draw(); }
			});
		}
	}

	private void clearSettlerHudElements() {
		for (HudDrawElement element : settlerHudElements) element.remove();
		settlerHudElements.clear();
	}

	private void clearManageHudElements() {
		for (HudDrawElement element : manageHudElements) element.remove();
		manageHudElements.clear();
	}

	private void clearHudElements() {
		clearSettlerHudElements();
		clearManageHudElements();
	}

	private void updateSize() {
		settlersForm.setHeight(Math.min(420, settlersContent.getY() + settlersContentHeight + 30));
		settlersContent.setContentBox(new Rectangle(0, 0, settlersContent.getWidth(), settlersContentHeight));
		settlersContent.setWidth(settlersForm.getWidth());
		settlersContent.setHeight(settlersForm.getHeight() - settlersContent.getY() - 30);
		manageButton.setY(settlersForm.getHeight() - 27);
		ContainerComponent.setPosInventory(settlersForm);

		manageForm.setHeight(Math.min(420, manageContent.getY() + manageContentHeight + 30));
		manageContent.setContentBox(new Rectangle(0, 0, manageContent.getWidth(), manageContentHeight));
		manageContent.setWidth(manageForm.getWidth());
		manageContent.setHeight(manageForm.getHeight() - manageContent.getY() - 30);
		createButton.setY(manageForm.getHeight() - 27);
		backButton.setY(manageForm.getHeight() - 27);
		ContainerComponent.setPosInventory(manageForm);
	}

	@Override
	public void dispose() {
		FishingAreaAssignUI.unregister(this);
		GameToolManager.clearGameTools(this);
		clearHudElements();
		super.dispose();
	}

	private static final class SelectButton {
		private final SettlementSettlerData data;
		private final FormDropdownSelectionButton button;

		private SelectButton(SettlementSettlerData data, FormDropdownSelectionButton button) {
			this.data = data;
			this.button = button;
		}
	}
}
