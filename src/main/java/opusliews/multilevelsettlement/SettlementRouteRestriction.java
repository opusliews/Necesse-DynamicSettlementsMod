package opusliews.multilevelsettlement;

import necesse.engine.util.LevelIdentifier;

@FunctionalInterface
public interface SettlementRouteRestriction {
	SettlementRouteRestriction ALLOW_ALL = (levelIdentifier, tileX, tileY) -> true;

	boolean isTileAllowed(LevelIdentifier levelIdentifier, int tileX, int tileY);
}
