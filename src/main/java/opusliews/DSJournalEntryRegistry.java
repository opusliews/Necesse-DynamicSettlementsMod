package opusliews;

import necesse.engine.localization.message.LocalMessage;
import necesse.gfx.fairType.TypeParsers;
import necesse.inventory.InventoryItem;
import opusliews.journal.GuideJournalProgressObjective;
import opusliews.journal.GuideJournalRegistry;
import opusliews.progression.EarlyHealthProgressionSystem;
import opusliews.progression.GuideProgressionSystem;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

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


		GuideJournalRegistry.registerButtonSectionAfterObjective(
				categoryStringID, "primitivecrafting",
				new LocalMessage("journalguide", "primitivecraftingtitle"),
				new LocalMessage("journalguide", "primitivecraftingbody"),
				new LocalMessage("journalguide", "completedbutton"), null, null, "guide9"
		);

		GuideJournalRegistry.registerButtonSectionAfterObjective(
				categoryStringID, "componentcrafting",
				new LocalMessage("journalguide", "componentcraftingtitle"),
				new LocalMessage("journalguide", "componentcraftingbody"),
				new LocalMessage("journalguide", "completedbutton"), null, null, "guide26"
		);

		GuideJournalRegistry.registerButtonSectionAfterObjective(
				categoryStringID, "interfacecontrols",
				new LocalMessage("journalguide", "interfacecontrolstitle"),
				new LocalMessage("journalguide", "interfacecontrolsbody"),
				new LocalMessage("journalguide", "completedbutton"), null, null, "guide1"
		);

		String[] triggeredSections = {
				"hidingholes", "moldsanddurability", "moldenchanting", "foundstations", "treasureshovel", "farmchanges", "woodashfarmland",
				"settlementintro", "happinesswork", "builders", "blueprintcreation", "blueprintconstruction",
				"blueprinttools", "projectmanagement", "inspectionglass", "travellingbuilder", "stockmanagement",
				"carpenters", "workstationlinks", "craftingtasks", "craftingautostock", "automatedmetalworking",
				"charcoalautomation", "clayautomation", "fishingareas", "settlementdefence", "warningbells",
				"guardduty", "guardfatigue", "malignance", "sleeping", "sleepalarms", "cavesettlements",
				"caveresidents", "caveinfrastructure", "crosslevellogistics", "crosslevelcommands", "caveguards",
				"undergroundthreats", "multilevelraids", "starvation", "winteriscoming"
		};
		for (String section : triggeredSections) GuideProgressionSystem.registerRevealChallenge(section);

		registerTriggeredButton("hidingholes");
		registerTriggeredButton("moldsanddurability");
		GuideJournalRegistry.registerAutoSectionAfterChallenge(
				categoryStringID,
				"moldenchanting",
				new LocalMessage("journalguide", "moldenchantingtitle"),
				new LocalMessage("journalguide", "moldenchantingbody"),
				GuideProgressionSystem.revealChallengeID("moldenchanting")
		);
		registerTriggeredAuto("foundstations");
		registerTriggeredButton("treasureshovel");
		registerTriggeredButton("farmchanges");
		GuideJournalRegistry.registerAutoSectionAfterChallenge(
				categoryStringID,
				"woodashfarmland",
				new LocalMessage("journalguide", "woodashfarmlandtitle"),
				new LocalMessage("journalguide", "woodashfarmlandbody"),
				GuideProgressionSystem.revealChallengeID("woodashfarmland")
		);
		registerTriggeredButton("settlementintro");
		registerTriggeredButton("happinesswork");
		registerTriggeredButton("builders");
		registerTriggeredAuto("blueprintcreation");
		registerTriggeredAuto("blueprintconstruction");
		registerTriggeredButton("blueprinttools");
		registerTriggeredButton("projectmanagement");
		registerTriggeredButton("inspectionglass");
		registerTriggeredButton("travellingbuilder");
		registerTriggeredAuto("stockmanagement");
		registerTriggeredButton("carpenters");
		registerTriggeredAuto("workstationlinks");
		registerTriggeredButton("craftingtasks");
		registerTriggeredButton("craftingautostock");
		registerTriggeredAuto("automatedmetalworking");
		registerTriggeredAuto("charcoalautomation");
		registerTriggeredAuto("clayautomation");
		registerTriggeredButton("fishingareas");
		registerTriggeredAuto("settlementdefence");
		registerTriggeredButton("warningbells");
		registerTriggeredAuto("guardduty");
		registerTriggeredButton("guardfatigue");
		registerTriggeredButton("malignance");
		registerTriggeredButton("sleeping");
		registerTriggeredAuto("sleepalarms");
		registerTriggeredAuto("cavesettlements");
		registerTriggeredButton("caveresidents");
		registerTriggeredButton("caveinfrastructure");
		registerTriggeredButton("crosslevellogistics");
		registerTriggeredButton("crosslevelcommands");
		registerTriggeredButton("caveguards");
		registerTriggeredButton("undergroundthreats");
		registerTriggeredButton("multilevelraids");
		registerTriggeredButton("starvation");
		registerTriggeredButton("winteriscoming");


		// IMPORTANT, KEEP THIS REGISTRATION AT THE VERY END OF JOURNAL REGISTRATIONS
		GuideJournalRegistry.registerDiscoveryChallenge(
				WorldgenLockedContainerSystem.discoveryChallengeStringID
		);

		GuideJournalRegistry.registerAnyItemProgressSectionAfterChallenge(
				categoryStringID,
				"lockedcontainers",
				new LocalMessage("journalguide", "lockedcontainerstitle"),
				new LocalMessage[]{
						new LocalMessage("journalguide", "lockedcontainersbody")
				},
				new LocalMessage("journalguide", "lockedcontainersfindkey"),
				WorldgenLockedContainerSystem.discoveryChallengeStringID,
				"demonickey",
				"runickey",
				"ivykey",
				"quartzkey",
				"tungstenkey",
				"glacialkey",
				"dryadkey",
				"myceliumkey",
				"ancientfossilkey"
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

	private static void registerTriggeredButton(String sectionStringID) {
		GuideJournalRegistry.registerButtonSectionAfterChallenge(
				categoryStringID,
				sectionStringID,
				new LocalMessage("journalguide", sectionStringID + "title"),
				new LocalMessage("journalguide", sectionStringID + "body"),
				new LocalMessage("journalguide", "completedbutton"),
				GuideProgressionSystem.revealChallengeID(sectionStringID)
		);
	}

	private static void registerTriggeredAuto(String sectionStringID) {
		GuideJournalRegistry.registerButtonSectionAfterChallenge(
				categoryStringID,
				sectionStringID,
				new LocalMessage("journalguide", sectionStringID + "title"),
				new LocalMessage("journalguide", sectionStringID + "body"),
				new LocalMessage("journalguide", "completedbutton"),
				GuideProgressionSystem.revealChallengeID(sectionStringID)
		);
	}

}
