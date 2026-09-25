package opusliews.journal;

import java.awt.Color;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.function.Function;

import necesse.engine.Settings;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.network.client.Client;
import necesse.engine.registries.ItemRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.GameBackground;
import necesse.gfx.fairType.FairItemGlyph;
import necesse.gfx.fairType.FairType;
import necesse.gfx.fairType.TypeParsers;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormBreakLine;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormContentIconButton;
import necesse.gfx.forms.components.FormFairTypeLabel;
import necesse.gfx.forms.components.FormFlow;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormMouseHover;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.containerComponent.journal.FormJournalEntryComponent;
import necesse.gfx.forms.presets.containerComponent.journal.JournalContainerForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.lootTable.LootList;
import opusliews.network.PacketCompleteJournalSection;

public class GuideJournalFormRenderer {
	public static int renderLeftEntry(GuideJournalEntry entry, Client client, FormContentBox entries, JournalContainerForm journalForm, int x, int y) {
		FontOptions titleOptions = new FontOptions(20).color(entries.getInterfaceStyle().activeTextColor);
		FormLocalLabel title = new FormLocalLabel(entry.getLocalization(), titleOptions, -1, x + 16, y + 2, entries.getWidth() - 100);
		entries.addComponent(title);

		int completed = GuideJournalRegistry.getCompletedSectionCount(entry.getStringID(), client);
		int total = GuideJournalRegistry.getCompletableSectionCount(entry.getStringID(), client);
		if (total > 0) {
			FormLocalLabel progress = new FormLocalLabel(new StaticMessage(completed + "/" + total), titleOptions, 1, x + entries.getWidth() - 18, y + 2, 70);
			entries.addComponent(progress);
		}

		int height = Math.max(34, title.getHeight() + 10);
		FormMouseHover hover = new FormMouseHover(x + 6, y, entries.getWidth() - 20, height, true) {
			@Override
			public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
				super.draw(tickManager, perspective, renderBox);
			}
		};
		entries.addComponent(hover);
		hover.onClicked(event -> openEntry(journalForm, entry, client));

