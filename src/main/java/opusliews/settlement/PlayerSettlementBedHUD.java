package opusliews.settlement;

import java.util.List;

import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.network.client.Client;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.DrawOptions;
import necesse.gfx.drawOptions.human.HumanDrawOptions;
import necesse.gfx.drawables.SortedDrawable;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementContainerForm;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.inventory.container.settlement.data.SettlementSettlerData;
import necesse.level.maps.Level;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.levelData.settlementData.settler.Settler;
import opusliews.logging.Logging;
import opusliews.settlement.SettlementPlayerBedSystem.ClientAssignment;

public final class PlayerSettlementBedHUD {
	private PlayerSettlementBedHUD() {
	}

	public static void attach(SettlementContainerForm form, Client client, int settlementUniqueID) {
		if (form == null || client == null || client.getLevel() == null) {
			if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedHUD] Cannot attach player bed HUD because form/client/level is unavailable settlement=" + settlementUniqueID);
			return;
		}

		HudDrawElement element = new HudDrawElement() {
			@Override
			public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
				if (form.isDisposed()) {
					remove();
					return;
				}
				if (!form.shouldShowBedIcons()) return;
				Level level = client.getLevel();
				if (level == null) return;
				List<ClientAssignment> assignments = SettlementPlayerBedSystem.getClientAssignments(settlementUniqueID);
				for (ClientAssignment assignment : assignments) {
					if (assignment == null || assignment.levelIdentifier == null || !assignment.levelIdentifier.equals(level.getIdentifier())) continue;
					final DrawOptions drawOptions = SettlementSettlerData.getSettlerFlagDrawOptionsTile(assignment.tileX, assignment.tileY, camera,
							(size, drawX, drawY) -> getPlayerFaceDrawOptions(assignment, size, drawX, drawY));
					final boolean mouseOver = camera.getMouseLevelTilePosX() == assignment.tileX && camera.getMouseLevelTilePosY() == assignment.tileY;
					list.add(new SortedDrawable() {
						@Override
						public int getPriority() {
							return Integer.MAX_VALUE;
						}

						@Override
						public void draw(TickManager tickManager) {
							drawOptions.draw();
							if (mouseOver && assignment.playerName != null && !assignment.playerName.isEmpty()) {
								GameTooltipManager.addTooltip(new StringTooltips(assignment.playerName), TooltipLocation.FORM_FOCUS);
							}
						}
					});
				}
			}
		};

		client.getLevel().hudManager.addElement(element);
		if (Logging.logEnabled) Logging.logMessage("[PlayerSettlementBedHUD] Attached player bed HUD settlement=" + settlementUniqueID + " level=" + client.getLevel().getIdentifier());
	}

	private static DrawOptions getPlayerFaceDrawOptions(ClientAssignment assignment, int size, int drawX, int drawY) {
		HumanDrawOptions options = new HumanDrawOptions(null, assignment.look, false);
		return Settler.getHumanFaceDrawOptions(options, size, drawX, drawY);
	}
}
