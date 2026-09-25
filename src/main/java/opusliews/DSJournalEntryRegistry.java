package opusliews;

import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.fairType.TypeParsers;
import necesse.inventory.InventoryItem;
import opusliews.journal.GuideJournalRegistry;
import opusliews.journal.GuideJournalProgressObjective;
import opusliews.progression.EarlyHealthProgressionSystem;

import java.util.List;
import java.util.stream.Collectors;

import static necesse.engine.storyObjectives.objectives.CraftPickaxeStoryObjective.LOG_ITEMS;
import static opusliews.DSStoryObjectiveRegistry.getRotatingItemIcon;

public class DSJournalEntryRegistry {
	public static final String categoryStringID = "dynamicsettlements";

	public static void registerEntries() {
		String logIcon = TypeParsers.getItemsParseString((List)LOG_ITEMS.stream().map(InventoryItem::new).collect(Collectors.toList()));
		String moldIcon = getRotatingItemIcon(
				"unfiredingotmold",
				"unfiredpickaxeheadmold",
				"unfiredaxeheadmold",
				"unfiredshovelheadmold",
				"unfiredsickleblademold",
				"unfiredshearsblademold",
				"unfiredswordblademold",
				"unfiredthickplatemold",
				"unfiredsawblademold"
		);

		GuideJournalRegistry.registerCategory(
				categoryStringID,
				new LocalMessage("journalguide", "categorytitle"),
				null
		);

		GuideJournalRegistry.registerProgressSection(
				categoryStringID,
				"earlyhealth",
				new LocalMessage("journalguide", "earlyhealthtitle"),
				new LocalMessage("journalguide", "earlyhealthbody"),
				new GuideJournalProgressObjective(
						new LocalMessage("journalguide", "earlyhealthkills"),
						client -> EarlyHealthProgressionSystem.getGoalProgressText(client, EarlyHealthProgressionSystem.Goal.HOSTILE_KILLS),
						client -> EarlyHealthProgressionSystem.isGoalComplete(client, EarlyHealthProgressionSystem.Goal.HOSTILE_KILLS)
				),
				new GuideJournalProgressObjective(
						new LocalMessage("journalguide", "earlyhealthore"),
						client -> EarlyHealthProgressionSystem.getGoalProgressText(client, EarlyHealthProgressionSystem.Goal.ORE_MINED),
						client -> EarlyHealthProgressionSystem.isGoalComplete(client, EarlyHealthProgressionSystem.Goal.ORE_MINED)
				),
				new GuideJournalProgressObjective(
						new LocalMessage("journalguide", "earlyhealthfoods"),
						client -> EarlyHealthProgressionSystem.getGoalProgressText(client, EarlyHealthProgressionSystem.Goal.UNIQUE_FOODS),
						client -> EarlyHealthProgressionSystem.isGoalComplete(client, EarlyHealthProgressionSystem.Goal.UNIQUE_FOODS)
				),
				new GuideJournalProgressObjective(
						new LocalMessage("journalguide", "earlyhealthcave"),
						client -> EarlyHealthProgressionSystem.getGoalProgressText(client, EarlyHealthProgressionSystem.Goal.CAVE_TIME),
						client -> EarlyHealthProgressionSystem.isGoalComplete(client, EarlyHealthProgressionSystem.Goal.CAVE_TIME)
				)
		);

		GuideJournalRegistry.registerButtonSectionAfterObjective(
				categoryStringID,
				"journalguide22",
				new LocalMessage("journalguide", "journalguide22title"),
				new LocalMessage[]{
						new LocalMessage("journalguide", "journalguide22body",
								new Object[]{
										"logicon", logIcon,
										"moldicon", moldIcon
								})
				},
				new LocalMessage("journalguide", "completedbutton"),
				null,
				"guide22",
				"guide21",
				"charcoal",
				"sawblademold",
				"thickplatemold"
		);

		GuideJournalRegistry.registerButtonSectionAfterObjective(
				categoryStringID,
				"journalguide25",
				new LocalMessage("journalguide", "journalguide25title"),
				new LocalMessage[]{
						new LocalMessage("journalguide", "journalguide25body")
				},
				new LocalMessage("journalguide", "completedbutton"),
				null,
				"guide25",
				"guide24",
				"metalworkhammer"
		);


		/*
		Example informational section (no completion state and no reward):

		GuideJournalRegistry.registerInfoSection(
				categoryStringID,
				"exampleinfo",
				new LocalMessage("journalguide", "exampleinfotitle"),
				new LocalMessage[]{
						new LocalMessage("journalguide", "exampleinfobody1"),
						new LocalMessage("journalguide", "exampleinfobody2")
				}
		);

		Example button-completed section. The reward and story objective are both optional:

		GuideJournalRegistry.registerButtonSection(
				categoryStringID,
				"examplebutton",
				new LocalMessage("journalguide", "examplebuttontitle"),
				new LocalMessage[]{
						new LocalMessage("journalguide", "examplebuttonbody1"),
						new LocalMessage("journalguide", "examplebuttonbody2")
				},
				new LocalMessage("journalguide", "continuebutton"),
				null,       // Optional LootTable reward.
				"guide23"  // Optional story objective to complete. Use null for none.
		);

		Example section hidden until a story objective is completed:

		GuideJournalRegistry.registerInfoSectionAfterObjective(
				categoryStringID,
				"examplehidden",
				new LocalMessage("journalguide", "examplehiddentitle"),
				new LocalMessage("journalguide", "examplehiddenbody"),
				"guide24"
		);

		A button section can also be hidden until an objective is complete while keeping
		its optional item requirements independent:

		GuideJournalRegistry.registerButtonSectionAfterObjective(
				categoryStringID,
				"examplehiddenrequirements",
				new LocalMessage("journalguide", "examplehiddenrequirementstitle"),
				new LocalMessage("journalguide", "examplehiddenrequirementsbody"),
				new LocalMessage("journalguide", "completebutton"),
				null,
				"guide26", // Optional objective completed by this button.
				"guide25", // Section becomes visible after this objective is completed.
				"ironbar",
				"saw"
		);

		Example button section with optional required items. The player only needs to have
		obtained these items at least once; they are not consumed and do not need to remain
		in the inventory. The Complete button stays disabled until every item has been obtained:

		GuideJournalRegistry.registerButtonSection(
				categoryStringID,
				"examplerequirements",
				new LocalMessage("journalguide", "examplerequirementstitle"),
				new LocalMessage("journalguide", "examplerequirementsbody"),
				new LocalMessage("journalguide", "completebutton"),
				null,
				"guide24",
				"ironbar",
				"woodenshaft",
				"saw"
		);
		*/
	}
}
