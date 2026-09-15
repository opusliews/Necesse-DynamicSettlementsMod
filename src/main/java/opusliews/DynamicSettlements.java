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
import necesse.engine.window.GameWindow;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.job.JobType;
import necesse.gfx.forms.components.localComponents.FormLocalLabel;
import necesse.gfx.forms.components.localComponents.FormLocalTextButton;
import necesse.gfx.forms.presets.ContinueForm;
import necesse.gfx.forms.presets.ModsForm;
import necesse.gfx.gameFont.FontOptions;
import opusliews.blueprint.BlueprintAreaLevelData;
import opusliews.buff.MalignanceGogglesBuff;
import opusliews.container.BlueprintWorkstationContainer;
import opusliews.crafting.AnvilCraftingFeature;
import opusliews.crafting.AnvilCraftingTasksFeature;
import opusliews.damage.DamageRepairLevelData;
import opusliews.damage.WeatheringLevelData;
import opusliews.forms.BlueprintWorkstationContainerForm;
import opusliews.item.MalignanceGogglesItem;
import opusliews.jobs.ConstructionLevelJob;
import opusliews.jobs.RepairLevelJob;
import opusliews.mobs.BuilderHumanMob;
import opusliews.network.*;
import opusliews.object.BlueprintWorkstationObject;
import opusliews.object.BlueprintWorkstationObjectEntity;
import opusliews.object.BuilderJobRequestBulletinObject;
import opusliews.object.WarningBellObject;
import opusliews.settler.BuilderRequestLevelData;
import opusliews.settler.BuilderSettler;
import opusliews.sleep.SettlementSleepSettingsLevelData;

import java.util.ArrayList;
import java.util.List;

@ModEntry
public class DynamicSettlements {
	public static int blueprintWorkstationContainerID;
	public static boolean debugBlueprintMaterialGrant = false;
	public static boolean SBCompatFailure = false;

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

		DSItemRegistry.registerItems();

		ObjectRegistry.registerObject("blueprintworkstation",
				new BlueprintWorkstationObject(), 100.0F, true);
		ObjectRegistry.registerObject(
				BuilderJobRequestBulletinObject.stringID,
				new BuilderJobRequestBulletinObject(), 25.0F, true);
		ObjectRegistry.registerObject(
				WarningBellObject.stringID,
				new WarningBellObject(), 60.0F, true);

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

		JobTypeRegistry.registerType(
				"construction",
				new JobType(
						true,
						true,
						new LocalMessage("jobs", "constructionname"),
						new LocalMessage("jobs", "constructiontip")
				)
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

		AnvilCraftingFeature.register();
		AnvilCraftingTasksFeature.register();
	}

	public void postInit() {
		if (SBCompatFailure) {
			return;
		}

		DSRecipeRegistry.registerRecipes();
	}
}
