package opusliews.stock;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.window.WindowManager;
import necesse.engine.registries.ItemRegistry;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormTextInput;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.ItemCategoriesFilterForm;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementStorageConfigForm;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.item.Item;
import necesse.inventory.itemFilter.ItemCategoriesFilter;
import opusliews.network.PacketSettlementStockRequest;
import opusliews.network.PacketSettlementStockUpdate;
import opusliews.logging.Logging;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class SettlementStockUI {
	public static final int extraWidth = 240;
	private static final int inputWidth = 106;
	private static final int columnGap = 4;
	private static final int rightPadding = 20;
	private static final String clipboardStockKey = "dynamicStockClipboard";
	private static final String clipboardStockEntryKey = "item";
	private static final WeakHashMap<SettlementStorageConfigForm, FormState> forms = new WeakHashMap<>();
	private static final WeakHashMap<Form, Integer> advancedRowWidths = new WeakHashMap<>();

	private SettlementStockUI() {
	}

	public static synchronized void attach(SettlementStorageConfigForm form) {
		if (form == null || form.isDisposed() || forms.containsKey(form)) return;
		if (Logging.logEnabled) Logging.logMessage("[StockUI] attach tile=" + form.tile.x + "," + form.tile.y + " filterForm=" + form.filterForm);
		FormState state = new FormState(form);
		forms.put(form, state);
		state.attach();
		form.client.network.sendPacket(new PacketSettlementStockRequest(form.tile.x, form.tile.y));
	}

	public static synchronized void detach(SettlementStorageConfigForm form) {
		FormState state = forms.remove(form);
		if (state == null) return;
		for (ItemControls itemControls : state.controls.values()) advancedRowWidths.remove(itemControls.row);
		state.controls.clear();
		state.targets.clear();
	}

	public static synchronized void applySync(int tileX, int tileY, Map<Integer, Integer> targets) {
		for (Map.Entry<SettlementStorageConfigForm, FormState> entry : new ArrayList<>(forms.entrySet())) {
			SettlementStorageConfigForm form = entry.getKey();
			if (form == null) continue;
			if (form.isDisposed()) {
				detach(form);
				continue;
			}
			if (form.tile.x != tileX || form.tile.y != tileY) continue;
			entry.getValue().setTargets(targets);
		}
	}

	public static synchronized void tickForm(SettlementStorageConfigForm form) {
		if (form.isDisposed()) {
			detach(form);
			return;
		}
		FormState state = forms.get(form);
		if (state == null) return;
		state.enforceLocalMax();
		state.refreshVisibility();
	}

	public static synchronized void restoreAdvancedRowWidth(Form row) {
		Integer requiredWidth = advancedRowWidths.get(row);
		if (requiredWidth != null && row.getWidth() < requiredWidth) {
			if (Logging.logEnabled) Logging.logMessage("[StockUI] restoring ItemForm width from " + row.getWidth() + " to " + requiredWidth);
			row.setWidth(requiredWidth);
		}
	}

	private static int getRequiredMax(ItemCategoriesFilter filter, Map<Integer, Integer> targets) {
		long value = 0;
		switch (filter.limitMode) {
			case TOTAL_ITEMS:
				for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null || !filter.isItemAllowed(item)) continue;
					value += Math.max(0, entry.getValue());
				}
				break;
			case TOTAL_EACH_ITEM:
				for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null || !filter.isItemAllowed(item)) continue;
					value = Math.max(value, Math.max(0, entry.getValue()));
				}
				break;
			case TOTAL_STACKS:
				for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null || !filter.isItemAllowed(item)) continue;
					int stackSize = Math.max(1, item.getStackSize());
					value += (Math.max(0, entry.getValue()) + stackSize - 1L) / stackSize;
				}
				break;
			case TOTAL_STACKS_EACH_ITEM:
				for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null || !filter.isItemAllowed(item)) continue;
					int stackSize = Math.max(1, item.getStackSize());
					long stacks = (Math.max(0, entry.getValue()) + stackSize - 1L) / stackSize;
					value = Math.max(value, stacks);
				}
				break;
		}
		return (int)Math.min(Integer.MAX_VALUE, value);
	}

	private static final class FormState {
		private final SettlementStorageConfigForm form;
		private final LinkedHashMap<Integer, Integer> targets = new LinkedHashMap<>();
		private final LinkedHashMap<Integer, ItemControls> controls = new LinkedHashMap<>();
		private boolean manualAdvanced;
		private boolean lastVisible;
		private boolean visibilityInitialized;

		private FormState(SettlementStorageConfigForm form) {
			this.form = form;
		}

		private void attach() {
			try {
				Form mainForm = (Form)getFieldValue(form, "mainForm");
				if (Logging.logEnabled) Logging.logMessage("[StockUI] FormState.attach mainForm=" + mainForm + " width=" + (mainForm == null ? -1 : mainForm.getWidth()));
				if (mainForm != null) {
					FormLocalTextButton advanced = mainForm.addComponent(new FormLocalTextButton(
							new LocalMessage("ui", "storageadvanced"),
							mainForm.getWidth() - 104,
							4,
							100,
							FormInputSize.SIZE_20,
							ButtonColor.BASE
					));
					advanced.onClicked(event -> {
						manualAdvanced = !manualAdvanced;
						if (Logging.logEnabled) Logging.logMessage("[StockUI] Advanced clicked visible=" + manualAdvanced + " controls=" + controls.size());
						refreshVisibility();
					});
				}
				installClipboardHooks(mainForm);
				addItemControls();
				refreshVisibility();
			} catch (Exception exception) {
				Logging.logMessage("[StockUI] attach failed: " + exception.getClass().getName() + ": " + exception.getMessage());
				exception.printStackTrace();
			}
		}

		private void installClipboardHooks(Form mainForm) {
			if (mainForm == null) return;

			try {
				FormContentIconButton pasteButton = (FormContentIconButton)getFieldValue(form, "pasteButton");
				FormContentIconButton copyButton = findCopyButton(mainForm, pasteButton);
				if (copyButton == null || pasteButton == null) {
					Logging.logMessage("[StockUI] Could not extend storage clipboard buttons tile=" + form.tile.x + "," + form.tile.y
							+ " copy=" + (copyButton != null) + " paste=" + (pasteButton != null));
					return;
				}

				copyButton.onClicked(event -> copyExtendedConfig());
				pasteButton.onClicked(event -> pasteStockConfig());
				if (Logging.logEnabled) Logging.logMessage("[StockUI] Extended storage Copy/Paste with stock settings tile=" + form.tile.x + "," + form.tile.y);
			} catch (Exception exception) {
				Logging.logMessage("[StockUI] Failed to extend storage Copy/Paste tile=" + form.tile.x + "," + form.tile.y
						+ " error=" + exception.getClass().getName() + ": " + exception.getMessage());
			}
		}

		private FormContentIconButton findCopyButton(Form mainForm, FormContentIconButton pasteButton) {
			if (mainForm == null || pasteButton == null) return null;
			int expectedX = pasteButton.getX() - 28;
			int expectedY = pasteButton.getY();

			for (Object componentObject : mainForm.getComponentList()) {
				if (!(componentObject instanceof FormContentIconButton)) continue;
				FormContentIconButton button = (FormContentIconButton)componentObject;
				if (button.getX() == expectedX && button.getY() == expectedY) return button;
			}
			return null;
		}

		private void copyExtendedConfig() {
			try {
				SaveData save = new SaveData("config");
				save.addInt("priority", (Integer)form.prioritySelect.getSelected());

				SaveData filterSave = new SaveData("filter");
				form.filter.addSaveData(filterSave);
				save.addSaveData(filterSave);

				SaveData stockSave = new SaveData(clipboardStockKey);
				stockSave.addInt("version", 2);
				for (Map.Entry<Integer, Integer> entry : targets.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					int stock = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
					if (item == null || stock <= 0) continue;

					SaveData itemSave = new SaveData(clipboardStockEntryKey);
					itemSave.addItemStringID("id", item.getStringID());
					itemSave.addInt("stock", stock);
					itemSave.addBoolean("allowed", form.filter.isItemAllowed(item));
					stockSave.addSaveData(itemSave);
				}
				save.addSaveData(stockSave);

				WindowManager.getWindow().putClipboard(save.getScript());
				if (Logging.logEnabled) Logging.logMessage("[StockUI] Copied storage config with stock settings tile=" + form.tile.x + "," + form.tile.y
						+ " stockTargets=" + targets.size());
			} catch (Exception exception) {
				Logging.logMessage("[StockUI] Failed copying stock settings tile=" + form.tile.x + "," + form.tile.y
						+ " error=" + exception.getClass().getName() + ": " + exception.getMessage());
			}
		}

		private void pasteStockConfig() {
			try {
				String clipboard = WindowManager.getWindow().getClipboard();
				if (clipboard == null || clipboard.isEmpty()) return;

				LoadData root = new LoadData(clipboard);
				LoadData stockData = root.getFirstLoadDataByName(clipboardStockKey);
				if (stockData == null) {
					if (Logging.logEnabled) Logging.logMessage("[StockUI] Pasted legacy storage config without stock settings tile=" + form.tile.x + "," + form.tile.y);
					return;
				}

				int clipboardVersion = stockData.getInt("version", 1, false);
				LinkedHashMap<Integer, Integer> nextTargets = new LinkedHashMap<>();
				LinkedHashMap<Integer, Boolean> copiedAllowedStates = new LinkedHashMap<>();
				for (Object dataObject : stockData.getLoadDataByName(clipboardStockEntryKey)) {
					LoadData itemData = (LoadData)dataObject;
					String stringID = itemData.getItemStringID("id", null, false);
					Item item = stringID == null ? null : ItemRegistry.getItem(stringID);
					if (item == null) continue;
					int stock = Math.max(0, itemData.getInt("stock", 0, false));
					if (stock > 0) nextTargets.put(item.getID(), stock);
					if (clipboardVersion >= 2) copiedAllowedStates.put(item.getID(), itemData.getBoolean("allowed", true, false));
				}

				LinkedHashMap<Integer, Integer> previousTargets = new LinkedHashMap<>(targets);
				LinkedHashMap<Integer, Boolean> changedItemIDs = new LinkedHashMap<>();
				for (int itemID : previousTargets.keySet()) changedItemIDs.put(itemID, true);
				for (int itemID : nextTargets.keySet()) changedItemIDs.put(itemID, true);

				setTargets(nextTargets);

				for (Map.Entry<Integer, Boolean> entry : copiedAllowedStates.entrySet()) {
					Item item = ItemRegistry.getItem(entry.getKey());
					if (item == null) continue;
					boolean allowed = entry.getValue();

					form.filter.setItemAllowed(item, allowed);
					form.filterForm.updateButton(item.getID());
					form.onItemsChanged(new Item[]{item}, allowed);
					if (Logging.logEnabled) Logging.logMessage("[StockUI] Forced copied stock allowed state item="
							+ item.getStringID() + " allowed=" + allowed + " tile=" + form.tile.x + "," + form.tile.y);
				}

				for (int itemID : changedItemIDs.keySet()) {
					int previous = Math.max(0, previousTargets.getOrDefault(itemID, 0));
					int next = Math.max(0, nextTargets.getOrDefault(itemID, 0));
					if (previous == next) continue;
					form.client.network.sendPacket(new PacketSettlementStockUpdate(
							form.tile.x,
							form.tile.y,
							itemID,
							next
					));
				}

				if (Logging.logEnabled) Logging.logMessage("[StockUI] Pasted storage config stock settings tile=" + form.tile.x + "," + form.tile.y
						+ " previousTargets=" + previousTargets.size() + " nextTargets=" + nextTargets.size());
			} catch (Exception exception) {
				Logging.logMessage("[StockUI] Failed pasting stock settings tile=" + form.tile.x + "," + form.tile.y
						+ " error=" + exception.getClass().getName() + ": " + exception.getMessage());
			}
		}

		private void addItemControls() throws Exception {
			Field itemFormsField = ItemCategoriesFilterForm.class.getDeclaredField("itemForms");
			itemFormsField.setAccessible(true);
			Map<?, ?> itemForms = (Map<?, ?>)itemFormsField.get(form.filterForm);
			if (Logging.logEnabled) Logging.logMessage("[StockUI] itemForms=" + (itemForms == null ? "null" : itemForms.size()));
			if (itemForms == null) return;

			int inspected = 0;
			int added = 0;
			for (Object row : itemForms.values()) {
				inspected++;
				Field itemsField = row.getClass().getDeclaredField("items");
				itemsField.setAccessible(true);
				Item[] items = (Item[])itemsField.get(row);
				if (items == null || items.length != 1 || !(row instanceof Form)) {
					if (Logging.logEnabled && inspected <= 10) Logging.logMessage("[StockUI] skipping row class=" + row.getClass().getName() + " items=" + (items == null ? "null" : items.length) + " isForm=" + (row instanceof Form));
					continue;
				}
				Item item = items[0];
				Form rowForm = (Form)row;
				int ancestorOffset = getAncestorX(row);
				int stockGlobalX = formWidth() - inputWidth - rightPadding;
				int restockGlobalX = stockGlobalX - inputWidth - columnGap;
				int stockLocalX = Math.max(0, stockGlobalX - ancestorOffset);
				int restockLocalX = Math.max(0, restockGlobalX - ancestorOffset);

				Form restockWrapper = rowForm.addComponent(new Form("stockRestock", inputWidth, 20));
				restockWrapper.drawBase = false;
				restockWrapper.drawEdge = false;
				restockWrapper.setPosition(restockLocalX, 0);
				FormTextInput restockInput = restockWrapper.addComponent(new FormTextInput(
						0,
						0,
						FormInputSize.SIZE_20,
						inputWidth,
						7
				));
				restockInput.placeHolder = new LocalMessage("ui", "storagerestock");
				restockInput.setActive(false);

				Form stockWrapper = rowForm.addComponent(new Form("stockTarget", inputWidth, 20));
				stockWrapper.drawBase = false;
				stockWrapper.drawEdge = false;
				stockWrapper.setPosition(stockLocalX, 0);
				FormTextInput stockInput = stockWrapper.addComponent(new FormTextInput(
						0,
						0,
						FormInputSize.SIZE_20,
						inputWidth,
						7
				));
				stockInput.placeHolder = new LocalMessage("ui", "storagestock");
				stockInput.setRegexMatchFull("([0-9]+)?");
				stockInput.rightClickToClear = true;
				stockInput.onSubmit(event -> submitStock(item, stockInput));

				int advancedRowWidth = Math.max(0, formWidth() - 8 - ancestorOffset);
				controls.put(item.getID(), new ItemControls(rowForm, advancedRowWidth, restockWrapper, stockWrapper, restockInput, stockInput));
				added++;
				if (Logging.logEnabled && added <= 10) Logging.logMessage("[StockUI] added controls item=" + item.getStringID() + " rowX=" + rowForm.getX() + " rowWidth=" + rowForm.getWidth() + " ancestorOffset=" + ancestorOffset + " restockX=" + restockLocalX + " stockX=" + stockLocalX + " advancedWidth=" + advancedRowWidth);
			}
			if (Logging.logEnabled) Logging.logMessage("[StockUI] addItemControls complete inspected=" + inspected + " added=" + added + " controls=" + controls.size());
		}

		private void submitStock(Item item, FormTextInput input) {
			int stock;
			try {
				stock = input.getText().isEmpty() ? 0 : Math.max(0, Integer.parseInt(input.getText()));
			} catch (NumberFormatException exception) {
				refreshControl(item.getID());
				return;
			}

			if (stock <= 0) targets.remove(item.getID());
			else targets.put(item.getID(), stock);

			if (stock > 0 && !form.filter.isItemAllowed(item)) {
				form.filter.setItemAllowed(item, true);
				form.filterForm.updateButton(item.getID());
				form.onItemsChanged(new Item[]{item}, true);
			}

			refreshControl(item.getID());
			refreshVisibility();
			enforceLocalMax();
			form.client.network.sendPacket(new PacketSettlementStockUpdate(
					form.tile.x,
					form.tile.y,
					item.getID(),
					stock
			));
		}

		private void setTargets(Map<Integer, Integer> nextTargets) {
			targets.clear();
			if (nextTargets != null) {
				for (Map.Entry<Integer, Integer> entry : nextTargets.entrySet()) {
					if (entry.getValue() != null && entry.getValue() > 0) targets.put(entry.getKey(), entry.getValue());
				}
			}
			for (int itemID : controls.keySet()) refreshControl(itemID);
			refreshVisibility();
			enforceLocalMax();
		}

		private void refreshControl(int itemID) {
			ItemControls itemControls = controls.get(itemID);
			if (itemControls == null) return;
			int stock = Math.max(0, targets.getOrDefault(itemID, 0));
			if (!itemControls.stockInput.isTyping()) itemControls.stockInput.setText(stock > 0 ? String.valueOf(stock) : "");
			itemControls.restockInput.setText(stock > 0 ? String.valueOf(SettlementStockSystem.getRestockThreshold(stock)) : "");
		}

		private void refreshVisibility() {
			boolean visible = manualAdvanced || targets.values().stream().anyMatch(value -> value != null && value > 0);
			boolean visibilityChanged = !visibilityInitialized || visible != lastVisible;
			visibilityInitialized = true;
			lastVisible = visible;

			for (ItemControls itemControls : controls.values()) {
				if (visibilityChanged) {
					itemControls.restockWrapper.setHidden(!visible);
					itemControls.stockWrapper.setHidden(!visible);
					if (visible) advancedRowWidths.put(itemControls.row, itemControls.advancedRowWidth);
					else advancedRowWidths.remove(itemControls.row);
				}

				if (visible) {
					itemControls.row.setWidth(Math.max(itemControls.row.getWidth(), itemControls.advancedRowWidth));
					expandParentWidths(itemControls.row);
				}
			}

			if (visible) form.filterForm.updateDimensions();
			if (visibilityChanged && Logging.logEnabled) {
				Logging.logMessage("[StockUI] refreshVisibility visible=" + visible + " manualAdvanced=" + manualAdvanced + " targets=" + targets.size() + " controls=" + controls.size() + " filterWidth=" + form.filterForm.getWidth());
				int logged = 0;
				for (Map.Entry<Integer, ItemControls> entry : controls.entrySet()) {
					if (logged++ >= 5) break;
					ItemControls itemControls = entry.getValue();
					Item item = ItemRegistry.getItem(entry.getKey());
					Logging.logMessage("[StockUI] row state item=" + (item == null ? entry.getKey() : item.getStringID()) + " rowWidth=" + itemControls.row.getWidth() + " requiredWidth=" + itemControls.advancedRowWidth + " restockHidden=" + itemControls.restockWrapper.isHidden() + " stockHidden=" + itemControls.stockWrapper.isHidden() + " restockX=" + itemControls.restockWrapper.getX() + " stockX=" + itemControls.stockWrapper.getX());
				}
			}
		}

		private void expandParentWidths(Form row) {
			Object child = row;
			while (child instanceof Form) {
				try {
					Field parentField = findField(child.getClass(), "parent");
					if (parentField == null) return;
					parentField.setAccessible(true);
					Object parentObject = parentField.get(child);
					if (!(parentObject instanceof Form)) return;

					Form childForm = (Form)child;
					Form parentForm = (Form)parentObject;
					int requiredWidth = childForm.getX() + childForm.getWidth();
					if (parentForm.getWidth() < requiredWidth) parentForm.setWidth(requiredWidth);
					child = parentForm;
				} catch (Exception exception) {
					return;
				}
			}
		}

		private void enforceLocalMax() {
			if (form.limitInput.isTyping() || form.filter.maxAmount == Integer.MAX_VALUE) return;
			int required = getRequiredMax(form.filter, targets);
			if (required <= 0 || form.filter.maxAmount >= required) return;
			form.filter.maxAmount = required;
			form.updateLimitInput();
			form.onLimitChange(form.filter.limitMode, required);
		}

		private int formWidth() {
			try {
				Form mainForm = (Form)getFieldValue(form, "mainForm");
				return mainForm == null ? 740 : mainForm.getWidth();
			} catch (Exception exception) {
				return 740;
			}
		}
	}

	private static int getAncestorX(Object row) {
		int x = 0;
		Object current = row;
		while (current != null) {
			if (current instanceof Form) x += ((Form)current).getX();
			try {
				Field parent = findField(current.getClass(), "parent");
				if (parent == null) break;
				parent.setAccessible(true);
				current = parent.get(current);
			} catch (Exception exception) {
				break;
			}
		}
		return x;
	}

	private static Object getFieldValue(Object object, String name) throws Exception {
		Field field = findField(object.getClass(), name);
		if (field == null) return null;
		field.setAccessible(true);
		return field.get(object);
	}

	private static Field findField(Class<?> type, String name) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
			}
		}
		return null;
	}

	private static final class ItemControls {
		private final Form row;
		private final int advancedRowWidth;
		private final Form restockWrapper;
		private final Form stockWrapper;
		private final FormTextInput restockInput;
		private final FormTextInput stockInput;

		private ItemControls(Form row, int advancedRowWidth, Form restockWrapper, Form stockWrapper, FormTextInput restockInput, FormTextInput stockInput) {
			this.row = row;
			this.advancedRowWidth = advancedRowWidth;
			this.restockWrapper = restockWrapper;
			this.stockWrapper = stockWrapper;
			this.restockInput = restockInput;
			this.stockInput = stockInput;
		}
	}
}
