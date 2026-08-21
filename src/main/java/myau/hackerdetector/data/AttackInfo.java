package myau.hackerdetector.data;

import myau.hackerdetector.AttackDetector;
import net.minecraft.entity.player.EntityPlayer;

public class AttackInfo {
    public EntityPlayer target;
    public String targetName;
    public final AttackDetector.AttackType attackType;
    public boolean multiTarget = false;

    public AttackInfo(EntityPlayer target, AttackDetector.AttackType attackType) {
        this.setTarget(target);
        this.attackType = attackType;
    }

    public void setTarget(EntityPlayer target) {
        this.target = target;
        this.targetName = target == null ? null : target.getName();
    }
}
