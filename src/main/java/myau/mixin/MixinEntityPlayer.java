package myau.mixin;

import myau.Myau;
import myau.hackerdetector.data.IEntityPlayerSamples;
import myau.hackerdetector.data.PlayerDataSamples;
import myau.module.modules.KeepSprint;
import myau.module.modules.SkinHider;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({EntityPlayer.class})
public abstract class MixinEntityPlayer extends MixinEntityLivingBase implements IEntityPlayerSamples {
    @Unique
    private PlayerDataSamples myau$playerDataSamples = new PlayerDataSamples();

    @Override
    public PlayerDataSamples getPlayerDataSamples() {
        return this.myau$playerDataSamples;
    }

    @Inject(
            method = {"isWearing"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void myau$showSkinHiderCape(EnumPlayerModelParts part, CallbackInfoReturnable<Boolean> cir) {
        if (part != EnumPlayerModelParts.CAPE || Myau.moduleManager == null) {
            return;
        }
        Object self = this;
        if (!(self instanceof AbstractClientPlayer)) {
            return;
        }
        SkinHider hider = (SkinHider) Myau.moduleManager.modules.get(SkinHider.class);
        if (hider != null && hider.shouldReplaceCape((AbstractClientPlayer) self)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyConstant(
            method = {"attackTargetEntityWithCurrentItem"},
            constant = {@Constant(
                    doubleValue = 0.6
            )}
    )
    private double attackTargetEntityWithCurrentItem(double speed) {
        if (Myau.moduleManager == null) {
            return speed;
        } else {
            KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
            return keepSprint.isEnabled() && keepSprint.shouldKeepSprint()
                    ? speed + (1.0 - speed) * (1.0 - keepSprint.slowdown.getValue().doubleValue() / 100.0)
                    : speed;
        }
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
        if (Myau.moduleManager != null) {
            KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
            if (!keepSprint.isEnabled() || !keepSprint.shouldKeepSprint()) {
                entityPlayer.setSprinting(boolean2);
            }
        }
    }
}
