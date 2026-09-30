package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import net.bytebuddy.asm.Advice;
import opusliews.raids.MultiLevelRaidSystem;

@ModMethodPatch(target = ItemAttackerRaiderMob.class, name = "addSaveData", arguments = {SaveData.class})
public class RaiderSaveMultiLevelPatch {
	@Advice.OnMethodExit
	static void onExit(@Advice.This ItemAttackerRaiderMob raider, @Advice.Argument(0) SaveData save) { MultiLevelRaidSystem.addRaiderSave(raider, save); }
}
