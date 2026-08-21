package myau.mixin;

import myau.hackerdetector.HackerDetectorEngine;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin({EntityOtherPlayerMP.class})
public abstract class MixinEntityOtherPlayerMP {
    @Inject(
            method = {"setPositionAndRotation2"},
            at = {@At("HEAD")}
    )
    private void myauTrackServerPos(double x, double y, double z, float yaw, float pitch, int increments, boolean teleport, CallbackInfo ci) {
        HackerDetectorEngine.onOtherPlayerPosition((EntityOtherPlayerMP) (Object) this, x, y, z, yaw, pitch);
    }
}
