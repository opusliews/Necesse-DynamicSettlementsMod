package opusliews.multilevelsettlement;

import necesse.engine.util.LevelIdentifier;

import java.awt.Point;
import java.util.Objects;

public final class SettlementLevelPosition {
	public final LevelIdentifier levelIdentifier;
	public final int tileX;
	public final int tileY;

	public SettlementLevelPosition(LevelIdentifier levelIdentifier, int tileX, int tileY) {
		this.levelIdentifier = Objects.requireNonNull(levelIdentifier, "levelIdentifier");
		this.tileX = tileX;
		this.tileY = tileY;
	}

	public SettlementLevelPosition(LevelIdentifier levelIdentifier, Point tile) {
		this(levelIdentifier, tile.x, tile.y);
	}

	public Point getTilePoint() {
		return new Point(tileX, tileY);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof SettlementLevelPosition)) return false;
		SettlementLevelPosition other = (SettlementLevelPosition)obj;
		return tileX == other.tileX && tileY == other.tileY && levelIdentifier.equals(other.levelIdentifier);
	}

	@Override
	public int hashCode() {
		return Objects.hash(levelIdentifier, tileX, tileY);
	}

	@Override
	public String toString() {
		return levelIdentifier + "@" + tileX + "," + tileY;
	}
}
