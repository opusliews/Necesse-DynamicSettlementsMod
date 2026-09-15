package opusliews.forms;

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
import necesse.gfx.ui.ButtonState;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;
import necesse.inventory.recipe.Recipe;
import necesse.level.gameObject.container.CraftingStationObject;
import necesse.level.maps.LevelObject;
import opusliews.container.AnvilCraftingTaskBoardContainer;
import opusliews.crafting.AnvilCraftingTask;
import opusliews.hud.IronAnvilLinkHud;

import java.awt.*;
import java.util.List;
import java.util.*;

public class AnvilCraftingTaskBoardContainerForm extends ContainerFormSwitcher {
	private static final int ITEM_WIDTH = 400;
	private static final int CONDITION_WIDTH = 400;
	private static final int CONFIG_WIDTH = 44;
	private static final int FORM_WIDTH = ITEM_WIDTH + CONDITION_WIDTH + CONFIG_WIDTH;

	private final Client client;
	private final AnvilCraftingTaskBoardContainer taskContainer;
	private final Form boardForm;
	private final FormContentBox content;
	private final Form itemSelectForm;
	private final Form conditionConfigForm;

	private int lastSignature = Integer.MIN_VALUE;
	private int currentConfigIndex = -1;
	private final HashSet<String> collapsedPickerCategories = new HashSet<>();

