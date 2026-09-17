package opusliews;

import necesse.engine.loading.ClientLoader;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.localization.message.StaticMessage;
import necesse.engine.modLoader.LoadedMod;
import necesse.engine.modLoader.ModListData;
import necesse.engine.modLoader.ModLoader;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.network.PacketReader;
import necesse.engine.registries.*;
import necesse.engine.sound.gameSound.GameSound;
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.job.JobType;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.forms.presets.ModsForm;
import necesse.gfx.gameFont.FontOptions;
import necesse.inventory.item.Item;
import necesse.inventory.item.matItem.MatItem;
import opusliews.blueprint.BlueprintAreaLevelData;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.jobs.CharcoalCleanupLevelJob;
import opusliews.jobs.CharcoalProductionLevelJob;
import opusliews.buff.MalignanceGogglesBuff;
import opusliews.container.BlueprintWorkstationContainer;
import opusliews.container.CrudeWorkbenchContainer;
import opusliews.crafting.AnvilCraftingFeature;
import opusliews.crafting.AnvilCraftingTasksFeature;
import opusliews.damage.DamageRepairLevelData;
import opusliews.damage.WeatheringLevelData;
import opusliews.earlygame.CrudeWorkbenchFeature;
import opusliews.earlygame.EarlyGameLevelData;
import opusliews.forms.BlueprintWorkstationContainerForm;
import necesse.gfx.forms.presets.containerComponent.object.CraftingStationContainerForm;
import opusliews.item.CrudeAxeItem;
import opusliews.item.DirtPileItem;
import opusliews.item.FirestarterItem;
import opusliews.item.MalignanceGogglesItem;
import opusliews.item.SharpenedStoneItem;
import opusliews.jobs.ConstructionLevelJob;
import opusliews.jobs.RepairLevelJob;
import opusliews.mobs.BuilderHumanMob;
import opusliews.network.*;
import opusliews.object.BlueprintWorkstationObject;
import opusliews.object.BlueprintWorkstationObjectEntity;
import opusliews.object.BuilderJobRequestBulletinObject;
import opusliews.object.PlacedLogRegistry;
import opusliews.object.WarningBellObject;
import opusliews.settler.BuilderRequestLevelData;
import opusliews.settler.BuilderSettler;
import opusliews.sleep.SettlementSleepSettingsLevelData;
import opusliews.tile.*;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;

import java.util.ArrayList;
import java.util.List;

@ModEntry
public class DynamicSettlements {
	public static int blueprintWorkstationContainerID;
	public static int crudeWorkbenchContainerID;
	public static boolean debugBlueprintMaterialGrant = false;
	public static boolean SBCompatFailure = false;
	public static GameSound stoneTapSound;

	public void preInit() {
		boolean settlementBuildersLoaded = ModLoader.getEnabledMods().stream()
				.anyMatch(mod -> mod.id.equals("opusliews.settlementbuilders"));

		if (!settlementBuildersLoaded) {
			return;
		}

		SBCompatFailure = true;
		setupIncompatibilityNotice();
	}

	private void setupIncompatibilityNotice() {
		ContinueForm form = new ContinueForm(
				"dynamicsettlementsincompatibility",
				520,
				300
		) {
			@Override
			public void onWindowResized(GameWindow window) {
				super.onWindowResized(window);
				setPosMiddle(window.getHudWidth() / 2, window.getHudHeight() / 2);
			}

			@Override
			public boolean canContinue() {
				return false;
			}
		};

		form.addComponent(new FormLocalLabel(
				new StaticMessage(
						"\"Dynamic Settlements\" cannot be loaded alongside \"Settlement Builders\".\n\n"
								+ "\"Dynamic Settlements\" already includes all \"Settlement Builders\" features.\n\n"
								+ "Choose which mod you want to disable:"
				),
				new FontOptions(20),
				0,
				form.getWidth() / 2,
				20,
				form.getWidth() - 30
		));

		FormLocalTextButton disableSettlementBuilders = form.addComponent(
				new FormLocalTextButton(
						new StaticMessage("Disable \"Settlement Builders\" and restart"),
						20,
						175,
						form.getWidth() - 40
				)
		);

		disableSettlementBuilders.onClicked(event -> {
			disableModAndRestart("opusliews.settlementbuilders");
		});

		FormLocalTextButton disableDynamicSettlements = form.addComponent(
				new FormLocalTextButton(
						new StaticMessage("Disable \"Dynamic Settlements\" and restart"),
						20,
						225,
						form.getWidth() - 40
				)
		);

		disableDynamicSettlements.onClicked(event -> {
			disableModAndRestart("opusliews.dynamicsettlements");
		});

		ClientLoader.loadingNoticeForms.add(form);
	}

