package myau.hackerdetector.checks;

import myau.hackerdetector.data.PlayerDataSamples;
import net.minecraft.entity.player.EntityPlayer;

public interface ICheck {
    String getCheatName();

    String getCheatDescription();

    default String getFlagType() {
        return "";
    }

    boolean isEnabled();

    void performCheck(EntityPlayer player, PlayerDataSamples data);

    boolean check(EntityPlayer player, PlayerDataSamples data);
}
