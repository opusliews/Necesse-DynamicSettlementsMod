package opusliews.object;

import java.awt.Rectangle;
import necesse.engine.localization.message.GameMessage;
import necesse.inventory.item.toolItem.ToolType;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;

public class DeepHoleCeilingLightObject extends GameObject {
	public static final String stringID = "deepholeceilinglight";

	public DeepHoleCeilingLightObject() {
		super(new Rectangle(0, 0, 0, 0));
		this.mapColor = null;
		this.toolType = ToolType.UNBREAKABLE;
		this.isLightTransparent = true;
		this.lightLevel = 75;
		this.hoverHitbox = new Rectangle(0, 0, 0, 0);
	}

	@Override
	public GameMessage preventsLadderPlacement(Level level, int tileX, int tileY) {
		return null;
	}

	@Override
	public boolean canBePlacedOn(Level level, int layerID, int x, int y, GameObject newObject, boolean ignoreOtherLayers) {
		return true;
	}
}
