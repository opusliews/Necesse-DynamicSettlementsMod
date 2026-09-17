package opusliews.charcoal;

import java.awt.Color;
import java.awt.Point;
import java.util.function.BooleanSupplier;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.engine.util.EventVariable;
import necesse.level.maps.hudManager.HudDrawElement;
import necesse.level.maps.levelData.settlementData.zones.SettlementTileTickZone;

public class CharcoalProductionZone extends SettlementTileTickZone {
	public static final String stringID = "charcoalproduction";
	public static final EventVariable hideZones = new EventVariable(false);

	@Override
	protected void handleTile(Point tile) {
		// TODO Adding jobs
	}

	@Override
	public boolean isHiddenSetting() {
		return (Boolean)hideZones.get();
	}

	@Override
	protected GameMessage getDefaultName(int number) {
		return new LocalMessage("ui", "charcoalproductionzonedefname", "number", number);
	}

	@Override
	public GameMessage getAbstractName() {
		return new LocalMessage("ui", "charcoalproductionzone");
	}

	@Override
	public HudDrawElement getHudDrawElement(int drawPriority, BooleanSupplier overrideShow) {
		return getHudDrawElement(drawPriority, overrideShow, new Color(92, 92, 92, 150), new Color(46, 46, 46, 75));
	}
}
