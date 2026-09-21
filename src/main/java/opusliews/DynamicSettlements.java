package opusliews;

import necesse.engine.loading.ClientLoader;
import necesse.engine.localization.message.LocalMessage;
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
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import opusliews.blueprint.BlueprintAreaLevelData;
import opusliews.buff.*;
import opusliews.charcoal.CharcoalProductionZone;
import opusliews.container.BlueprintWorkstationContainer;
import opusliews.container.CrudeWorkbenchContainer;
import opusliews.crafting.CraftingStationFeature;
import opusliews.crafting.CraftingTasksFeature;
import opusliews.damage.DamageRepairLevelData;
import opusliews.damage.WeatheringLevelData;
import opusliews.durability.ItemDurabilityRegistry;
import opusliews.earlygame.CrudeAnvilFeature;
import opusliews.earlygame.CrudeWorkbenchFeature;
import opusliews.earlygame.EarlyGameLevelData;
import opusliews.forms.BlueprintWorkstationContainerForm;
import opusliews.forms.CrudeWorkbenchContainerForm;
import opusliews.item.*;
import opusliews.jobs.CharcoalCleanupLevelJob;
import opusliews.jobs.CharcoalProductionLevelJob;
import opusliews.jobs.ConstructionLevelJob;
import opusliews.jobs.RepairLevelJob;
import opusliews.mobs.BuilderHumanMob;
import opusliews.network.*;
import opusliews.object.*;
import opusliews.settler.BuilderRequestLevelData;
import opusliews.settler.BuilderSettler;
import opusliews.sleep.SettlementSleepSettingsLevelData;
import opusliews.tile.*;

import java.util.ArrayList;
import java.util.List;

@ModEntry
public class DynamicSettlements {
	public static int blueprintWorkstationContainerID;
	public static int crudeWorkbenchContainerID;
	public static boolean debugBlueprintMaterialGrant = false;
	public static boolean SBCompatFailure = false;
	public static GameSound stoneTapSound;
	public static GameSound clayDigSound;
	public static GameSound clayDigFastSound;
	public static GameSound rockSlideSound;

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
				new LocalMessage("ui", "modincompatibilitymessage"),
				new FontOptions(20),
				0,
				form.getWidth() / 2,
				20,
				form.getWidth() - 30
		));

		FormLocalTextButton disableSettlementBuilders = form.addComponent(
				new FormLocalTextButton(
						new LocalMessage("ui", "disablesettlementbuildersrestart"),
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
						new LocalMessage("ui", "disabledynamicsettlementsrestart"),
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
		BuffRegistry.registerBuff(TrapdoorHiddenBuff.stringID, new TrapdoorHiddenBuff());
		BuffRegistry.registerBuff(DeepHoleHiddenBuff.stringID, new DeepHoleHiddenBuff());
		BuffRegistry.registerBuff(DeepHoleDiggingBuff.stringID, new DeepHoleDiggingBuff());
		BuffRegistry.registerBuff(DeepHoleCaveFallBuff.stringID, new DeepHoleCaveFallBuff());
		BuffRegistry.registerBuff(DeepHoleLadderDescentBuff.stringID, new DeepHoleLadderDescentBuff());
		SettlerRegistry.registerSettler("builder", new BuilderSettler());
		MobRegistry.registerMob("builderhuman",
				BuilderHumanMob.class, true);

		CrudeWorkbenchFeature.register();
		CrudeAnvilFeature.register();
		DSItemRegistry.registerItems();
		ItemDurabilityRegistry.registerDurability();
		ItemRegistry.registerItem(DirtPileItem.stringID, new DirtPileItem(), 0.0F, true);
		ItemRegistry.registerItem("charcoal", new MatItem(500, Item.Rarity.NORMAL), 4.0F, true);
		ItemRegistry.registerItem(SharpenedStoneItem.stringID, new SharpenedStoneItem(), 0.5F, true);
		ItemRegistry.registerItem(FirestarterItem.stringID, new FirestarterItem(), 8.0F, true);
		ItemRegistry.registerItem(CrudeAxeItem.stringID, new CrudeAxeItem(), 6.0F, true);
		TileRegistry.registerTile(ShallowHoleTile.stringID, new ShallowHoleTile(), 0.0F, false);
		TileRegistry.registerTile(DeepHoleTile.stringID, new DeepHoleTile(), 0.0F, false);
		for (int itemCount = 1; itemCount <= FiringPitTile.maxItems; itemCount++) {
			TileRegistry.registerTile(FiringPitTile.getStringID(itemCount), new FiringPitTile(itemCount), 0.0F, false);
		}
		TileRegistry.registerTile(FiringPitLogTile.stringID, new FiringPitLogTile(), 0.0F, false);
		TileRegistry.registerTile(BurningFiringPitTile.stringID, new BurningFiringPitTile(), 0.0F, false);
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
		ObjectRegistry.registerObject(TrapdoorObject.openStringID, new TrapdoorObject(false), 20.0F, true);
		ObjectRegistry.registerObject(TrapdoorObject.closedStringID, new TrapdoorObject(true), 0.0F, false);
		ObjectRegistry.registerObject(HoleCaveLadderObject.stringID, new HoleCaveLadderObject(), 10.0F, true);
		ObjectRegistry.registerObject(HoleCaveLadderUpObject.stringID, new HoleCaveLadderUpObject(), 0.0F, false);
		ObjectRegistry.registerObject(DeepHoleCeilingLightObject.stringID, new DeepHoleCeilingLightObject(), 0.0F, false);
		PlacedLogRegistry.register();

		GameObject clayRock = ObjectRegistry.getObject("clayrock");
		clayRock.toolType = ToolType.SHOVEL;
		clayRock.toolTier = 0.0F;

		crudeWorkbenchContainerID = ContainerRegistry.registerSettlementDependantLOContainer(
				(client, uniqueSeed, settlement, levelObject, content) -> new CrudeWorkbenchContainerForm(
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
		PacketRegistry.registerPacket(PacketDeepHoleInteract.class);
		PacketRegistry.registerPacket(PacketHoleCaveLadderInteract.class);
		PacketRegistry.registerPacket(PacketCharcoalPitInteract.class);
		PacketRegistry.registerPacket(PacketFirestarterUse.class);
		PacketRegistry.registerPacket(PacketPlaceLog.class);
		PacketRegistry.registerPacket(PacketLogCutSound.class);
		PacketRegistry.registerPacket(PacketCharcoalProductionSettingsRequest.class);
		PacketRegistry.registerPacket(PacketCharcoalProductionSettingsUpdate.class);
		PacketRegistry.registerPacket(PacketCharcoalProductionSettingsSync.class);
		PacketRegistry.registerPacket(PacketClayDiggingSound.class);
		PacketRegistry.registerPacket(PacketCrudeWorkbenchOutput.class);
		PacketRegistry.registerPacket(PacketCrudeAnvilOutput.class);

		CraftingStationFeature.register();
		CraftingTasksFeature.register();
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
		clayDigSound = GameSound.fromFile("ClayDig");
		clayDigFastSound = GameSound.fromFile("ClayDigFast");
		rockSlideSound = GameSound.fromFile("RockSlide");
	}
}
