package opusliews.forms;

import necesse.engine.localization.Localization;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.Control;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.util.FloatDimension;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.Renderer;
import necesse.gfx.fairType.FairButtonGlyph;
import necesse.gfx.fairType.FairGlyph;
import necesse.gfx.fairType.FairItemGlyph;
import necesse.gfx.fairType.FairType;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.floatMenu.SelectionFloatMenu;
import necesse.gfx.forms.presets.containerComponent.ContainerFormSwitcher;
import necesse.gfx.gameFont.FontManager;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.gfx.ui.ButtonIcon;
import necesse.gfx.ui.ButtonState;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;
import necesse.inventory.recipe.Recipe;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.LevelObject;
import opusliews.container.CraftingTaskBoardContainer;
import opusliews.crafting.CraftingTask;
import opusliews.hud.CraftingStationLinkHud;

import java.awt.*;
import java.util.List;
import java.util.*;

import static java.lang.Math.max;

public class CraftingTaskBoardContainerForm extends ContainerFormSwitcher {
	private static final int ITEM_WIDTH = 400;
	private static final int CONDITION_WIDTH = 400;
	private static final int STATUS_WIDTH = 72;
	private static final int CONFIG_WIDTH = 72;
	private static final int FORM_WIDTH = ITEM_WIDTH + CONDITION_WIDTH + STATUS_WIDTH + CONFIG_WIDTH;

	private final Client client;
	private final CraftingTaskBoardContainer taskContainer;
	private final Form boardForm;
	private final FormContentBox content;
	private final Form itemSelectForm;
	private final Form conditionConfigForm;

	private int lastSignature = Integer.MIN_VALUE;
	private int currentConfigIndex = -1;
	private final HashSet<String> collapsedPickerCategories = new HashSet<>();

	public CraftingTaskBoardContainerForm(Client client, CraftingTaskBoardContainer container) {
		super(client, container);
		this.client = client;
		this.taskContainer = container;

		boardForm = (Form)addComponent(new Form("craftingTasks", FORM_WIDTH, 400));
		FormFlow flow = new FormFlow(10);
		boardForm.addComponent(flow.nextY(new FormLabel(
				Localization.translate("ui", "craftingtaskstitle"),
				new FontOptions(32),
				0,
				boardForm.getWidth() / 2,
				0
		), 5));

		boardForm.addComponent(new FormContentIconButton(
				boardForm.getWidth() - 28,
				4,
				FormInputSize.SIZE_24,
				ButtonColor.BASE,
				getInterfaceStyle().container_storage_remove,
				new GameMessage[]{new StaticMessage(Localization.translate("ui", "closebutton"))}
		)).onClicked(e -> client.closeContainer(true));

		int contentY = flow.next();
		content = boardForm.addComponent(new FormContentBox(0, contentY, boardForm.getWidth(), boardForm.getHeight() - contentY));
		itemSelectForm = (Form)addComponent(new Form("craftingTaskItemSelect", 0, 0));
		conditionConfigForm = (Form)addComponent(new Form("craftingTaskConditionConfig", 0, 0));

		updateBoard();
		makeCurrent(boardForm);
		onWindowResized(WindowManager.getWindow());
	}

	private int getSignature() {
		int result = taskContainer.hasValidLinkedStation() ? 1 : 0;
		List<CraftingTask> tasks = taskContainer.boardEntity.getTasks();
		result = 31 * result + tasks.size();
		for (CraftingTask task : tasks) {
			result = 31 * result + task.itemID;
			result = 31 * result + task.conditionType;
			result = 31 * result + task.amount;
			result = 31 * result + (task.paused ? 1 : 0);
			result = 31 * result + task.status;
			result = 31 * result + task.problemDetails.hashCode();
		}
		return result;
	}

