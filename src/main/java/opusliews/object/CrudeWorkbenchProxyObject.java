package opusliews.object;

import necesse.engine.localization.message.GameMessage;
import necesse.engine.localization.message.LocalMessage;
import necesse.inventory.recipe.Tech;
import necesse.level.gameObject.container.CraftingStationObject;
import opusliews.earlygame.CrudeWorkbenchFeature;

public class CrudeWorkbenchProxyObject extends CraftingStationObject {
	public CrudeWorkbenchProxyObject() {
		super();
		displayMapTooltip = false;
	}

	@Override
	public void loadTextures() {
		// Virtual station: never placed or drawn in the world.
	}

	@Override
	public Tech[] getCraftingTechs() {
		return new Tech[]{CrudeWorkbenchFeature.tech};
	}

	@Override
	public GameMessage getCraftingHeader() {
		return new LocalMessage("object", "crudeworkbench");
	}

	@Override
	public boolean allowHighlightOption() {
		return false;
	}
}
