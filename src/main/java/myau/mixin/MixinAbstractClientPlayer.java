package myau.mixin;

import myau.Myau;
import myau.module.modules.SkinHider;
import myau.module.modules.Sprint;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({AbstractClientPlayer.class})
public abstract class MixinAbstractClientPlayer extends MixinEntityPlayer {
    @Inject(
            method = {"getLocationSkin()Lnet/minecraft/util/ResourceLocation;"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void myau$getLocationSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        if (Myau.moduleManager == null) {
            return;
        }
        SkinHider hider = (SkinHider) Myau.moduleManager.modules.get(SkinHider.class);
        if (hider != null && hider.isEnabled() && hider.shouldReplace((AbstractClientPlayer) (Object) this)) {
            cir.setReturnValue(hider.getHiddenSkin());
        }
    }

    @Inject(
            method = {"getLocationCape"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void myau$getLocationCape(CallbackInfoReturnable<ResourceLocation> cir) {
        if (Myau.moduleManager == null) {
            return;
        }
        SkinHider hider = (SkinHider) Myau.moduleManager.modules.get(SkinHider.class);
        if (hider != null && hider.shouldReplaceCape((AbstractClientPlayer) (Object) this)) {
            cir.setReturnValue(hider.getHiddenCape());
        }
    }

    @Inject(
            method = {"getSkinType"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void myau$getSkinType(CallbackInfoReturnable<String> cir) {
        if (Myau.moduleManager == null) {
            return;
        }
        SkinHider hider = (SkinHider) Myau.moduleManager.modules.get(SkinHider.class);
        if (hider != null && hider.isEnabled() && hider.shouldReplace((AbstractClientPlayer) (Object) this)) {
            cir.setReturnValue(hider.getHiddenSkinType());
        }
    }

    @Redirect(
            method = {"getFovModifier"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/attributes/IAttributeInstance;getAttributeValue()D"
            )
    )
    private double getFovModifier(IAttributeInstance iAttributeInstance) {
        double attributeValue = iAttributeInstance.getAttributeValue();
        if ((((Entity) (Object) this)) instanceof EntityPlayerSP && Myau.moduleManager != null) {
            Sprint sprint = (Sprint) Myau.moduleManager.modules.get(Sprint.class);
            return sprint.isEnabled() && sprint.shouldApplyFovFix(iAttributeInstance) ? attributeValue * 1.300000011920929 : attributeValue;
        } else {
            return attributeValue;
        }
    }
}
