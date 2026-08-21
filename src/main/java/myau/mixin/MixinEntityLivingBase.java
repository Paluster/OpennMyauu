package myau.mixin;

import myau.Myau;
import myau.event.EventManager;
import myau.events.StrafeEvent;
import myau.management.RotationState;
import myau.hackerdetector.HackerDetectorEngine;
import net.minecraft.entity.player.EntityPlayer;
import myau.module.modules.Jesus;
import myau.module.modules.MouseDelayFix;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({EntityLivingBase.class})
public abstract class MixinEntityLivingBase extends MixinEntity {

    @Inject(
            method = {"setRotationYawHead"},
            at = {@At("HEAD")}
    )
    private void myauTrackYawHead(float yawHead, CallbackInfo ci) {
        if ((Object) this instanceof EntityPlayer) {
            HackerDetectorEngine.onPlayerYawHead((EntityPlayer) (Object) this, yawHead);
        }
    }

    @Inject(
            method = {"getLook"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void getLook(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof EntityPlayerSP) || Myau.moduleManager == null) {
            return;
        }
        MouseDelayFix module = (MouseDelayFix) Myau.moduleManager.modules.get(MouseDelayFix.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        IAccessorEntity accessor = (IAccessorEntity) this;
        if (partialTicks == 1.0F) {
            cir.setReturnValue(accessor.callGetVectorForRotation(this.rotationPitch, this.rotationYaw));
            return;
        }
        float pitch = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * partialTicks;
        float yaw = this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * partialTicks;
        cir.setReturnValue(accessor.callGetVectorForRotation(pitch, yaw));
    }

    @ModifyVariable(
            method = {"jump"},
            at = @At("STORE"),
            ordinal = 0
    )
    private float jump(float float1) {
        return (Entity) ((Object) this) instanceof EntityPlayerSP && RotationState.isActived()
                ? RotationState.getSmoothedYaw() * (float) (Math.PI / 180.0)
                : float1;
    }

    @Redirect(
            method = {"moveEntityWithHeading"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;moveFlying(FFF)V"
            )
    )
    private void moveEntityWithHeading(EntityLivingBase entityLivingBase, float float2, float float3, float float4) {
        if ((Entity) ((Object) this) instanceof EntityPlayerSP) {
            StrafeEvent event = new StrafeEvent(float2, float3, float4);
            EventManager.call(event);
            float2 = event.getStrafe();
            float3 = event.getForward();
            float4 = event.getFriction();
            boolean actived = RotationState.isActived();
            float yaw = this.rotationYaw;
            if (actived) {
                this.rotationYaw = RotationState.getSmoothedYaw();
            }
            entityLivingBase.moveFlying(float2, float3, float4);
            if (actived) {
                this.rotationYaw = yaw;
            }
        } else {
            entityLivingBase.moveFlying(float2, float3, float4);
        }
    }

    @ModifyVariable(
            method = {"moveEntityWithHeading"},
            name = {"f3"},
            at = @At("STORE")
    )
    private float moveEntityWithHeading(float float1) {
        if ((EntityLivingBase) ((Object) this) instanceof EntityPlayerSP && float1 == (float) EnchantmentHelper.getDepthStriderModifier((EntityLivingBase) ((Object) this))) {
            if (Myau.moduleManager == null) {
                return float1;
            }
            Jesus jesus = (Jesus) Myau.moduleManager.modules.get(Jesus.class);
            if (jesus.isEnabled() && (!jesus.groundOnly.getValue() || this.onGround)) {
                return Math.max(float1, jesus.speed.getValue());
            }
        }
        return float1;
    }
}