	private void updateBoard() {
		lastSignature = getSignature();
		content.clearComponents();

		if (!taskContainer.hasValidLinkedStation()) {
			FormFlow flow = new FormFlow(60);
			content.addComponent(flow.nextY(new FormLabel(
					Localization.translate("ui", "craftingboardnotlinked"),
					new FontOptions(20),
					0,
					content.getWidth() / 2,
					0
			), 8));
			content.addComponent(flow.nextY(new FormLabel(
					Localization.translate("ui", "craftingboardlinkfirst"),
					new FontOptions(16),
					0,
					content.getWidth() / 2,
					0
			), 8));
			content.setContentBox(new Rectangle(content.getWidth(), max(content.getHeight(), flow.next())));
			return;
		}

		FormFlow flow = new FormFlow(5);
		int headerY = flow.next(28);
		FontOptions headerOptions = new FontOptions(20);
		content.addComponent(new FormLabel(Localization.translate("ui", "itemheader"), headerOptions, 0, ITEM_WIDTH / 2, headerY + 2));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, ITEM_WIDTH, 0, 0, false));
		content.addComponent(new FormLabel(Localization.translate("ui", "conditionheader"), headerOptions, 0, ITEM_WIDTH + CONDITION_WIDTH / 2, headerY + 2));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, ITEM_WIDTH + CONDITION_WIDTH, 0, 1, false));
		content.addComponent(new FormLabel(Localization.translate("ui", "statusheader"), headerOptions, 0, ITEM_WIDTH + CONDITION_WIDTH + STATUS_WIDTH / 2, headerY + 2));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, ITEM_WIDTH + CONDITION_WIDTH + STATUS_WIDTH, 0, 1, false));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 4, flow.next(), content.getWidth() - 8, true));
		flow.next(4);

		List<CraftingTask> tasks = taskContainer.boardEntity.getTasks();
		for (int i = 0; i < tasks.size(); i++) {
			if (i > 0) {
				content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 4, flow.next(), content.getWidth() + 18, true));
				flow.next(2);
			}
			TaskRow row = new TaskRow(content.getWidth() - content.getScrollBarWidth(), i, tasks.get(i));
			content.addComponent(flow.nextY(row, 2));
		}

		flow.next(12);
		FormLocalTextButton addButton = content.addComponent(new FormLocalTextButton(
				new StaticMessage(Localization.translate("ui", "addnewtask")),
				content.getWidth() / 2 - 100,
				flow.next(28),
				200,
				FormInputSize.SIZE_24,
				ButtonColor.GREEN
		));
		addButton.onClicked(e -> {
			setupItemSelect();
			makeCurrent(itemSelectForm);
		});

		int fullHeight = flow.next() - 35;
		fullHeight = max(fullHeight, 0);
		for (Object componentObject : content.getComponents()) {
			if (componentObject instanceof FormBreakLine) {
				FormBreakLine line = (FormBreakLine)componentObject;
				if (!line.horizontal) {
					line.length = fullHeight;
				}
			}
		}
		content.setContentBox(new Rectangle(content.getWidth(), fullHeight));
	}

	private void setupItemSelect() {
		itemSelectForm.clearComponents();
		itemSelectForm.setWidth(684);
		itemSelectForm.setHeight(420);

		itemSelectForm.addComponent(new FormLabel(
				Localization.translate("ui", "selectitem"),
				new FontOptions(20),
				-1,
				6,
				8
		));

		FormTextInput searchInput = itemSelectForm.addComponent(new FormTextInput(
				itemSelectForm.getWidth() - 158,
				4,
				FormInputSize.SIZE_24,
				150,
				-1,
				100
		));
		searchInput.rightClickToClear = true;
		searchInput.placeHolder = new StaticMessage(Localization.translate("ui", "search"));

		FormContentBox itemContent = itemSelectForm.addComponent(new FormContentBox(
				0,
				36,
				itemSelectForm.getWidth(),
				340
		));

		Runnable rebuild = () -> populateItemSelect(itemContent, searchInput.getText());
		searchInput.onChange(e -> rebuild.run());
		rebuild.run();

		FormLocalTextButton back = itemSelectForm.addComponent(new FormLocalTextButton(
				new StaticMessage(Localization.translate("ui", "backbutton")),
				itemSelectForm.getWidth() / 2 - 100,
				382,
				200,
				FormInputSize.SIZE_32_TO_40,
				ButtonColor.BASE
		));
		back.onClicked(e -> makeCurrent(boardForm));
		onWindowResized(WindowManager.getWindow());
	}

	private void populateItemSelect(FormContentBox itemContent, String search) {
		itemContent.clearComponents();
		String filter = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		boolean searching = !filter.isEmpty();

		LevelObject stationObject = taskContainer.getLinkedStation();
		if (stationObject == null || !(stationObject.object instanceof CraftingStationObject)) {
			itemContent.addComponent(new FormLabel(
					Localization.translate("ui", "nocraftableitems"),
					new FontOptions(16),
					0,
					itemContent.getWidth() / 2,
					24
			));
			itemContent.setContentBox(new Rectangle(itemContent.getWidth(), 56));
			return;
		}

		CraftingStationObject station = (CraftingStationObject)stationObject.object;
		int categoryDepth = max(station.getCraftingCategoryDepth(), 0);
		HashSet forceCategorySolo = station.getForcedSoloCraftingCategories();
		Map<String, ItemCategory> displayNameToCategory = new HashMap<>();
		Map<ItemCategory, ArrayList<Integer>> byCategory = new HashMap<>();
		HashSet<Integer> seenItems = new HashSet<>();

		for (Recipe recipe : taskContainer.getCraftableRecipes()) {
			int itemID = recipe.resultItem.item.getID();
			Item item = ItemRegistry.getItem(itemID);
			if (item == null || !seenItems.add(itemID)) {
				continue;
			}

			InventoryItem inventoryItem = new InventoryItem(item);
			if (searching && !inventoryItem.getItemDisplayName().toLowerCase(Locale.ROOT).contains(filter)) {
				continue;
			}

			ItemCategory category = recipe.getCraftingCategory();
			if (category == null) {
				category = ItemCategory.craftingManager.getItemsCategory(item);
				int desiredDepth = categoryDepth;

				for (ItemCategory checkCategory = category; checkCategory != null; checkCategory = checkCategory.parent) {
					if (forceCategorySolo.contains(checkCategory)) {
						desiredDepth = checkCategory.depth;
						break;
					}
				}

				while (category != null && category.depth > desiredDepth) {
					category = category.parent;
				}
			}

			if (category == null) {
				category = ItemCategory.craftingMasterCategory;
			}

			String displayName = category.displayName.translate();
			ItemCategory existingCategory = displayNameToCategory.get(displayName);
			if (existingCategory != null) {
				category = existingCategory;
			} else {
				displayNameToCategory.put(displayName, category);
			}

			byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(itemID);
		}

		ArrayList<ItemCategory> categories = new ArrayList<>(byCategory.keySet());
		Collections.sort(categories);
		FormFlow flow = new FormFlow(2);
		int contentWidth = itemContent.getWidth() - itemContent.getScrollBarWidth() - 8;
		int iconSize = 36;
		int iconsPerRow = max(1, (contentWidth - 28) / iconSize);

		for (ItemCategory category : categories) {
			boolean expanded = searching || !collapsedPickerCategories.contains(category.stringID);
			int headerY = flow.next(24);
			FormContentIconButton expand = itemContent.addComponent(new FormContentIconButton(
					4,
					headerY + 2,
					FormInputSize.SIZE_20,
					ButtonColor.BASE,
					expanded ? getInterfaceStyle().button_expanded_16 : getInterfaceStyle().button_collapsed_16,
					new GameMessage[0]
			));
			expand.setActive(!searching);
			expand.onClicked(e -> {
				if (collapsedPickerCategories.contains(category.stringID)) {
					collapsedPickerCategories.remove(category.stringID);
				} else {
					collapsedPickerCategories.add(category.stringID);
				}
				populateItemSelect(itemContent, searchInputText(search));
			});
			itemContent.addComponent(new FormLabel(
					category.displayName.translate(),
					new FontOptions(16),
					-1,
					28,
					headerY + 4
			));

			if (!expanded) {
				continue;
			}

			ArrayList<Integer> items = byCategory.get(category);
			int rowCount = (items.size() + iconsPerRow - 1) / iconsPerRow;
			int gridY = flow.next(rowCount * iconSize + 2);
			for (int i = 0; i < items.size(); i++) {
				int itemID = items.get(i);
				int x = 28 + (i % iconsPerRow) * iconSize;
				int y = gridY + (i / iconsPerRow) * iconSize;
				itemContent.addComponent(new TaskItemIcon(x, y, itemID));
			}
		}

		if (categories.isEmpty()) {
			itemContent.addComponent(new FormLabel(
					searching ? Localization.translate("ui", "nomatchingitems") : Localization.translate("ui", "nocraftableitems"),
					new FontOptions(16),
					0,
					itemContent.getWidth() / 2,
					24
			));
			flow.next(48);
		}

		itemContent.setContentBox(new Rectangle(itemContent.getWidth(), flow.next() + 8));
	}

	private String searchInputText(String text) {
		return text == null ? "" : text;
	}

	private void setupConditionConfig(int index) {
		currentConfigIndex = index;
		CraftingTask task = taskContainer.boardEntity.getTask(index);
		if (task == null) {
			makeCurrent(boardForm);
			return;
		}

		conditionConfigForm.clearComponents();
		conditionConfigForm.setWidth(380);
		FormFlow flow = new FormFlow(10);
		conditionConfigForm.addComponent(flow.nextY(new FormLabel(
				getConditionName(task.conditionType, "X"),
				new FontOptions(20),
				0,
				conditionConfigForm.getWidth() / 2,
				0
		), 5));
		flow.next(8);
		FormLabel amountLabel = conditionConfigForm.addComponent(flow.nextY(new FormLabel(
				Integer.toString(task.amount),
				new FontOptions(18),
				0,
				conditionConfigForm.getWidth() / 2,
				0
		), 5));
		flow.next(8);

		int buttonY = flow.next(28);
		int buttonWidth = 50;
		int middleGap = 20;
		int left = conditionConfigForm.getWidth() / 2 - middleGap / 2 - buttonWidth * 3;
		addAmountButton("-100", left, buttonY, buttonWidth, -100, index, amountLabel);
		addAmountButton("-10", left + buttonWidth, buttonY, buttonWidth, -10, index, amountLabel);
		addAmountButton("-1", left + buttonWidth * 2, buttonY, buttonWidth, -1, index, amountLabel);
		int right = conditionConfigForm.getWidth() / 2 + middleGap / 2;
		addAmountButton("+1", right, buttonY, buttonWidth, 1, index, amountLabel);
		addAmountButton("+10", right + buttonWidth, buttonY, buttonWidth, 10, index, amountLabel);
		addAmountButton("+100", right + buttonWidth * 2, buttonY, buttonWidth, 100, index, amountLabel);
		flow.next(12);

		FormLocalTextButton back = conditionConfigForm.addComponent(flow.nextY(new FormLocalTextButton(
				new StaticMessage(Localization.translate("ui", "backbutton")),
				4,
				0,
				conditionConfigForm.getWidth() - 8,
				FormInputSize.SIZE_32_TO_40,
				ButtonColor.BASE
		), 4));
		back.onClicked(e -> {
			updateBoard();
			makeCurrent(boardForm);
		});
		conditionConfigForm.setHeight(flow.next() + 4);
		onWindowResized(WindowManager.getWindow());
	}

	private void addAmountButton(String text, int x, int y, int width, int delta, int index, FormLabel amountLabel) {
		FormTextButton button = conditionConfigForm.addComponent(new FormTextButton(
				text,
				x,
				y,
				width,
				FormInputSize.SIZE_24,
				delta < 0 ? ButtonColor.RED : ButtonColor.GREEN
		));
		button.acceptMouseRepeatEvents = true;
		button.onClicked(e -> {
			CraftingTask current = taskContainer.boardEntity.getTask(index);
			if (current == null) {
				return;
			}
			int amount = max(0, Math.min(65535, current.amount + delta));
			taskContainer.updateTask(index, current.conditionType, amount);
			amountLabel.setText(Integer.toString(amount));
		});
	}

	private String getConditionName(int type, String amount) {
		return Localization.translate(
				"ui",
				type == CraftingTask.CONDITION_KEEP_STOCKED ? "keepunitsstocked" : "craftunits",
				"amount",
				amount
		);
	}

	@Override
	protected void init() {
		super.init();
		CraftingStationLinkHud.setOpenTaskBoard(
				client.getLevel(),
				taskContainer.boardEntity.tileX,
				taskContainer.boardEntity.tileY
		);
	}

	@Override
	public void onWindowResized(GameWindow window) {
		super.onWindowResized(window);
		boardForm.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
		itemSelectForm.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
		conditionConfigForm.setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
	}

	@Override
	public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
		if (isCurrent(boardForm) && getSignature() != lastSignature) {
			updateBoard();
		}
		super.draw(tickManager, perspective, renderBox);
	}

	@Override
	public boolean shouldOpenInventory() {
		return false;
	}

	@Override
	public boolean shouldShowToolbar() {
		return false;
	}

	@Override
	public void dispose() {
		CraftingStationLinkHud.clearOpenTaskBoard(
				client.getLevel(),
				taskContainer.boardEntity.tileX,
				taskContainer.boardEntity.tileY
		);
		super.dispose();
	}

	private class TaskItemIcon extends FormItemIcon {
		private final int itemID;

		TaskItemIcon(int x, int y, int itemID) {
			super(x, y, new InventoryItem(ItemRegistry.getItem(itemID)), true);
			this.itemID = itemID;
		}

		@Override
		public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
			super.handleInputEvent(event, tickManager, perspective);
			if (!event.isUsed() && event.state && event.getID() == -100 && isMouseOver(event)) {
				taskContainer.addTask(itemID);
				updateBoard();
				makeCurrent(boardForm);
				if (event.shouldSubmitSound()) {
					playTickSound();
				}
				event.use();
			}
		}

		@Override
		public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
			if (isHovering()) {
				getInterfaceStyle().inventoryslot_small.highlighted.initDraw().draw(getX(), getY());
			} else {
				getInterfaceStyle().inventoryslot_small.active.initDraw().draw(getX(), getY());
			}
			super.draw(tickManager, perspective, renderBox);
		}
	}

	private class TaskRow extends Form {
		private final int index;
		private CraftingTask task;
		private FormFairTypeLabel conditionLabel;

		TaskRow(int width, int index, CraftingTask task) {
			super(width, 44);
			this.index = index;
			this.task = task;
			drawBase = false;
			shouldLimitDrawArea = false;
			build();
		}

		private void build() {
			FormContentIconButton up = addComponent(new FormContentIconButton(
					4, 5, FormInputSize.SIZE_16, ButtonColor.BASE,
					getInterfaceStyle().button_expanded_16,
					new GameMessage[]{new StaticMessage(Localization.translate("ui", "moveup"))}
			));
			up.mirrorY();
			up.setActive(index > 0);
			up.onClicked(e -> {
				taskContainer.moveTask(index, index - 1);
				updateBoard();
			});

			FormContentIconButton down = addComponent(new FormContentIconButton(
					4, 23, FormInputSize.SIZE_16, ButtonColor.BASE,
					getInterfaceStyle().button_expanded_16,
					new GameMessage[]{new StaticMessage(Localization.translate("ui", "movedown"))}
			));
			down.setActive(index < taskContainer.boardEntity.getTaskCount() - 1);
			down.onClicked(e -> {
				taskContainer.moveTask(index, index + 1);
				updateBoard();
			});

			Item item = ItemRegistry.getItem(task.itemID);
			if (item != null) {
				InventoryItem inventoryItem = new InventoryItem(item);
				int nameX = 28;
				FontOptions itemOptions = new FontOptions(20);
				FairType itemFairType = new FairType();
				itemFairType.append(new FairItemGlyph(16, inventoryItem).onlyShowNameTooltip());
				itemFairType.append(itemOptions, " " + inventoryItem.getItemDisplayName());
				FormFairTypeLabel itemLabel = new FormFairTypeLabel(
						new StaticMessage(""),
						itemOptions,
						FairType.TextAlign.LEFT,
						nameX,
						getHeight() / 2 - 10
				);
				itemLabel.setCustomFairType(itemFairType);
				itemLabel.setMax(ITEM_WIDTH - nameX - 4, 1, true, true);
				addComponent(itemLabel);
			}

			int conditionX = ITEM_WIDTH;
			FormContentIconButton change = addComponent(new FormContentIconButton(
					conditionX + 18,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.BASE,
					getInterfaceStyle().button_collapsed_24,
					new GameMessage[]{new StaticMessage(Localization.translate("ui", "changebutton"))}
			));
			change.onClicked(e -> {
				SelectionFloatMenu menu = new SelectionFloatMenu(change, SelectionFloatMenu.Solid(new FontOptions(12)), 210);
				menu.add(Localization.translate("ui", "craftxunits"), () -> {
					CraftingTask current = taskContainer.boardEntity.getTask(index);
					if (current != null) taskContainer.updateTask(index, CraftingTask.CONDITION_CRAFT_UNITS, current.amount);
					menu.remove();
				});
				menu.add(Localization.translate("ui", "keepxunitsstocked"), () -> {
					CraftingTask current = taskContainer.boardEntity.getTask(index);
					if (current != null) taskContainer.updateTask(index, CraftingTask.CONDITION_KEEP_STOCKED, current.amount);
					menu.remove();
				});
				if (e.event.isControllerEvent()) getManager().openFloatMenu(menu);
				else getManager().openFloatMenu(menu, e.from, e.event, -4, 0);
			});

			FormContentIconButton config = addComponent(new FormContentIconButton(
					conditionX + 46,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.BASE,
					getInterfaceStyle().container_storage_config,
					new GameMessage[]{new StaticMessage(Localization.translate("ui", "configurebutton"))}
			));
			config.onClicked(e -> {
				setupConditionConfig(index);
				makeCurrent(conditionConfigForm);
			});

			conditionLabel = addComponent(new FormFairTypeLabel(
					new StaticMessage(""),
					new FontOptions(16),
					FairType.TextAlign.LEFT,
					conditionX + 80,
					13
			));
			conditionLabel.setMax(CONDITION_WIDTH - 88, 1, true, true);
			updateConditionLabel();

			int statusX = ITEM_WIDTH + CONDITION_WIDTH;
			ButtonIcon statusIcon;
			String statusTooltip;
			switch (task.status) {
				case CraftingTask.STATUS_PAUSED:
					statusIcon = new ButtonIcon(getInterfaceStyle(), "pause_song", new Color(145, 145, 145));
					statusTooltip = Localization.translate("ui", "statuspaused");
					break;
				case CraftingTask.STATUS_PROBLEM:
					statusIcon = new ButtonIcon(getInterfaceStyle(), "settlement_error_icon", new Color(225, 55, 55));
					statusTooltip = task.problemDetails.isEmpty() ? Localization.translate("ui", "statusproblem") : String.join("\n", task.problemDetails);
					break;
				case CraftingTask.STATUS_IN_PROGRESS:
					statusIcon = new ButtonIcon(getInterfaceStyle(), "rotate_clockwise_32", new Color(70, 145, 235));
					statusTooltip = Localization.translate("ui", "statusinprogress");
					break;
				default:
					statusIcon = new ButtonIcon(getInterfaceStyle(), "button_checked_20", new Color(55, 190, 70));
					statusTooltip = Localization.translate("ui", "statusfinished");
					break;
			}

			FormContentIconButton status = addComponent(new FormContentIconButton(
					statusX + STATUS_WIDTH / 2 - 12,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.BASE,
					statusIcon,
					new GameMessage[]{new StaticMessage(statusTooltip)}
			));

			int actionX = statusX + STATUS_WIDTH;
			FormContentIconButton pause = addComponent(new FormContentIconButton(
					actionX + 4,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.BASE,
					task.paused ? getInterfaceStyle().play_song : getInterfaceStyle().pause_song,
					new GameMessage[]{new StaticMessage(Localization.translate("ui", task.paused ? "resumebutton" : "pausebutton"))}
			));
			pause.onClicked(e -> {
				CraftingTask current = taskContainer.boardEntity.getTask(index);
				if (current != null) {
					taskContainer.setTaskPaused(index, !current.paused);
					updateBoard();
				}
			});

			FormContentIconButton delete = addComponent(new FormContentIconButton(
					actionX + 40,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.RED,
					getInterfaceStyle().container_storage_remove,
					new GameMessage[]{new StaticMessage(Localization.translate("ui", "deletebutton"))}
			));
			delete.onClicked(e -> {
				taskContainer.deleteTask(index);
				updateBoard();
			});
		}

		private void updateConditionLabel() {
			CraftingTask current = taskContainer.boardEntity.getTask(index);
			if (current == null) {
				return;
			}
			task = current;
			FontOptions options = conditionLabel.getFontOptions();
			FairType fairType = new FairType();
			String prefix = Localization.translate(
					"ui",
					task.conditionType == CraftingTask.CONDITION_KEEP_STOCKED ? "keepword" : "craftword"
			);
			String suffix = Localization.translate(
					"ui",
					task.conditionType == CraftingTask.CONDITION_KEEP_STOCKED ? "unitsstocked" : "units"
			);
			fairType.append(options, prefix + " ");
			fairType.append(createAmountGlyph(-1));
			fairType.append(new FairAmountGlyph(options));
			fairType.append(createAmountGlyph(1));
			fairType.append(options, " " + suffix);
			conditionLabel.setCustomFairType(fairType);
		}

		private class FairAmountGlyph implements FairGlyph {
			private final FontOptions options;
			private final FloatDimension dimensions;

			private FairAmountGlyph(FontOptions options) {
				this.options = options;
				this.dimensions = new FloatDimension(52.0F, FontManager.bit.getHeight("65535", options));
			}

			@Override
			public FloatDimension getDimensions() {
				return dimensions;
			}

			@Override
			public void updateDimensions() {
			}

			@Override
			public void handleInputEvent(float drawX, float drawY, InputEvent event) {
			}

			@Override
			public void draw(float x, float y, Color defaultColor) {
				CraftingTask current = taskContainer.boardEntity.getTask(index);
				String text = current == null ? "0" : Integer.toString(current.amount);
				options.defaultColor(defaultColor);
				float textWidth = FontManager.bit.getWidth(text, options);
				FontManager.bit.drawStringNoShadow(
						x + (dimensions.width - textWidth) / 2.0F,
						y - dimensions.height,
						text,
						options
				);
			}

			@Override
			public FairGlyph getTextBoxCharacter() {
				return this;
			}
		}

		private FairButtonGlyph createAmountGlyph(int direction) {
			return new FairButtonGlyph(16, 16) {
				private boolean hovering;

				@Override
				public void handleInputEvent(float drawX, float drawY, InputEvent event) {
					Rectangle hitbox = new Rectangle((int)drawX + 2, (int)drawY - height - 2, width, height);
					hovering = hitbox.contains(event.pos.hudX, event.pos.hudY);
					if (hovering) handleEvent(drawX, drawY, event);
				}

				@Override
				public void handleEvent(float drawX, float drawY, InputEvent event) {
					if ((event.getID() == -100 || event.isRepeatEvent(conditionLabel)) && event.state) {
						event.startRepeatEvents(conditionLabel);
						int amount = 1;
						if (Control.INV_QUICK_MOVE.isDown()) amount = 10;
						else if (Control.INV_QUICK_TRASH.isDown() || Control.INV_QUICK_DROP.isDown()) amount = 100;

						CraftingTask current = taskContainer.boardEntity.getTask(index);
						if (current == null) return;
						int next = max(0, Math.min(65535, current.amount + direction * amount));
						if (next != current.amount) {
							taskContainer.updateTask(index, current.conditionType, next);
							updateConditionLabel();
							if (event.shouldSubmitSound()) conditionLabel.playTickSound();
						}
					}
				}

				@Override
				public void draw(float x, float y, Color defaultColor) {
					ButtonState state = hovering ? ButtonState.HIGHLIGHTED : ButtonState.ACTIVE;
					Color color = direction < 0
							? getInterfaceStyle().button_minus_20.colorGetter.apply(state)
							: getInterfaceStyle().button_plus_20.colorGetter.apply(state);
					if (direction < 0) getInterfaceStyle().button_minus_20.texture.initDraw().color(color).posMiddle((int)x + 8, (int)y - 8).draw();
					else getInterfaceStyle().button_plus_20.texture.initDraw().color(color).posMiddle((int)x + 8, (int)y - 8).draw();
					if (hovering) Renderer.setCursor(GameWindow.CURSOR.INTERACT);
				}
			};
		}
	}
}