	private void disableModAndRestart(String modID) {
		List<ModListData> modList = new ArrayList<>();

		for (LoadedMod mod : ModLoader.getAllMods()) {
			ModListData data = new ModListData(mod);

			if (mod.id.equals(modID)) {
				data.enabled = false;
			}

			modList.add(data);
		}

		ModLoader.saveModListSettings(modList);

		Runnable restart = ModsForm.restartGameRunnable();

		if (restart != null) {
			restart.run();
		} else {
			WindowManager.getWindow().requestClose();
		}
	}

	public void init() {
		if (SBCompatFailure) {
			return;
		}
		// Registrations
		BuffRegistry.registerBuff(MalignanceGogglesItem.buffStringID, new MalignanceGogglesBuff());
		SettlerRegistry.registerSettler("builder", new BuilderSettler());
		MobRegistry.registerMob("builderhuman",
				BuilderHumanMob.class, true);

		CrudeWorkbenchFeature.register();
		DSItemRegistry.registerItems();
		ItemRegistry.registerItem(DirtPileItem.stringID, new DirtPileItem(), 0.0F, true);
		ItemRegistry.registerItem("charcoal", new MatItem(500, Item.Rarity.NORMAL), 4.0F, true);
		ItemRegistry.registerItem(SharpenedStoneItem.stringID, new SharpenedStoneItem(), 0.5F, true);
		ItemRegistry.registerItem(FirestarterItem.stringID, new FirestarterItem(), 8.0F, true);
		ItemRegistry.registerItem(CrudeAxeItem.stringID, new CrudeAxeItem(), 6.0F, true);
		TileRegistry.registerTile(ShallowHoleTile.stringID, new ShallowHoleTile(), 0.0F, false);
		TileRegistry.registerTile(CharcoalPitTile.stringID, new CharcoalPitTile(), 0.0F, false);
		TileRegistry.registerTile(CoveredCharcoalPitTile.stringID, new CoveredCharcoalPitTile(), 0.0F, false);
		TileRegistry.registerTile(BurningCharcoalPitTile.stringID, new BurningCharcoalPitTile(), 0.0F, false);

		ObjectRegistry.registerObject("blueprintworkstation",
				new BlueprintWorkstationObject(), 100.0F, true);
		ObjectRegistry.registerObject(
				BuilderJobRequestBulletinObject.stringID,
				new BuilderJobRequestBulletinObject(), 25.0F, true);
		ObjectRegistry.registerObject(
				WarningBellObject.stringID,
				new WarningBellObject(), 60.0F, true);
		PlacedLogRegistry.register();

		crudeWorkbenchContainerID = ContainerRegistry.registerSettlementDependantLOContainer(
				(client, uniqueSeed, settlement, levelObject, content) -> new CraftingStationContainerForm(
						client,
						new CrudeWorkbenchContainer(client.getClient(), uniqueSeed, settlement, levelObject, new PacketReader(content))
				),
				(client, uniqueSeed, settlement, levelObject, content, serverObject) -> new CrudeWorkbenchContainer(
						client, uniqueSeed, settlement, levelObject, new PacketReader(content)
				)
		);

		blueprintWorkstationContainerID = ContainerRegistry.registerSettlementDependantOEContainer(
				(client, uniqueSeed, settlement, oe, content) -> new BlueprintWorkstationContainerForm(
						client,
						new BlueprintWorkstationContainer(
								client.getClient(),
								uniqueSeed,
								settlement,
								(BlueprintWorkstationObjectEntity)oe,
								new PacketReader(content)
						)
				),
				(client, uniqueSeed, settlement, oe, content, serverObject) -> new BlueprintWorkstationContainer(
						client,
						uniqueSeed,
						settlement,
						(BlueprintWorkstationObjectEntity)oe,
						new PacketReader(content)
				)
		);

		LevelDataRegistry.registerLevelData(BlueprintAreaLevelData.managerKey, BlueprintAreaLevelData.class);
		LevelDataRegistry.registerLevelData(DamageRepairLevelData.managerKey, DamageRepairLevelData.class);
		LevelDataRegistry.registerLevelData(WeatheringLevelData.managerKey, WeatheringLevelData.class);
		LevelDataRegistry.registerLevelData(BuilderRequestLevelData.managerKey, BuilderRequestLevelData.class);
		LevelDataRegistry.registerLevelData(SettlementSleepSettingsLevelData.managerKey, SettlementSleepSettingsLevelData.class);
		LevelDataRegistry.registerLevelData(CharcoalPitLevelData.managerKey, CharcoalPitLevelData.class);
		LevelDataRegistry.registerLevelData(EarlyGameLevelData.managerKey, EarlyGameLevelData.class);

		JobTypeRegistry.registerType(
				"construction",
				new JobType(
						true,
						true,
						new LocalMessage("jobs", "constructionname"),
						new LocalMessage("jobs", "constructiontip")
				)
		);

		JobTypeRegistry.registerType(
				"firing",
				new JobType(
						true,
						false,
						new LocalMessage("jobs", "firingname"),
						new LocalMessage("jobs", "firingtip")
				)
		);

		SettlementWorkZoneRegistry.registerZone(CharcoalProductionZone.stringID, CharcoalProductionZone.class);

		LevelJobRegistry.registerJob(
				CharcoalProductionZone.stringID,
				CharcoalProductionLevelJob.class,
				CharcoalProductionLevelJob::handler,
				"firing"
		);

		LevelJobRegistry.registerJob(
				"charcoalcleanup",
				CharcoalCleanupLevelJob.class,
				CharcoalCleanupLevelJob::handler,
				"firing"
		);

		LevelJobRegistry.registerJob(
				"construction",
				ConstructionLevelJob.class,
				ConstructionLevelJob::handler,
				"construction"
		);

		LevelJobRegistry.registerJob(
				"repair",
				RepairLevelJob.class,
				RepairLevelJob::handler,
				"construction"
		);

		PacketRegistry.registerPacket(PacketBlueprintUpdate.class);
		PacketRegistry.registerPacket(PacketPlaceBlueprintArea.class);
		PacketRegistry.registerPacket(PacketRequestBlueprintAreas.class);
		PacketRegistry.registerPacket(PacketSyncBlueprintAreas.class);
		PacketRegistry.registerPacket(PacketAddBlueprintArea.class);
		PacketRegistry.registerPacket(PacketRemoveBlueprintArea.class);
		PacketRegistry.registerPacket(PacketBuilderTilePlaceSound.class);
		PacketRegistry.registerPacket(PacketBuilderObjectPlaceSound.class);
		PacketRegistry.registerPacket(PacketEraseBlueprintProject.class);
		PacketRegistry.registerPacket(PacketBlueprintBlockedState.class);
		PacketRegistry.registerPacket(PacketBuilderRoadRepairToggle.class);
		PacketRegistry.registerPacket(PacketRequestInspectionGlassData.class);
		PacketRegistry.registerPacket(PacketInspectionGlassData.class);
		PacketRegistry.registerPacket(PacketWarningBellRing.class);
		PacketRegistry.registerPacket(PacketGuardDutyToggle.class);
		PacketRegistry.registerPacket(PacketGuardFatigueUpdate.class);
		PacketRegistry.registerPacket(PacketSettlementSleepSettingsRequest.class);
		PacketRegistry.registerPacket(PacketSettlementSleepSettingsSync.class);
		PacketRegistry.registerPacket(PacketSettlementSleepSettingsUpdate.class);
		PacketRegistry.registerPacket(PacketDigShallowHole.class);
		PacketRegistry.registerPacket(PacketCharcoalPitInteract.class);
		PacketRegistry.registerPacket(PacketFirestarterUse.class);
		PacketRegistry.registerPacket(PacketPlaceLog.class);
		PacketRegistry.registerPacket(PacketLogCutSound.class);
		PacketRegistry.registerPacket(PacketCharcoalProductionSettingsRequest.class);
		PacketRegistry.registerPacket(PacketCharcoalProductionSettingsUpdate.class);
		PacketRegistry.registerPacket(PacketCharcoalProductionSettingsSync.class);

		AnvilCraftingFeature.register();
		AnvilCraftingTasksFeature.register();
	}

	public void postInit() {
		if (SBCompatFailure) {
			return;
		}

		DSRecipeRegistry.registerRecipes();
		loadSounds();
	}

	public static void loadSounds() {
		stoneTapSound = GameSound.fromFile("StoneTap");
	}
}
