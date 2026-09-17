package opusliews.earlygame;

import necesse.engine.localization.message.LocalMessage;
import necesse.engine.registries.ObjectRegistry;
import necesse.engine.registries.RecipeTechRegistry;
import necesse.inventory.recipe.Tech;
import opusliews.object.CrudeWorkbenchProxyObject;

public final class CrudeWorkbenchFeature {
	public static final String techStringID = "crudeworkbench";
	public static final String proxyObjectStringID = "crudeworkbenchproxy";
	public static Tech tech;
	public static CrudeWorkbenchProxyObject proxyObject;

	private CrudeWorkbenchFeature() {
	}

	public static void register() {
		tech = RecipeTechRegistry.registerTech(
				techStringID,
				"oaktreestump",
				new LocalMessage("object", "crudeworkbench")
		);
		proxyObject = new CrudeWorkbenchProxyObject();
		ObjectRegistry.registerObject(proxyObjectStringID, proxyObject, 0.0F, false);
	}
}
