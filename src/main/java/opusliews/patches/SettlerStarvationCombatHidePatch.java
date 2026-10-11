package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import net.bytebuddy.asm.Advice;
import opusliews.hunger.SettlerStarvationSystem;

@ModMethodPatch(target = Mob.class, name = "isServerHit", arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class})
public class SettlerStarvationCombatHidePatch {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This Mob mob) {
        if (mob instanceof HumanMob) SettlerStarvationSystem.onCombatHit((HumanMob) mob);
    }
}