	public AnvilCraftingTaskBoardContainerForm(Client client, AnvilCraftingTaskBoardContainer container) {
		super(client, container);
		this.client = client;
		this.taskContainer = container;

		boardForm = (Form)addComponent(new Form("anvilCraftingTasks", FORM_WIDTH, 400));
		FormFlow flow = new FormFlow(10);
		boardForm.addComponent(flow.nextY(new FormLabel(
				"Anvil Crafting Tasks",
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
				new GameMessage[]{new StaticMessage("Close")}
		)).onClicked(e -> client.closeContainer(true));

		int contentY = flow.next();
		content = boardForm.addComponent(new FormContentBox(0, contentY, boardForm.getWidth(), boardForm.getHeight() - contentY));
		itemSelectForm = (Form)addComponent(new Form("anvilTaskItemSelect", 0, 0));
		conditionConfigForm = (Form)addComponent(new Form("anvilTaskConditionConfig", 0, 0));

		updateBoard();
		makeCurrent(boardForm);
		onWindowResized(WindowManager.getWindow());
	}

	private int getSignature() {
		int result = taskContainer.hasValidLinkedAnvil() ? 1 : 0;
		List<AnvilCraftingTask> tasks = taskContainer.boardEntity.getTasks();
		result = 31 * result + tasks.size();
		for (AnvilCraftingTask task : tasks) {
			result = 31 * result + task.itemID;
			result = 31 * result + task.conditionType;
			result = 31 * result + task.amount;
		}
		return result;
	}

	private void updateBoard() {
		lastSignature = getSignature();
		content.clearComponents();

		if (!taskContainer.hasValidLinkedAnvil()) {
			FormFlow flow = new FormFlow(60);
			content.addComponent(flow.nextY(new FormLabel(
					"This board is not linked to an anvil",
					new FontOptions(20),
					0,
					content.getWidth() / 2,
					0
			), 8));
			content.addComponent(flow.nextY(new FormLabel(
					"Make the link from an anvil first",
					new FontOptions(16),
					0,
					content.getWidth() / 2,
					0
			), 8));
			content.setContentBox(new Rectangle(content.getWidth(), Math.max(content.getHeight(), flow.next())));
			return;
		}

		FormFlow flow = new FormFlow(5);
		int headerY = flow.next(28);
		FontOptions headerOptions = new FontOptions(20);
		content.addComponent(new FormLabel("Item", headerOptions, 0, ITEM_WIDTH / 2, headerY + 2));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, ITEM_WIDTH, 0, 0, false));
		content.addComponent(new FormLabel("Condition", headerOptions, 0, ITEM_WIDTH + CONDITION_WIDTH / 2, headerY + 2));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, ITEM_WIDTH + CONDITION_WIDTH, 0, 0, false));
		content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 4, flow.next(), content.getWidth() - 8, true));
		flow.next(4);

		List<AnvilCraftingTask> tasks = taskContainer.boardEntity.getTasks();
		for (int i = 0; i < tasks.size(); i++) {
			if (i > 0) {
				content.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 4, flow.next(), content.getWidth() - 8, true));
				flow.next(2);
			}
			TaskRow row = new TaskRow(content.getWidth() - content.getScrollBarWidth(), i, tasks.get(i));
			content.addComponent(flow.nextY(row, 2));
		}

		int gridBottom = flow.next() + 4;
		for (Object componentObject : content.getComponents()) {
			if (componentObject instanceof FormBreakLine) {
				FormBreakLine line = (FormBreakLine)componentObject;
				if (!line.horizontal) {
					line.length = gridBottom;
				}
			}
		}

		flow.next(16);
		FormLocalTextButton addButton = content.addComponent(new FormLocalTextButton(
				new StaticMessage("Add New Task"),
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

		int fullHeight = flow.next() + 10;
		content.setContentBox(new Rectangle(content.getWidth(), fullHeight));
	}

	private void setupItemSelect() {
		itemSelectForm.clearComponents();
		itemSelectForm.setWidth(684);
		itemSelectForm.setHeight(420);

		itemSelectForm.addComponent(new FormLabel(
				"Select Item",
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
		searchInput.placeHolder = new StaticMessage("Search");

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
				new StaticMessage("Back"),
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

		LevelObject anvil = taskContainer.getLinkedAnvil();
		if (anvil == null || !(anvil.object instanceof CraftingStationObject)) {
			itemContent.addComponent(new FormLabel(
					"No craftable items found",
					new FontOptions(16),
					0,
					itemContent.getWidth() / 2,
					24
			));
			itemContent.setContentBox(new Rectangle(itemContent.getWidth(), 56));
			return;
		}

		CraftingStationObject station = (CraftingStationObject)anvil.object;
		int categoryDepth = Math.max(station.getCraftingCategoryDepth(), 0);
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
		int iconsPerRow = Math.max(1, (contentWidth - 28) / iconSize);

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
				populateItemSelect(itemContent, search);
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
					searching ? "No matching items" : "No craftable items found",
					new FontOptions(16),
					0,
					itemContent.getWidth() / 2,
					24
			));
			flow.next(48);
		}

		itemContent.setContentBox(new Rectangle(itemContent.getWidth(), flow.next() + 8));
	}

	private void setupConditionConfig(int index) {
		currentConfigIndex = index;
		AnvilCraftingTask task = taskContainer.boardEntity.getTask(index);
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
				new StaticMessage("Back"),
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
			AnvilCraftingTask current = taskContainer.boardEntity.getTask(index);
			if (current == null) {
				return;
			}
			int amount = Math.max(0, Math.min(65535, current.amount + delta));
			taskContainer.updateTask(index, current.conditionType, amount);
			amountLabel.setText(Integer.toString(amount));
		});
	}

	private String getConditionName(int type, String amount) {
		return type == AnvilCraftingTask.CONDITION_KEEP_STOCKED
				? "Keep " + amount + " Units Stocked"
				: "Craft " + amount + " Units";
	}

	@Override
	protected void init() {
		super.init();
		IronAnvilLinkHud.setOpenTaskBoard(
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
		IronAnvilLinkHud.clearOpenTaskBoard(
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
				taskContainer.addTaskAction.runAndSend(itemID);
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
		private AnvilCraftingTask task;
		private FormFairTypeLabel conditionLabel;

		TaskRow(int width, int index, AnvilCraftingTask task) {
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
					new GameMessage[]{new StaticMessage("Move up")}
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
					new GameMessage[]{new StaticMessage("Move down")}
			));
			down.setActive(index < taskContainer.boardEntity.getTaskCount() - 1);
			down.onClicked(e -> {
				taskContainer.moveTask(index, index + 1);
				updateBoard();
			});

			Item item = ItemRegistry.getItem(task.itemID);
			if (item != null) {
				InventoryItem inventoryItem = new InventoryItem(item);
				FontOptions itemOptions = new FontOptions(18);
				FairType itemFairType = new FairType();
				itemFairType.append(new FairItemGlyph(24, inventoryItem));
				itemFairType.append(itemOptions, " " + inventoryItem.getItemDisplayName());
				FormFairTypeLabel itemLabel = new FormFairTypeLabel(new StaticMessage(""), itemOptions, FairType.TextAlign.LEFT, 28, 11);
				itemLabel.setCustomFairType(itemFairType);
				itemLabel.setMax(ITEM_WIDTH - 36, 1, true, true);
				addComponent(itemLabel);
			}

			int conditionX = ITEM_WIDTH;
			FormContentIconButton change = addComponent(new FormContentIconButton(
					conditionX + 18,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.BASE,
					getInterfaceStyle().button_collapsed_24,
					new GameMessage[]{new StaticMessage("Change")}
			));
			change.onClicked(e -> {
				SelectionFloatMenu menu = new SelectionFloatMenu(change, SelectionFloatMenu.Solid(new FontOptions(12)), 210);
				menu.add("Craft X Units", () -> {
					AnvilCraftingTask current = taskContainer.boardEntity.getTask(index);
					if (current != null) taskContainer.updateTask(index, AnvilCraftingTask.CONDITION_CRAFT_UNITS, current.amount);
					menu.remove();
				});
				menu.add("Keep X Units Stocked", () -> {
					AnvilCraftingTask current = taskContainer.boardEntity.getTask(index);
					if (current != null) taskContainer.updateTask(index, AnvilCraftingTask.CONDITION_KEEP_STOCKED, current.amount);
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
					new GameMessage[]{new StaticMessage("Configure")}
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

			FormContentIconButton delete = addComponent(new FormContentIconButton(
					ITEM_WIDTH + CONDITION_WIDTH + 10,
					10,
					FormInputSize.SIZE_24,
					ButtonColor.RED,
					getInterfaceStyle().container_storage_remove,
					new GameMessage[]{new StaticMessage("Delete")}
			));
			delete.onClicked(e -> {
				taskContainer.deleteTaskAction.runAndSend(index);
				updateBoard();
			});
		}

		private void updateConditionLabel() {
			AnvilCraftingTask current = taskContainer.boardEntity.getTask(index);
			if (current == null) {
				return;
			}
			task = current;
			FontOptions options = conditionLabel.getFontOptions();
			FairType fairType = new FairType();
			String prefix = task.conditionType == AnvilCraftingTask.CONDITION_KEEP_STOCKED ? "Keep " : "Craft ";
			String suffix = task.conditionType == AnvilCraftingTask.CONDITION_KEEP_STOCKED ? " Units Stocked" : " Units";
			fairType.append(options, prefix);
			fairType.append(createAmountGlyph(-1));
			fairType.append(new FairAmountGlyph(options));
			fairType.append(createAmountGlyph(1));
			fairType.append(options, suffix);
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
				AnvilCraftingTask current = taskContainer.boardEntity.getTask(index);
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
				@Override
				public void handleEvent(float drawX, float drawY, InputEvent event) {
					if ((event.getID() == -100 || event.isRepeatEvent(conditionLabel)) && event.state) {
						event.startRepeatEvents(conditionLabel);
						int amount = 1;
						if (Control.INV_QUICK_MOVE.isDown()) amount = 10;
						else if (Control.INV_QUICK_TRASH.isDown() || Control.INV_QUICK_DROP.isDown()) amount = 100;

						AnvilCraftingTask current = taskContainer.boardEntity.getTask(index);
						if (current == null) return;
						int next = Math.max(0, Math.min(65535, current.amount + direction * amount));
						if (next != current.amount) {
							taskContainer.updateTask(index, current.conditionType, next);

							lastSignature = getSignature();

							if (event.shouldSubmitSound()) conditionLabel.playTickSound();
						}
					}
				}

				@Override
				public void draw(float x, float y, Color defaultColor) {
					ButtonState state = isHovering() ? ButtonState.HIGHLIGHTED : ButtonState.ACTIVE;
					Color color = direction < 0
							? getInterfaceStyle().button_minus_20.colorGetter.apply(state)
							: getInterfaceStyle().button_plus_20.colorGetter.apply(state);
					if (direction < 0) getInterfaceStyle().button_minus_20.texture.initDraw().color(color).posMiddle((int)x + 8, (int)y - 8).draw();
					else getInterfaceStyle().button_plus_20.texture.initDraw().color(color).posMiddle((int)x + 8, (int)y - 8).draw();
					if (isHovering()) Renderer.setCursor(GameWindow.CURSOR.INTERACT);
				}
			};
		}
	}
}
