package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.LoadData;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "applyLoadData", arguments = {LoadData.class})
public class RaiderLoadMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This ItemAttackerRaiderMob raider, @Advice.Argument(0) LoadData save) { MultiLevelRaidSystem.applyRaiderLoad(raider, save); }
}
