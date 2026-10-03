package opusliews.hud;

import java.util.List;
import necesse.engine.util.LevelIdentifier;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.camera.GameCamera;
import necesse.level.maps.hudManager.HudDrawElement;
import opusliews.zones.SettlementZoneClientCache;

/**
 * Wraps settlement work-zone HUD elements so each geometry is only rendered on
 * the level that owns that zone. Vanilla zones without an explicit owner are
 * surface-owned for backwards compatibility.
 */
public class SurfaceSettlementZoneHudDrawElement extends HudDrawElement {
	private final HudDrawElement delegate;
	private final int zoneUniqueID;

	public SurfaceSettlementZoneHudDrawElement(HudDrawElement delegate, int zoneUniqueID) {
		this.delegate = delegate;
		this.zoneUniqueID = zoneUniqueID;
	}

	@Override
	public void addDrawables(List list, GameCamera camera, PlayerMob perspective) {
		if (delegate == null || perspective == null || perspective.getLevel() == null) return;
		LevelIdentifier current = perspective.getLevel().getIdentifier();
		LevelIdentifier owner = SettlementZoneClientCache.findWorkOwner(zoneUniqueID);
		if (owner == null) {
			if (!current.isSurface()) return;
		}
		else if (!owner.equals(current)) return;
		delegate.addDrawables(list, camera, perspective);
	}
}
