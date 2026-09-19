package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.hostile.pirates.PirateCaptainMob;
import necesse.inventory.lootTable.LootItemInterface;
import necesse.inventory.lootTable.LootTable;
import necesse.inventory.lootTable.lootItem.LootItem;
import net.bytebuddy.asm.Advice;
import opusliews.item.TreasureShovelItem;

@ModMethodPatch(
		target = PirateCaptainMob.class,
		name = "getLootTable",
		arguments = {}
)
public class PirateCaptainTreasureShovelPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This PirateCaptainMob mob,
			@Advice.Return(readOnly = false) LootTable result
	) {
		if (mob.dropLadder) {
			result = new LootTable(new LootItemInterface[]{PirateCaptainMob.lootTable, new LootItem(TreasureShovelItem.stringID)});
		}
	}
}
