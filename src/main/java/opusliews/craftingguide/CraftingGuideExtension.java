package opusliews.craftingguide;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.InputEvent;
import necesse.engine.input.controller.ControllerEvent;
import necesse.engine.input.controller.ControllerInput;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.network.packet.PacketContainerAction;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameBackground;
import necesse.gfx.Renderer;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.*;
import necesse.gfx.forms.components.containerSlot.FormContainerMaterialSlot;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.controller.ControllerNavigationHandler;
import necesse.gfx.forms.position.FormFixedPosition;
import necesse.gfx.forms.position.FormPosition;
import necesse.gfx.forms.position.FormPositionContainer;
import necesse.gfx.forms.presets.containerComponent.item.CraftingGuideContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.gameTooltips.*;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.ContainerAction;
import necesse.inventory.container.ContainerActionResult;
import necesse.inventory.container.item.CraftingGuideContainer;
import necesse.inventory.container.slots.ContainerSlot;
import necesse.inventory.item.Item;
import necesse.inventory.item.ItemCategory;
import necesse.inventory.recipe.*;
import opusliews.forge.ForgeCookingInput;
import opusliews.forge.ForgeCookingRecipe;
import opusliews.forge.ForgeCookingRecipeRegistry;
import opusliews.logging.Logging;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class CraftingGuideExtension {
    private static final Map<CraftingGuideContainerForm, GuideState> formStates = Collections.synchronizedMap(new WeakHashMap<>());

    private CraftingGuideExtension() {
    }

    public static void registerContainer(CraftingGuideContainer container) {
       // Kept for compatibility with the existing constructor patch. Real-item returns now use
       // vanilla ContainerAction QUICK_MOVE/QUICK_DROP so no custom action registration is needed.
    }

    public static void setupForm(CraftingGuideContainerForm form, Client client, CraftingGuideContainer container) {
       if (form == null || client == null || container == null) {
          Logging.logMessage("[CraftingGuide] Cannot set up extension because form/client/container was null");
          return;
       }
       if (formStates.containsKey(form)) return;

       form.clearComponents();
       form.setWidth(600);
       form.setHeight(300);

       form.setPosition(form.getX(), form.getY() - 140);

       GuideState state = new GuideState(form, client, container);
       formStates.put(form, state);
       state.buildForms();
       state.syncSelectedItem();
       if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Extended guide form initialized at 400x300");
    }

    public static int beforeDraw(CraftingGuideContainerForm form) {
       GuideState state = formStates.get(form);
       if (state == null) return -1;
       state.syncSelectedItem();
       InventoryItem real = state.ingredientSlot.getContainerSlot().getItem();
       return real == null ? -1 : real.item.getID();
    }

    public static boolean returnRealIngredient(CraftingGuideContainer container, Client client) {
       if (container == null || client == null) return false;
       ContainerSlot slot = container.getSlot(container.INGREDIENT_SLOT);
       if (slot == null || slot.getItem() == null) return true;

       InventoryItem original = slot.getItem();
       String itemID = original.item.getStringID();
       int originalAmount = original.getAmount();

       ContainerActionResult moveResult = container.applyContainerAction(container.INGREDIENT_SLOT, ContainerAction.QUICK_MOVE);
       client.network.sendPacket(new PacketContainerAction(container.INGREDIENT_SLOT, ContainerAction.QUICK_MOVE, moveResult.value));

       ContainerSlot afterMove = container.getSlot(container.INGREDIENT_SLOT);
       if (afterMove != null && afterMove.getItem() != null) {
          int remaining = afterMove.getItemAmount();
          ContainerActionResult dropResult = container.applyContainerAction(container.INGREDIENT_SLOT, ContainerAction.QUICK_DROP);
          client.network.sendPacket(new PacketContainerAction(container.INGREDIENT_SLOT, ContainerAction.QUICK_DROP, dropResult.value));
          if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Inventory full while replacing guide item; dropped remainder item=" + itemID + " amount=" + remaining);
       }

       boolean cleared = container.getSlot(container.INGREDIENT_SLOT).getItem() == null;
       if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Returned real guide item item=" + itemID + " amount=" + originalAmount + " cleared=" + cleared);
       return cleared;
    }

    public static final class GuideState {
       private final CraftingGuideContainerForm form;
       private final Client client;
       private final CraftingGuideContainer container;
       private final Set<String> collapsedCategories = new HashSet<>();
       private final ArrayList<Integer> searchableItemIDs = new ArrayList<>();
       private Form mainForm;
       private Form searchForm;
       private FormContentBox guideContent;
       private FormContentBox searchContent;
       private FormTextInput searchInput;
       private CraftingGuideMaterialSlot ingredientSlot;
       private int lastSelectedItemID = Integer.MIN_VALUE;
       private Integer pendingGhostItemID;
       private boolean pendingCloseSearch;

       public GuideState(CraftingGuideContainerForm form, Client client, CraftingGuideContainer container) {
          this.form = form;
          this.client = client;
          this.container = container;
          this.searchableItemIDs.addAll(collectSearchableItemIDs());
       }

       public void buildForms() {
          mainForm = (Form)form.addComponent(new Form("dynamicCraftingGuideMain", 600, 300));
          mainForm.drawBase = false;
          mainForm.drawEdge = false;

          InventoryItem guideItem = container.guideSlot.getItem(container.client.playerMob.getInv());
          mainForm.addComponent(new FormLabel(
                guideItem == null ? "NULL" : guideItem.getItemLocalization().translate(),
                new FontOptions(20), -1, 10, 10));

          guideContent = mainForm.addComponent(new FormContentBox(0, 34, 532, 260));
          ingredientSlot = mainForm.addComponent(new CraftingGuideMaterialSlot(client, container, container.INGREDIENT_SLOT, 540, 220));

          FormContentIconButton searchButton = mainForm.addComponent(new FormContentIconButton(
                544,
                180,
                FormInputSize.SIZE_32,
                ButtonColor.BASE,
                mainForm.getInterfaceStyle().button_search_24,
                new GameMessage[]{new StaticMessage(Localization.translate("ui", "search"))}
          ));
          searchButton.onClicked(e -> openSearch());

          searchForm = (Form)form.addComponent(new Form("dynamicCraftingGuideSearch", 600, 300));
          searchForm.zIndex = 1000;
          searchForm.setHidden(true);
          searchForm.addComponent(new FormLabel(
                Localization.translate("ui", "search"),
                new FontOptions(20),
                0,
                searchForm.getWidth() / 2,
                8
          ));

          searchInput = searchForm.addComponent(new FormTextInput(10, 34, FormInputSize.SIZE_24, 580, -1, 200));
          searchInput.placeHolder = new LocalMessage("ui", "searchtip");
          searchInput.rightClickToClear = true;
          searchInput.onChange(e -> rebuildSearch());

          searchContent = searchForm.addComponent(new FormContentBox(4, 66, 592, 188));
          FormLocalTextButton back = searchForm.addComponent(new FormLocalTextButton(
                new StaticMessage(Localization.translate("ui", "backbutton")),
                120,
                262,
                160,
                FormInputSize.SIZE_24,
                ButtonColor.BASE
          ));
          back.onClicked(e -> closeSearch());

          GuideEscapeCatcher escapeCatcher = searchForm.addComponent(new GuideEscapeCatcher(this));
          escapeCatcher.zIndex = 10000;
          rebuildSearch();
       }

       public void openSearch() {
          mainForm.setHidden(true);
          searchForm.setHidden(false);
          searchInput.setTyping(true);
          searchInput.selectAll();
          ControllerInput.submitNextRefreshFocusEvent();
          if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Search opened");
       }

       public void closeSearch() {
          if (searchInput != null && searchInput.isTyping()) searchInput.setTyping(false);
          searchForm.setHidden(true);
          mainForm.setHidden(false);
          ControllerInput.submitNextRefreshFocusEvent();
          if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Search closed");
       }

       public boolean isSearchOpen() {
          return searchForm != null && !searchForm.isHidden();
       }

       public void selectItem(int itemID, boolean closeSearchAfter) {
          Item item = ItemRegistry.getItem(itemID);
          if (item == null) {
             Logging.logMessage("[CraftingGuide] Cannot select missing item ID=" + itemID);
             return;
          }

          InventoryItem realItem = ingredientSlot.getContainerSlot().getItem();
          if (realItem != null) {
             pendingGhostItemID = itemID;
             pendingCloseSearch = closeSearchAfter;
             if (!returnRealIngredient(container, client)) {
                pendingGhostItemID = null;
                pendingCloseSearch = false;
                return;
             }
             applyPendingGhostSelectionIfReady();
             return;
          }

          setGhostSelection(itemID, closeSearchAfter);
       }

       private void setGhostSelection(int itemID, boolean closeSearchAfter) {
          Item item = ItemRegistry.getItem(itemID);
          if (item == null) return;
          ingredientSlot.ghostItem = new InventoryItem(item);
          lastSelectedItemID = Integer.MIN_VALUE;
          syncSelectedItem();
          if (closeSearchAfter) closeSearch();
          if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Selected ghost item=" + item.getStringID());
       }

       private void applyPendingGhostSelectionIfReady() {
          if (pendingGhostItemID == null || ingredientSlot.getContainerSlot().getItem() != null) return;
          int itemID = pendingGhostItemID;
          boolean closeSearchAfter = pendingCloseSearch;
          pendingGhostItemID = null;
          pendingCloseSearch = false;
          setGhostSelection(itemID, closeSearchAfter);
       }

       public void syncSelectedItem() {
          if (ingredientSlot == null) return;
          applyPendingGhostSelectionIfReady();
          InventoryItem realItem = ingredientSlot.getContainerSlot().getItem();
          if (realItem != null && ingredientSlot.ghostItem != null) ingredientSlot.ghostItem = null;
          InventoryItem selected = realItem != null ? realItem : ingredientSlot.ghostItem;
          int selectedItemID = selected == null ? -1 : selected.item.getID();
          if (selectedItemID != lastSelectedItemID) {
             lastSelectedItemID = selectedItemID;
             rebuildGuide(selectedItemID);
             if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Rebuilt guide selection=" + (selected == null ? "none" : selected.item.getStringID()) + " real=" + (realItem != null));
          }
       }

       private void rebuildGuide(int selectedItemID) {
          guideContent.clearComponents();
          if (selectedItemID < 0 || ItemRegistry.getItem(selectedItemID) == null) {
             guideContent.addComponent(new FormLabel(
                   Localization.translate("ui", "insertmat"),
                   new FontOptions(16),
                   0,
                   guideContent.getWidth() / 2,
                   38));
             guideContent.setContentBox(new Rectangle(guideContent.getWidth(), guideContent.getHeight()));
             return;
          }

          List<Recipe> resultRecipes = castRecipes(Recipes.getRecipesFromResult(selectedItemID));
          List<Recipe> ingredientRecipes = castRecipes(Recipes.getRecipesFromIngredient(selectedItemID));
          List<ForgeCookingRecipe> forgeResultRecipes = getForgeRecipesFromResult(selectedItemID);
          List<ForgeCookingRecipe> forgeIngredientRecipes = getForgeRecipesFromIngredient(selectedItemID);
          int y = 6;
          boolean addedAnySection = false;

          if (!resultRecipes.isEmpty() || !forgeResultRecipes.isEmpty()) {
             addedAnySection = true;
             guideContent.addComponent(new FormLabel(
                   Localization.translate("ui", "craftingguidemadefrom"),
                   new FontOptions(16), -1, 8, y));
             y += 22;

             LinkedHashMap<String, ArrayList<Recipe>> recipesByTech = new LinkedHashMap<>();
             for (Recipe recipe : resultRecipes) {
                String techName = recipe.tech.displayName.translate();
                recipesByTech.computeIfAbsent(techName, key -> new ArrayList<>()).add(recipe);
             }

             int totalRecipeAlternatives = resultRecipes.size() + forgeResultRecipes.size();
             for (Map.Entry<String, ArrayList<Recipe>> entry : recipesByTech.entrySet()) {
                if (totalRecipeAlternatives > 1) {
                   guideContent.addComponent(new FormLabel(
                         entry.getKey(),
                         new FontOptions(12).color(guideContent.getInterfaceStyle().activeTextColor),
                         -1,
                         8,
                         y));
                   y += 18;
                }
                ArrayList<Recipe> recipes = entry.getValue();
                for (int i = 0; i < recipes.size(); i++) {
                   y = addIngredientGrid(recipes.get(i), y);
                   if (i < recipes.size() - 1) y += 4;
                }
                y += 4;
             }

             if (!forgeResultRecipes.isEmpty()) {
                if (totalRecipeAlternatives > 1) {
                   guideContent.addComponent(new FormLabel(
                         getForgeDisplayName(),
                         new FontOptions(12).color(guideContent.getInterfaceStyle().activeTextColor),
                         -1,
                         8,
                         y));
                   y += 18;
                }
                for (int i = 0; i < forgeResultRecipes.size(); i++) {
                   y = addForgeIngredientGrid(forgeResultRecipes.get(i), y);
                   if (i < forgeResultRecipes.size() - 1) y += 4;
                }
             }
          }

          if (!ingredientRecipes.isEmpty() || !forgeIngredientRecipes.isEmpty()) {
             if (addedAnySection) y += 8;
             addedAnySection = true;
             guideContent.addComponent(new FormLabel(
                   Localization.translate("ui", "craftingguideusedto"),
                   new FontOptions(16), -1, 8, y));
             y += 22;
             y = addResultGrid(ingredientRecipes, forgeIngredientRecipes, y);
          }

          if (!addedAnySection) {
             guideContent.addComponent(new FormLabel(
                   Localization.translate("ui", "craftingguidenorecipes"),
                   new FontOptions(16),
                   0,
                   guideContent.getWidth() / 2,
                   38));
             y = 68;
          }

          guideContent.setContentBox(new Rectangle(guideContent.getWidth(), Math.max(guideContent.getHeight(), y + 8)));
       }

       private int addIngredientGrid(Recipe recipe, int startY) {
          Ingredient[] ingredients = recipe.ingredients;
          if (ingredients == null || ingredients.length == 0) return startY;
          int contentWidth = guideContent.getWidth() - guideContent.getScrollBarWidth() - 12;
          int iconSize = 36;
          int iconsPerRow = Math.max(1, contentWidth / iconSize);
          for (int i = 0; i < ingredients.length; i++) {
             int x = 8 + (i % iconsPerRow) * iconSize;
             int y = startY + (i / iconsPerRow) * iconSize;
             guideContent.addComponent(GuideItemIcon.forIngredient(this, x, y, recipe, i));
          }
          int rows = (ingredients.length + iconsPerRow - 1) / iconsPerRow;
          return startY + rows * iconSize;
       }

       private int addForgeIngredientGrid(ForgeCookingRecipe recipe, int startY) {
          ArrayList<ForgeCookingInput> inputs = getForgeInputs(recipe);
          if (inputs.isEmpty()) return startY;
          int contentWidth = guideContent.getWidth() - guideContent.getScrollBarWidth() - 12;
          int iconSize = 36;
          int iconsPerRow = Math.max(1, contentWidth / iconSize);
          for (int i = 0; i < inputs.size(); i++) {
             int x = 8 + (i % iconsPerRow) * iconSize;
             int y = startY + (i / iconsPerRow) * iconSize;
             guideContent.addComponent(ForgeGuideItemIcon.forIngredient(this, x, y, recipe, inputs.get(i)));
          }
          int rows = (inputs.size() + iconsPerRow - 1) / iconsPerRow;
          return startY + rows * iconSize;
       }

       private int addResultGrid(List<Recipe> recipes, List<ForgeCookingRecipe> forgeRecipes, int startY) {
          int contentWidth = guideContent.getWidth() - guideContent.getScrollBarWidth() - 12;
          int iconSize = 36;
          int iconsPerRow = Math.max(1, contentWidth / iconSize);
          int total = recipes.size() + forgeRecipes.size();
          for (int i = 0; i < total; i++) {
             int x = 8 + (i % iconsPerRow) * iconSize;
             int y = startY + (i / iconsPerRow) * iconSize;
             if (i < recipes.size()) {
                guideContent.addComponent(GuideItemIcon.forRecipeResult(this, x, y, recipes.get(i)));
             } else {
                guideContent.addComponent(ForgeGuideItemIcon.forRecipeResult(this, x, y, forgeRecipes.get(i - recipes.size())));
             }
          }
          int rows = (total + iconsPerRow - 1) / iconsPerRow;
          return startY + rows * iconSize;
       }

       private List<ForgeCookingRecipe> getForgeRecipesFromResult(int itemID) {
          Item item = ItemRegistry.getItem(itemID);
          if (item == null) return new ArrayList<>();
          String itemStringID = item.getStringID();
          ArrayList<ForgeCookingRecipe> out = new ArrayList<>();
          for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
             if (recipe != null && itemStringID.equals(recipe.outputItemStringID)) out.add(recipe);
          }
          return out;
       }

       private List<ForgeCookingRecipe> getForgeRecipesFromIngredient(int itemID) {
          Item item = ItemRegistry.getItem(itemID);
          if (item == null) return new ArrayList<>();
          String itemStringID = item.getStringID();
          ArrayList<ForgeCookingRecipe> out = new ArrayList<>();
          for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
             if (recipe == null) continue;
             if (forgeInputMatches(recipe.firstInput, itemStringID) || forgeInputMatches(recipe.secondInput, itemStringID)) out.add(recipe);
          }
          return out;
       }

       private boolean forgeInputMatches(ForgeCookingInput input, String itemStringID) {
          return input != null && itemStringID.equals(input.itemStringID);
       }

       private void rebuildSearch() {
          if (searchContent == null) return;
          searchContent.clearComponents();
          String filter = searchInput == null || searchInput.getText() == null ? "" : searchInput.getText().trim().toLowerCase(Locale.ROOT);
          boolean searching = !filter.isEmpty();

          Map<String, ItemCategory> displayNameToCategory = new HashMap<>();
          Map<ItemCategory, ArrayList<Integer>> byCategory = new HashMap<>();
          for (Integer itemID : searchableItemIDs) {
             Item item = ItemRegistry.getItem(itemID);
             if (item == null || !ItemRegistry.isObtainable(itemID)) continue;
             InventoryItem inventoryItem = new InventoryItem(item);
             if (searching && !inventoryItem.getItemDisplayName().toLowerCase(Locale.ROOT).contains(filter)) continue;

             ItemCategory category = ItemCategory.craftingManager.getItemsCategory(item);
             if (category == null) category = ItemCategory.craftingMasterCategory;
             String displayName = category.displayName.translate();
             ItemCategory existing = displayNameToCategory.get(displayName);
             if (existing != null) category = existing;
             else displayNameToCategory.put(displayName, category);
             byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(itemID);
          }

          ArrayList<ItemCategory> categories = new ArrayList<>(byCategory.keySet());
          Collections.sort(categories);
          int y = 2;
          int contentWidth = searchContent.getWidth() - searchContent.getScrollBarWidth() - 8;
          int iconSize = 36;
          int iconsPerRow = Math.max(1, (contentWidth - 28) / iconSize);

          for (ItemCategory category : categories) {
             ArrayList<Integer> items = byCategory.get(category);
             items.sort((a, b) -> new InventoryItem(ItemRegistry.getItem(a)).getItemDisplayName().compareToIgnoreCase(new InventoryItem(ItemRegistry.getItem(b)).getItemDisplayName()));
             boolean expanded = searching || !collapsedCategories.contains(category.stringID);
             int headerY = y;
             FormContentIconButton expand = searchContent.addComponent(new FormContentIconButton(
                   4,
                   headerY + 2,
                   FormInputSize.SIZE_20,
                   ButtonColor.BASE,
                   expanded ? searchContent.getInterfaceStyle().button_expanded_16 : searchContent.getInterfaceStyle().button_collapsed_16,
                   new GameMessage[0]
             ));
             expand.setActive(!searching);
             expand.onClicked(e -> {
                if (collapsedCategories.contains(category.stringID)) collapsedCategories.remove(category.stringID);
                else collapsedCategories.add(category.stringID);
                rebuildSearch();
             });
             searchContent.addComponent(new FormLabel(category.displayName.translate(), new FontOptions(16), -1, 28, headerY + 4));
             y += 24;

             if (!expanded) continue;
             int rowCount = (items.size() + iconsPerRow - 1) / iconsPerRow;
             int gridY = y;
             for (int i = 0; i < items.size(); i++) {
                int x = 28 + (i % iconsPerRow) * iconSize;
                int itemY = gridY + (i / iconsPerRow) * iconSize;
                searchContent.addComponent(GuideItemIcon.forSearch(this, x, itemY, items.get(i)));
             }
             y += rowCount * iconSize + 2;
          }

          if (categories.isEmpty()) {
             searchContent.addComponent(new FormLabel(
                   Localization.translate("ui", searching ? "nomatchingitems" : "nocraftableitems"),
                   new FontOptions(16),
                   0,
                   searchContent.getWidth() / 2,
                   24));
             y = 56;
          }

          searchContent.setContentBox(new Rectangle(searchContent.getWidth(), Math.max(searchContent.getHeight(), y + 8)));
       }

       private ArrayList<Integer> collectSearchableItemIDs() {
          LinkedHashSet<Integer> ids = new LinkedHashSet<>();
          List<Recipe> recipes = castRecipes(Recipes.streamRecipes().collect(Collectors.toList()));
          for (Recipe recipe : recipes) {
             if (recipe == null || recipe.resultItem == null || recipe.resultItem.item == null) continue;
             int resultID = recipe.resultItem.item.getID();
             if (ItemRegistry.isObtainable(resultID)) ids.add(resultID);

             Predicate<Item> itemFilter = recipe.getIngredientItemFilter();
             for (Ingredient ingredient : recipe.ingredients) {
                if (ingredient == null) continue;
                if (!ingredient.isGlobalIngredient()) {
                   Item item = ItemRegistry.getItem(ingredient.getIngredientID());
                   if (item != null && ItemRegistry.isObtainable(item.getID()) && (itemFilter == null || itemFilter.test(item))) ids.add(item.getID());
                   continue;
                }

                GlobalIngredient global = ingredient.getGlobalIngredient();
                if (global == null) continue;
                for (Object value : global.getObtainableRegisteredItemIDs()) {
                   if (!(value instanceof Integer)) continue;
                   int itemID = (Integer)value;
                   Item item = ItemRegistry.getItem(itemID);
                   if (item != null && (itemFilter == null || itemFilter.test(item))) ids.add(itemID);
                }
             }
          }
          for (ForgeCookingRecipe recipe : ForgeCookingRecipeRegistry.getRecipes()) {
             if (recipe == null) continue;
             Item output = ItemRegistry.getItem(recipe.outputItemStringID);
             if (output != null && ItemRegistry.isObtainable(output.getID())) ids.add(output.getID());
             addForgeInputSearchItem(ids, recipe.firstInput);
             addForgeInputSearchItem(ids, recipe.secondInput);
          }
          return new ArrayList<>(ids);
       }

       private void addForgeInputSearchItem(Set<Integer> ids, ForgeCookingInput input) {
          if (input == null) return;
          Item item = ItemRegistry.getItem(input.itemStringID);
          if (item != null && ItemRegistry.isObtainable(item.getID())) ids.add(item.getID());
       }
    }

    public static final class CraftingGuideMaterialSlot extends FormContainerMaterialSlot {
       private final CraftingGuideContainer guideContainer;

       public CraftingGuideMaterialSlot(Client client, CraftingGuideContainer container, int containerSlotIndex, int x, int y) {
          super(client, container, containerSlotIndex, x, y);
          this.guideContainer = container;
       }

       @Override
       protected void handleActionInputEvents(InputEvent event) {
          if (ghostItem != null && getContainerSlot().getItem() == null && getContainer().getClientDraggingSlot().getItem() == null) {
             if (isMouseOver(event) && event.isMouseClickEvent()) {
                if (event.state && event.getID() == -99) {
                   ghostItem = null;
                   playTickSound();
                   if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Cleared ghost item with right click");
                }
                event.use();
             }
             return;
          }
          super.handleActionInputEvents(event);
       }

       @Override
       protected void handleActionControllerEvents(ControllerEvent event) {
          if (ghostItem != null && getContainerSlot().getItem() == null && getContainer().getClientDraggingSlot().getItem() == null) {
             if (isControllerFocus() && event.getState() == ControllerInput.MENU_SELECT && event.buttonState) event.use();
             return;
          }
          super.handleActionControllerEvents(event);
       }

       @Override
       public GameTooltips getItemTooltip(InventoryItem item, PlayerMob perspective) {
          return buildCraftingItemTooltip(item, perspective, guideContainer, super.getItemTooltip(item, perspective));
       }
    }

    public static final class GuideItemIcon extends FormItemIcon {
       private final GuideState state;
       private final Recipe recipe;
       private final int ingredientIndex;
       private final boolean ingredientIcon;
       private final boolean searchIcon;
       private final int searchItemID;

       private GuideItemIcon(GuideState state, int x, int y, Recipe recipe, int ingredientIndex, boolean ingredientIcon, boolean searchIcon, int searchItemID) {
          super(x, y, initialItem(recipe, ingredientIndex, ingredientIcon, searchItemID), false);
          this.state = state;
          this.recipe = recipe;
          this.ingredientIndex = ingredientIndex;
          this.ingredientIcon = ingredientIcon;
          this.searchIcon = searchIcon;
          this.searchItemID = searchItemID;
       }

       public static GuideItemIcon forIngredient(GuideState state, int x, int y, Recipe recipe, int ingredientIndex) {
          return new GuideItemIcon(state, x, y, recipe, ingredientIndex, true, false, -1);
       }

       public static GuideItemIcon forRecipeResult(GuideState state, int x, int y, Recipe recipe) {
          return new GuideItemIcon(state, x, y, recipe, -1, false, false, -1);
       }

       public static GuideItemIcon forSearch(GuideState state, int x, int y, int itemID) {
          return new GuideItemIcon(state, x, y, null, -1, false, true, itemID);
       }

       private static InventoryItem initialItem(Recipe recipe, int ingredientIndex, boolean ingredientIcon, int searchItemID) {
          if (searchItemID >= 0 && ItemRegistry.getItem(searchItemID) != null) return new InventoryItem(ItemRegistry.getItem(searchItemID));
          if (recipe == null) return new InventoryItem(ItemRegistry.getItem(0));
          if (!ingredientIcon) return recipe.resultItem.copy();
          if (ingredientIndex < 0 || ingredientIndex >= recipe.ingredients.length) return recipe.resultItem.copy();
          Ingredient ingredient = recipe.ingredients[ingredientIndex];
          Item displayItem = ingredient.getDisplayItem(recipe.getIngredientItemFilter());
          return displayItem == null ? recipe.resultItem.copy() : new InventoryItem(displayItem, Math.max(1, ingredient.getIngredientAmount()));
       }

       private InventoryItem resolveItem() {
          if (searchIcon) {
             Item item = ItemRegistry.getItem(searchItemID);
             return item == null ? null : new InventoryItem(item);
          }
          if (recipe == null) return null;
          if (!ingredientIcon) return recipe.resultItem.copy();
          if (ingredientIndex < 0 || ingredientIndex >= recipe.ingredients.length) return null;
          Ingredient ingredient = recipe.ingredients[ingredientIndex];
          Item displayItem = ingredient.getDisplayItem(recipe.getIngredientItemFilter());
          if (displayItem == null) return null;
          return new InventoryItem(displayItem, Math.max(1, ingredient.getIngredientAmount()));
       }

       @Override
       public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
          if (event.isMouseMoveEvent()) {
             super.handleInputEvent(event, tickManager, perspective);
             return;
          }

          if (event.isMouseClickEvent() && (isHovering() || isMouseOver(event))) {
             if (event.state && event.getID() == -100) {
                InventoryItem selected = resolveItem();
                if (selected != null) {
                   state.selectItem(selected.item.getID(), searchIcon);
                   if (event.shouldSubmitSound()) playTickSound();
                   if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Clicked guide item=" + selected.item.getStringID() + " search=" + searchIcon);
                }
             }
             event.use();
             return;
          }

          super.handleInputEvent(event, tickManager, perspective);
       }

       @Override
       public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
          InventoryItem drawItem = resolveItem();
          if (drawItem == null) return;
          this.item = drawItem;
          if (isHovering()) getInterfaceStyle().inventoryslot_small.highlighted.initDraw().draw(getX() + 2, getY() + 2);
          else getInterfaceStyle().inventoryslot_small.active.initDraw().draw(getX() + 2, getY() + 2);
          drawItem.draw(perspective, getX() + 2, getY() + 2, false);
          if (ingredientIcon) {
             Renderer.drawRectangleLines(new Rectangle(getX() + 1, getY() + 1, 34, 34), 1.0F, 0.08F, 0.08F, 1.0F);
             Renderer.drawRectangleLines(new Rectangle(getX() + 2, getY() + 2, 32, 32), 1.0F, 0.08F, 0.08F, 0.85F);
          }
          if (isHovering()) addGuideTooltips(perspective, drawItem);
       }

       private void addGuideTooltips(PlayerMob perspective, InventoryItem drawItem) {
          if (searchIcon) {
             GameTooltipManager.addTooltip(drawItem.getTooltip(perspective, new GameBlackboard()), GameBackground.getItemTooltipBackground(), TooltipLocation.FORM_FOCUS);
             return;
          }
          if (recipe == null) return;
          if (!ingredientIcon) {
             CanCraft canCraft = state.container.canCraftRecipe(recipe, state.container.getCraftInventories(), true);
             ListGameTooltips tooltips = new ListGameTooltips(recipe.getTooltip(canCraft, perspective, new GameBlackboard()));
             tooltips.add((recipe.isHidden
                   ? new LocalMessage("tech", "madeinhidden", "tech", recipe.tech.displayName)
                   : new LocalMessage("tech", "madein", "tech", recipe.tech.displayName)).translate());
             GameTooltipManager.addTooltip(tooltips, GameBackground.getItemTooltipBackground(), TooltipLocation.FORM_FOCUS);
             return;
          }

          GameTooltips tooltips = buildCraftingItemTooltip(
                drawItem,
                perspective,
                state.container,
                drawItem.getTooltip(perspective, new GameBlackboard()));
          GameTooltipManager.addTooltip(tooltips, GameBackground.getItemTooltipBackground(), TooltipLocation.FORM_FOCUS);
       }
    }

    public static final class ForgeGuideItemIcon extends FormItemIcon {
       private final GuideState state;
       private final ForgeCookingRecipe recipe;
       private final ForgeCookingInput input;
       private final boolean ingredientIcon;

       private ForgeGuideItemIcon(GuideState state, int x, int y, ForgeCookingRecipe recipe, ForgeCookingInput input, boolean ingredientIcon) {
          super(x, y, getForgeIconItem(recipe, input, ingredientIcon), false);
          this.state = state;
          this.recipe = recipe;
          this.input = input;
          this.ingredientIcon = ingredientIcon;
       }

       public static ForgeGuideItemIcon forIngredient(GuideState state, int x, int y, ForgeCookingRecipe recipe, ForgeCookingInput input) {
          return new ForgeGuideItemIcon(state, x, y, recipe, input, true);
       }

       public static ForgeGuideItemIcon forRecipeResult(GuideState state, int x, int y, ForgeCookingRecipe recipe) {
          return new ForgeGuideItemIcon(state, x, y, recipe, null, false);
       }

       private InventoryItem resolveItem() {
          return getForgeIconItem(recipe, input, ingredientIcon);
       }

       @Override
       public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
          if (event.isMouseMoveEvent()) {
             super.handleInputEvent(event, tickManager, perspective);
             return;
          }
          if (event.isMouseClickEvent() && (isHovering() || isMouseOver(event))) {
             if (event.state && event.getID() == -100) {
                InventoryItem selected = resolveItem();
                if (selected != null) {
                   state.selectItem(selected.item.getID(), false);
                   if (event.shouldSubmitSound()) playTickSound();
                   if (Logging.logEnabled) Logging.logMessage("[CraftingGuide] Clicked forge guide item=" + selected.item.getStringID());
                }
             }
             event.use();
             return;
          }
          super.handleInputEvent(event, tickManager, perspective);
       }

       @Override
       public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
          InventoryItem drawItem = resolveItem();
          if (drawItem == null) return;
          this.item = drawItem;
          if (isHovering()) getInterfaceStyle().inventoryslot_small.highlighted.initDraw().draw(getX() + 2, getY() + 2);
          else getInterfaceStyle().inventoryslot_small.active.initDraw().draw(getX() + 2, getY() + 2);
          drawItem.draw(perspective, getX() + 2, getY() + 2, false);
          if (ingredientIcon) {
             Renderer.drawRectangleLines(new Rectangle(getX() + 1, getY() + 1, 34, 34), 1.0F, 0.08F, 0.08F, 1.0F);
             Renderer.drawRectangleLines(new Rectangle(getX() + 2, getY() + 2, 32, 32), 1.0F, 0.08F, 0.08F, 0.85F);
          }
          if (isHovering()) {
             GameTooltips tooltips = ingredientIcon
                   ? buildCraftingItemTooltip(drawItem, perspective, state.container, drawItem.getTooltip(perspective, new GameBlackboard()))
                   : buildForgeRecipeTooltip(recipe, perspective, drawItem);
             GameTooltipManager.addTooltip(tooltips, GameBackground.getItemTooltipBackground(), TooltipLocation.FORM_FOCUS);
          }
       }
    }

    private static InventoryItem getForgeIconItem(ForgeCookingRecipe recipe, ForgeCookingInput input, boolean ingredientIcon) {
       if (recipe == null) return new InventoryItem(ItemRegistry.getItem(0));
       if (!ingredientIcon) {
          InventoryItem output = recipe.getOutput();
          return output == null ? new InventoryItem(ItemRegistry.getItem(0)) : output;
       }
       if (input == null) return new InventoryItem(ItemRegistry.getItem(0));
       Item item = ItemRegistry.getItem(input.itemStringID);
       return item == null ? new InventoryItem(ItemRegistry.getItem(0)) : new InventoryItem(item, Math.max(1, input.amount));
    }

    private static ArrayList<ForgeCookingInput> getForgeInputs(ForgeCookingRecipe recipe) {
       ArrayList<ForgeCookingInput> inputs = new ArrayList<>();
       if (recipe == null) return inputs;
       if (recipe.firstInput != null) inputs.add(recipe.firstInput);
       if (recipe.secondInput != null) inputs.add(recipe.secondInput);
       return inputs;
    }

    private static String getForgeDisplayName() {
       String forgeName = Localization.translate("item", "forge");
       return forgeName == null || forgeName.isEmpty() || forgeName.startsWith("item.") ? "Forge" : forgeName;
    }

    private static GameTooltips buildCraftingItemTooltip(InventoryItem item, PlayerMob perspective, CraftingGuideContainer container, GameTooltips baseTooltip) {
       if (item == null || item.item == null) return baseTooltip;
       List<Recipe> recipes = castRecipes(Recipes.getRecipesFromResult(item.item.getID()));
       ArrayList<ForgeCookingRecipe> forgeRecipes = new ArrayList<>();
       String itemStringID = item.item.getStringID();
       for (ForgeCookingRecipe forgeRecipe : ForgeCookingRecipeRegistry.getRecipes()) {
          if (forgeRecipe != null && itemStringID.equals(forgeRecipe.outputItemStringID)) forgeRecipes.add(forgeRecipe);
       }
       if (recipes.isEmpty() && forgeRecipes.isEmpty()) return baseTooltip;

       ListGameTooltips tooltips = new ListGameTooltips();
       tooltips.add(baseTooltip);
       for (Recipe recipe : recipes) {
          tooltips.add(new SpacerGameTooltip(8));
          tooltips.add((recipe.isHidden
                ? new LocalMessage("tech", "madeinhidden", "tech", recipe.tech.displayName)
                : new LocalMessage("tech", "madein", "tech", recipe.tech.displayName)).translate());
          if (recipe.resultAmount == 1) tooltips.add(Localization.translate("misc", "recipecostsing"));
          else tooltips.add(Localization.translate("misc", "recipecostmult", "amount", recipe.resultAmount));

          CanCraft canCraft = container.canCraftRecipe(recipe, container.getCraftInventories(), true);
          for (int i = 0; i < recipe.ingredients.length; i++) {
             Ingredient ingredient = recipe.ingredients[i];
             int have = canCraft == null ? ingredient.getIngredientAmount() : canCraft.haveIngredients[i];
             boolean countAll = canCraft != null && canCraft.countAllIngredients;
             tooltips.add(ingredient.getTooltips(have, countAll, recipe.getIngredientItemFilter()));
          }
       }
       for (ForgeCookingRecipe forgeRecipe : forgeRecipes) {
          tooltips.add(new SpacerGameTooltip(8));
          tooltips.add(new LocalMessage("tech", "madein", "tech", new StaticMessage(getForgeDisplayName())).translate());
          if (forgeRecipe.outputAmount == 1) tooltips.add(Localization.translate("misc", "recipecostsing"));
          else tooltips.add(Localization.translate("misc", "recipecostmult", "amount", forgeRecipe.outputAmount));
          addForgeInputTooltips(tooltips, forgeRecipe);
       }
       return tooltips;
    }

    private static GameTooltips buildForgeRecipeTooltip(ForgeCookingRecipe recipe, PlayerMob perspective, InventoryItem output) {
       ListGameTooltips tooltips = new ListGameTooltips();
       tooltips.add(output.getTooltip(perspective, new GameBlackboard()));
       tooltips.add(new SpacerGameTooltip(8));
       tooltips.add(new LocalMessage("tech", "madein", "tech", new StaticMessage(getForgeDisplayName())).translate());
       if (recipe.outputAmount == 1) tooltips.add(Localization.translate("misc", "recipecostsing"));
       else tooltips.add(Localization.translate("misc", "recipecostmult", "amount", recipe.outputAmount));
       addForgeInputTooltips(tooltips, recipe);
       return tooltips;
    }

    private static void addForgeInputTooltips(ListGameTooltips tooltips, ForgeCookingRecipe recipe) {
       if (recipe == null) return;
       addForgeInputTooltip(tooltips, recipe.firstInput);
       addForgeInputTooltip(tooltips, recipe.secondInput);
    }

    private static void addForgeInputTooltip(ListGameTooltips tooltips, ForgeCookingInput input) {
       if (input == null) return;
       Item item = ItemRegistry.getItem(input.itemStringID);
       if (item == null) return;
       StringBuilder line = new StringBuilder();
       line.append(input.amount).append(" ").append(new InventoryItem(item).getItemDisplayName());
       if (input.resultBehavior == ForgeCookingInput.ResultBehavior.KEEP) line.append(" (not consumed)");
       else if (input.resultBehavior == ForgeCookingInput.ResultBehavior.DURABILITY_USE) line.append(" (uses durability)");
       else if (input.resultBehavior == ForgeCookingInput.ResultBehavior.REPLACE) line.append(" (returns item)");
       tooltips.add(line.toString());
    }

    public static final class GuideEscapeCatcher extends FormComponent implements FormPositionContainer {
       private FormPosition position = new FormFixedPosition(0, 0);
       private final GuideState state;

       public GuideEscapeCatcher(GuideState state) {
          this.state = state;
       }

       @Override
       public void handleInputEvent(InputEvent event, TickManager tickManager, PlayerMob perspective) {
          if (state.isSearchOpen() && !event.isUsed() && event.state && event.getID() == 256) {
             state.closeSearch();
             event.use();
          }
       }

       @Override
       public void handleControllerEvent(ControllerEvent event, TickManager tickManager, PlayerMob perspective) {
          if (state.isSearchOpen() && !event.isUsed() && event.buttonState
                && (event.getState() == ControllerInput.MENU_BACK || event.getState() == ControllerInput.MAIN_MENU)) {
             state.closeSearch();
             event.use();
          }
       }

       @Override
       public void addNextControllerFocus(List list, int currentXOffset, int currentYOffset, ControllerNavigationHandler customNavigationHandler, Rectangle area, boolean draw) {
       }

       @Override
       public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
       }

       @Override
       public List getHitboxes() {
          return Collections.emptyList();
       }

       @Override
       public FormPosition getPosition() {
          return position;
       }

       @Override
       public void setPosition(FormPosition position) {
          this.position = position;
       }
    }

    @SuppressWarnings("unchecked")
    private static List<Recipe> castRecipes(Collection recipes) {
       if (recipes == null) return new ArrayList<>();
       ArrayList<Recipe> out = new ArrayList<>();
       for (Object value : recipes) if (value instanceof Recipe) out.add((Recipe)value);
       return out;
    }
}
