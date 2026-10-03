package opusliews.hud;

import necesse.engine.GlobalData;
import necesse.engine.state.MainGame;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.util.Zoning;
import necesse.gfx.camera.GameCamera;
import necesse.gfx.drawOptions.texture.SharedTextureDrawOptions;
import opusliews.zones.SettlementZoneClientCache;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.function.Supplier;

public class LevelAwareRestrictZoning extends Zoning {
	private final int zoneUniqueID;
	private final Supplier<Rectangle> limits;

	public LevelAwareRestrictZoning(int zoneUniqueID, Supplier<Rectangle> limits, Zoning source) {
		this.zoneUniqueID = zoneUniqueID;
		this.limits = limits;
		if (source != null) {
			for (Object value : source.getTileRectangles()) addRectangle(new Rectangle((Rectangle)value));
		}
	}

	@Override
	public Rectangle getLimits() {
		return limits == null ? null : limits.get();
	}

	@Override
	public SharedTextureDrawOptions getDrawOptions(Color edgeColor, Color fillColor, GameCamera camera) {
		LevelIdentifier current = null;
		if (GlobalData.getCurrentState() instanceof MainGame) {
			MainGame game = (MainGame)GlobalData.getCurrentState();
			if (game.getClient() != null && game.getClient().getLevel() != null) current = game.getClient().getLevel().getIdentifier();
		}
		if (current == null) return null;
		LevelIdentifier owner = SettlementZoneClientCache.findRestrictOwner(zoneUniqueID);
		if (owner == null) {
			if (!current.isSurface()) return null;
		}
		else if (!owner.equals(current)) return null;
		return super.getDrawOptions(edgeColor, fillColor, camera);
	}
}
