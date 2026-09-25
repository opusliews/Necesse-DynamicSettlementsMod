package opusliews.patches;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.state.MainGame;
import necesse.engine.window.GameWindow;
import necesse.inventory.container.AdventureJournalContainer;
import net.bytebuddy.asm.Advice;
import opusliews.blueprint.BlueprintAreaHud;
import opusliews.blueprint.BlueprintAreaSync;
import opusliews.forms.NewBlueprintForm;
import opusliews.hud.InspectionGlassHud;
import opusliews.hud.CraftingStationLinkHud;
import opusliews.item.BlueprintItem;

@ModMethodPatch(target= MainGame.class, name="frameTick", arguments={TickManager.class, GameWindow.class})
public class MainGamePatch {
	public static Client journalPausedClient;

	@Advice.OnMethodExit
	static void onExit(@Advice.This MainGame mainGame, @Advice.Argument(value=0) TickManager tickManager, @Advice.Argument(value=1) GameWindow window) {
		NewBlueprintForm.frameTick(mainGame, tickManager, window);
		BlueprintItem.frameTick(mainGame, tickManager, window);
		InspectionGlassHud.frameTick(mainGame);

		Client client = mainGame.getClient();
		if (client != null && client.isSingleplayer()) {
			boolean journalOpen = client.getContainer() instanceof AdventureJournalContainer;

			if (journalOpen && journalPausedClient == null && !client.isPaused()) {
				client.pause();
				journalPausedClient = client;
			} else if (!journalOpen && journalPausedClient == client) {
				client.resume();
				journalPausedClient = null;
			}
		} else if (journalPausedClient != null) {
			journalPausedClient = null;
		}

		if (mainGame.getClient() != null) {
			BlueprintAreaSync.frameTick(mainGame.getClient());

			if (mainGame.getClient().getLevel() != null) {
				BlueprintAreaHud.ensureAdded(mainGame.getClient().getLevel());
				InspectionGlassHud.ensureAdded(mainGame.getClient().getLevel());
				CraftingStationLinkHud.ensureAdded(mainGame.getClient().getLevel());
			}
		}
	}
}
