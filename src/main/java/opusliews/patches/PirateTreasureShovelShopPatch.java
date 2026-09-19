package opusliews.patches;

import necesse.engine.modLoader.annotations.ModConstructorPatch;
import necesse.entity.mobs.friendly.human.humanShop.PirateHumanMob;
import necesse.entity.mobs.friendly.human.humanShop.SellingShopItem;
import net.bytebuddy.asm.Advice;
import opusliews.item.TreasureShovelItem;

@ModConstructorPatch(
		target = PirateHumanMob.class,
		arguments = {}
)
public class PirateTreasureShovelShopPatch {
	@Advice.OnMethodExit
	public static void onExit(@Advice.This PirateHumanMob mob) {
		mob.shop.addSellingItem(TreasureShovelItem.stringID, new SellingShopItem()).setStaticPrice(10000, 10000);
	}
}
