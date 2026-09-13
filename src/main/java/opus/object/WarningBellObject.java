package opus.object;

import java.awt.Color;
import java.awt.Point;

import necesse.gfx.gameTexture.GameTexture;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.WallTorchObject;
import necesse.level.maps.Level;

public class WarningBellObject extends WallTorchObject {
	public static final String stringID = "warningbell";

	public WarningBellObject() {
		super();
		this.disableParticles = true;
		this.lightLevel = 0;
		this.roomProperties.remove("lights");
		this.mapColor = new Color(214, 170, 45);
		this.objectHealth = 20;
		this.setItemCategory(new String[]{"objects", "misc"});
		this.setCraftingCategory(new String[]{"objects", "misc"});
	}

	@Override
	public void loadTextures() {
		super.loadTextures();
		this.texture = GameTexture.fromFile("objects/warningbell");
	}

	@Override
	public boolean attachesToObject(Level level, int tileX, int tileY) {
		GameObject object = level.getObject(tileX, tileY);
		return object.isFence || object.isWall && !object.isDoor;
	}

	@Override
	public boolean isValid(Level level, int layerID, int tileX, int tileY) {
		int rotation = level.getObjectRotation(layerID, tileX, tileY);
		Point attached = getAttachedTile(tileX, tileY, rotation);
		return attachesToObject(level, attached.x, attached.y);
	}

	@Override
	protected int getSprite(Level level, int tileX, int tileY, int rotation) {
		switch (rotation & 3) {
			case 0:
				return 2;
			case 1:
				return 3;
			case 2:
				return 0;
			default:
				return 1;
		}
	}

	@Override
	public int getLightLevel(Level level, int layerID, int tileX, int tileY) {
		return 0;
	}

	@Override
	public boolean isActive(Level level, int layerID, int tileX, int tileY) {
		return true;
	}

	public static Point getAttachedTile(int tileX, int tileY, int rotation) {
		switch (rotation & 3) {
			case 0:
				return new Point(tileX, tileY + 1);
			case 1:
				return new Point(tileX - 1, tileY);
			case 2:
				return new Point(tileX, tileY - 1);
			default:
				return new Point(tileX + 1, tileY);
		}
	}
}
