package opusliews.zones;

import necesse.engine.gameTool.GameToolManager;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZone;
import necesse.level.maps.levelData.settlementData.zones.SettlementWorkZoneRegistry;
import necesse.gfx.forms.presets.containerComponent.settlement.CreateOrExpandWorkZoneGameTool;
import necesse.gfx.forms.presets.containerComponent.settlement.SettlementAssignWorkForm;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.stream.Stream;

public class LevelAwareWorkZoneTool extends CreateOrExpandWorkZoneGameTool {
	private final SettlementAssignWorkForm form;
	private final int zoneID;
	private final HashMap<Integer, SettlementWorkZone> zones;

	public LevelAwareWorkZoneTool(SettlementAssignWorkForm form, int zoneID, HashMap<Integer, SettlementWorkZone> zones) {
		super(form.client.getLevel());
		this.form = form;
		this.zoneID = zoneID;
		this.zones = zones;
	}

	@Override
	public Stream<SettlementWorkZone> streamEditZones() {
		int settlementUniqueID = form.container.getSettlementUniqueID();
		return zones.values().stream()
				.filter(zone -> zone.getID() == zoneID)
				.filter(zone -> SettlementZoneClientCache.isWorkZoneOnLevel(settlementUniqueID, zone.getUniqueID(), level.getIdentifier()));
	}

	@Override
	public void onCreatedNewZone(Rectangle rectangle, Point anchor) {
		SettlementWorkZone zone = SettlementWorkZoneRegistry.getNewZone(zoneID);
		zone.expandZone(level, rectangle, anchor, (x, y) -> streamEditZones().anyMatch(other -> other.containsTile(x, y)));
		if (zone.shouldRemove()) return;
		zone.generateUniqueID(id -> zones.containsKey(id));
		zones.put(zone.getUniqueID(), zone);
		SettlementZoneClientCache.setLocalWorkOwner(form.container.getSettlementUniqueID(), zone.getUniqueID(), level.getIdentifier());
		form.container.createWorkZone.runAndSend(zone.getID(), zone.getUniqueID(), rectangle, anchor);
		SettlementWorkZoneClientUI.setRequestedConfig(form, zone.getUniqueID());
		SettlementWorkZoneClientUI.refreshHud(form);
	}

	@Override
	public void onRemovedZone(SettlementWorkZone zone, Rectangle rectangle) {
		if (!SettlementZoneClientCache.isWorkZoneOnLevel(form.container.getSettlementUniqueID(), zone.getUniqueID(), level.getIdentifier())) return;
		if (zone.shrinkZone(level, rectangle)) {
			form.container.shrinkWorkZone.runAndSend(zone.getUniqueID(), rectangle);
			if (zone.shouldRemove()) zones.remove(zone.getUniqueID());
			SettlementWorkZoneClientUI.refreshHud(form);
		}
	}

	@Override
	public void onExpandedZone(SettlementWorkZone zone, Rectangle rectangle, Point anchor) {
		if (!SettlementZoneClientCache.isWorkZoneOnLevel(form.container.getSettlementUniqueID(), zone.getUniqueID(), level.getIdentifier())) return;
		boolean updated = zone.expandZone(level, rectangle, anchor, (x, y) -> streamEditZones().anyMatch(other -> other != zone && other.containsTile(x, y)));
		if (updated) {
			form.container.expandWorkZone.runAndSend(zone.getUniqueID(), rectangle, anchor);
			SettlementWorkZoneClientUI.setRequestedConfig(form, zone.getUniqueID());
			SettlementWorkZoneClientUI.refreshHud(form);
		}
	}

	@Override
	public void isCancelled() {
		super.isCancelled();
		SettlementWorkZoneClientUI.setOverrideShowZones(form, false);
	}

	@Override
	public void isCleared() {
		super.isCleared();
		SettlementWorkZoneClientUI.setOverrideShowZones(form, false);
	}
}
