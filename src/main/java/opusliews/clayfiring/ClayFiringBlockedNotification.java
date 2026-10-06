package opusliews.clayfiring;

import necesse.engine.localization.Localization;
import necesse.engine.network.client.Client;
import necesse.gfx.GameBackground;
import necesse.gfx.GameColor;
import necesse.gfx.gameTooltips.BackgroundedGameTooltips;
import necesse.gfx.gameTooltips.GameTooltips;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.gfx.gameTooltips.SpacerGameTooltip;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.notifications.SettlementNotification;
import necesse.level.maps.levelData.settlementData.notifications.SettlementNotificationManager;
import necesse.level.maps.levelData.settlementData.notifications.SettlementNotificationSeverity;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import opusliews.logging.Logging;

public class ClayFiringBlockedNotification extends SettlementNotification {
	public static final String stringID = "clayfiringblocked";
	private static final String reasonKey = "reason";

	@Override
	public GameTooltips getTooltip(SettlementNotificationManager.ActiveNotification notification) {
		ListGameTooltips tooltips = new ListGameTooltips();
		tooltips.add(Localization.translate("ui", "notificationclayfiringblocked"), 400);
		String reason = notification.getGndData().getString(reasonKey, "");
		if (!reason.isEmpty()) {
			tooltips.add(new StringTooltips(Localization.translate("jobs", reason), GameColor.GRAY, 400));
		}
		tooltips.add(new SpacerGameTooltip(10));
		tooltips.add(new StringTooltips(Localization.translate("ui", "notificationclayfiringblockedresume"), GameColor.GRAY, 400));
		return new BackgroundedGameTooltips(tooltips, GameBackground.getItemTooltipBackground());
	}

	@Override
	public void onClicked(Client client, SettlementNotificationManager.ActiveNotification notification) {
	}

	@Override
	public boolean isStillValid(SettlerMob settlerMob) {
		return false;
	}

	@Override
	public boolean isStillValid(ServerSettlementData settlement) {
		if (settlement == null || settlement.getWorkZones() == null) return false;
		for (Object value : settlement.getWorkZones().getZones().values()) {
			if (value instanceof ClayFiringZone && ((ClayFiringZone)value).canProduce()) return true;
		}
		return false;
	}

	public static void submit(ServerSettlementData settlement, String translationKey) {
		if (settlement == null || settlement.networkData == null || translationKey == null || translationKey.isEmpty()) return;
		SettlementNotificationManager manager = settlement.networkData.notifications;
		SettlementNotificationManager.ActiveNotification notification = manager.submitNotification(stringID, settlement, SettlementNotificationSeverity.WARNING);
		String previous = notification.getGndData().getString(reasonKey, "");
		if (!translationKey.equals(previous)) {
			notification.getGndData().setString(reasonKey, translationKey);
			if (Logging.logEnabled) Logging.logMessage("[ClayFiringNotification] Blocked settlement=" + settlement.uniqueID + " reason=" + translationKey);
		}
	}

	public static void clear(ServerSettlementData settlement) {
		if (settlement == null || settlement.networkData == null) return;
		if (settlement.networkData.notifications.getGNDData(stringID) == null) return;
		settlement.networkData.notifications.removeNotification(stringID, settlement);
		if (Logging.logEnabled) Logging.logMessage("[ClayFiringNotification] Cleared blocked notification settlement=" + settlement.uniqueID);
	}
}