		FormBreakLine line = entries.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, x + 10, y + height + 2, entries.getWidth() - 30, true));
		line.color = new Color(80, 80, 80);
		return height + 12;
	}

	public static void openEntry(JournalContainerForm journalForm, GuideJournalEntry entry, Client client) {
		try {
			Field field = JournalContainerForm.class.getDeclaredField("formJournalEntryComponent");
			field.setAccessible(true);
			FormJournalEntryComponent component = (FormJournalEntryComponent)field.get(journalForm);
			if (component != null) {
				component.setupBiomeData(entry, entry.biomeLoot, client);
				component.entryContextBox.setScrollY(0);
				JournalContainerForm.lastOpenEntryScroll = 0;
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to open Dynamic Settlements journal entry", e);
		}
	}

	public static void renderEntry(GuideJournalEntry entry, Client client, FormContentBox contentBox) {
		JournalContainerForm.lastOpenBiomeEntry = entry.getStringID();
		JournalContainerForm.lastOpenMobEntry = null;
		contentBox.clearComponents();

		int width = contentBox.getWidth() - contentBox.getScrollBarWidth();
		int y = 8;
		Color textColor = contentBox.getInterfaceStyle().activeTextColor;

		FormLocalLabel entryTitle = new FormLocalLabel(entry.getLocalization(), new FontOptions(20).color(textColor), -1, 10, y, width - 25);
		contentBox.addComponent(entryTitle);
		y += entryTitle.getHeight() + 14;
		FormBreakLine titleLine = contentBox.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 5, y, width - 15, true));
		titleLine.color = new Color(0, 0, 0);
		y += 16;

		for (GuideJournalRegistry.SectionEntry section : GuideJournalRegistry.getSections(entry.getStringID())) {
			if (!section.isVisible(client)) continue;

			boolean collapsed = isSectionCollapsed(section);
			int toggleX = width - 26;

			FormContentIconButton toggle = new FormContentIconButton(
				toggleX,
				y,
				FormInputSize.SIZE_20,
				ButtonColor.BASE,
				collapsed ? contentBox.getInterfaceStyle().button_collapsed_16 : contentBox.getInterfaceStyle().button_expanded_16,
				new StaticMessage[0]
			);
			contentBox.addComponent(toggle);
			toggle.onClicked(event -> {
				int scrollY = contentBox.getScrollY();
				toggleSectionCollapsed(section);
				renderEntry(entry, client, contentBox);
				contentBox.setScrollY(scrollY);
				JournalContainerForm.lastOpenEntryScroll = scrollY;
			});

			int checkboxWidth = section.hasCompletionState() && section.isCompleted(client) ? 24 : 0;
			int sectionTitleWidth = width - 43 - checkboxWidth;
			FormLocalLabel sectionTitle = new FormLocalLabel(section.title, new FontOptions(18).color(textColor), -1, 14, y + 2, sectionTitleWidth);
			contentBox.addComponent(sectionTitle);

			if (checkboxWidth > 0) {
				FormContentIconButton completedIcon = new FormContentIconButton(
					toggleX - 24,
					y,
					FormInputSize.SIZE_20,
					ButtonColor.BASE,
					contentBox.getInterfaceStyle().button_checked_20,
					new StaticMessage[0]
				);
				contentBox.addComponent(completedIcon);
			}

			y += Math.max(24, sectionTitle.getHeight() + 4);
			FormBreakLine sectionLine = contentBox.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 5, y, width - 15, true));
			sectionLine.color = new Color(80, 80, 80);
			y += 12;

			if (collapsed) {
				y += 10;
				continue;
			}

			for (int i = 0; i < section.body.length; i++) {
				FontOptions bodyOptions = new FontOptions(16).color(textColor);
				FormFairTypeLabel body = new FormFairTypeLabel(section.body[i], bodyOptions, FairType.TextAlign.LEFT, 14, y);
				body.setMaxWidth(width - 38);
				body.setParsers(
						TypeParsers.GAME_COLOR,
						TypeParsers.URL_OPEN,
						TypeParsers.MARKDOWN_URL,
						TypeParsers.InputIcon(bodyOptions),
						TypeParsers.ItemIcon(bodyOptions.getSize(), true, FairItemGlyph::onlyShowNameTooltip),
						TypeParsers.MobIcon(bodyOptions.getSize())
				);
				contentBox.addComponent(body);
				y += body.getBoundingBox().height + 10;
			}

			if (section.progressObjectives.length > 0) {
				y = addProgressObjectives(section, client, contentBox, width, y, textColor);
			}

			if (section.hasItemRequirements()) {
				y = addRequiredItems(section, client, contentBox, width, y, textColor);
			}

			if (section.challenge != null) {
				y = addCompletionRow(section, client, contentBox, width, y, textColor);
			}
			y += 18;
		}

		contentBox.setContentBox(new Rectangle(0, 0, contentBox.getWidth(), Math.max(contentBox.getHeight(), y + 12)));
		contentBox.setScrollY(JournalContainerForm.lastOpenEntryScroll);
	}

	private static boolean isSectionCollapsed(GuideJournalRegistry.SectionEntry section) {
		return GuideJournalFoldState.isCollapsed(section.challengeStringID);
	}

	private static void toggleSectionCollapsed(GuideJournalRegistry.SectionEntry section) {
		GuideJournalFoldState.toggle(section.challengeStringID);
	}

	public static int addProgressObjectives(GuideJournalRegistry.SectionEntry section, Client client, FormContentBox contentBox, int width, int y, Color textColor) {
		for (GuideJournalProgressObjective objective : section.progressObjectives) {
			boolean completed = objective.isCompleted(client);
			String text = objective.label.translate() + "  " + objective.getProgressText(client);
			FormFairTypeLabel label = new FormFairTypeLabel(new StaticMessage(text), new FontOptions(16), FairType.TextAlign.LEFT, 22, y);
			label.setMaxWidth(width - 46);
			label.setColor(() -> objective.isCompleted(client) ? Settings.UI.successTextColor : textColor);
			contentBox.addComponent(label);
			y += Math.max(22, label.getBoundingBox().height) + 4;
		}
		return y + 5;
	}

	public static int addRequiredItems(GuideJournalRegistry.SectionEntry section, Client client, FormContentBox contentBox, int width, int y, Color textColor) {
		FormLocalLabel title = new FormLocalLabel(new LocalMessage("journalguide", "requireditems"), new FontOptions(16).color(textColor), -1, 14, y, width - 38);
		contentBox.addComponent(title);
		y += title.getHeight() + 5;

		for (String itemStringID : section.requiredItemStringIDs) {
			String itemName = ItemRegistry.getItem(itemStringID).getNewLocalization().translate();
			FormFairTypeLabel itemLabel = new FormFairTypeLabel(new StaticMessage("[item=" + itemStringID + "] " + itemName), new FontOptions(16), FairType.TextAlign.LEFT, 22, y);
			itemLabel.setMaxWidth(width - 46);
			itemLabel.setParsers(TypeParsers.GAME_COLOR, TypeParsers.ItemIcon(16, true, FairItemGlyph::onlyShowNameTooltip));
			itemLabel.setColor(() -> client.characterStats.items_obtained.isItemObtained(itemStringID) ? Settings.UI.successTextColor : textColor);
			contentBox.addComponent(itemLabel);
			y += Math.max(22, itemLabel.getBoundingBox().height) + 3;
		}

		return y + 7;
	}

	public static int addCompletionRow(GuideJournalRegistry.SectionEntry section, Client client, FormContentBox contentBox, int width, int y, Color textColor) {
		LootList rewardList = new LootList();
		if (section.reward != null) section.challenge.addRewardsToList(rewardList, client);
		boolean hasReward = section.reward != null && !section.reward.items.isEmpty();

		Form row = new Form(width - 28, hasReward ? 62 : 40);
		row.setBackground(GameBackground.indent);
		row.setPosition(14, y);
		contentBox.addComponent(row);

		int buttonWidth = 170;
		if (hasReward) {
			row.addComponent(new FormLocalLabel("journal", "challengesreward", new FontOptions(16).color(textColor), -1, 6, 5));
			FairType rewardType = new FairType();
			FontOptions rewardOptions = new FontOptions(16).color(textColor);
			if (rewardList.addRewardsToFairType(rewardType, rewardOptions, true, false, (Function)null)) {
				rewardType.applyParsers(TypeParsers.ItemIcon(rewardOptions.getSize(), true));
				FormFairTypeLabel rewardLabel = new FormFairTypeLabel("", 6, 27);
				rewardLabel.setCustomFairType(rewardType).setMaxWidth(row.getWidth() - buttonWidth - 18);
				row.addComponent(rewardLabel);
			}
		}

		FormLocalTextButton button = new FormLocalTextButton(
				section.challenge.isCompleted(client) ? new LocalMessage("journalguide", "completedbutton") : section.buttonText,
				row.getWidth() - buttonWidth - 7,
				hasReward ? 26 : 8,
				buttonWidth,
				FormInputSize.SIZE_24,
				ButtonColor.BASE
		) {
			@Override
			public void draw(TickManager tickManager, PlayerMob perspective, Rectangle renderBox) {
				boolean completed = section.challenge.isCompleted(client);
				boolean requirementsMet = section.areItemRequirementsMet(client);
				setActive(!completed && requirementsMet);
				if (completed) setLocalization(new LocalMessage("journalguide", "completedbutton"));
				super.draw(tickManager, perspective, renderBox);
			}
		};
		row.addComponent(button);
		button.setActive(!section.challenge.isCompleted(client) && section.areItemRequirementsMet(client));
		button.onClicked(event -> {
			if (!section.areItemRequirementsMet(client)) return;
			button.setActive(false);
			client.network.sendPacket(new PacketCompleteJournalSection(section.challengeStringID));
		});
		return y + row.getHeight();
	}
}
