package myau.mixin;

import com.mojang.authlib.GameProfile;
import myau.Myau;
import myau.module.modules.ShowNick;
import myau.module.modules.SkinHider;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({NetworkPlayerInfo.class})
public abstract class MixinNetworkPlayerInfo {
    @Shadow
    @Final
    private GameProfile gameProfile;

    @Inject(
            method = {"getLocationSkin"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void myau$getLocationSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        if (Myau.moduleManager == null) {
            return;
        }
        SkinHider hider = (SkinHider) Myau.moduleManager.modules.get(SkinHider.class);
        if (hider != null && hider.isEnabled() && hider.shouldReplace(this.gameProfile)) {
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
        if (hider != null && hider.shouldReplaceCape(this.gameProfile)) {
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
        if (hider != null && hider.isEnabled() && hider.shouldReplace(this.gameProfile)) {
            cir.setReturnValue(hider.getHiddenSkinType());
        }
    }

    @Inject(
            method = {"getDisplayName"},
            at = {@At("RETURN")},
            cancellable = true
    )
    private void myau$showNickDisplayName(CallbackInfoReturnable<IChatComponent> cir) {
        if (Myau.moduleManager == null) {
            return;
        }
        ShowNick showNick = (ShowNick) Myau.moduleManager.modules.get(ShowNick.class);
        if (showNick == null || !showNick.isEnabled()) {
            return;
        }
        NetworkPlayerInfo info = (NetworkPlayerInfo) (Object) this;
        IChatComponent marked = showNick.formatTabDisplayName(info, cir.getReturnValue());
        if (marked != cir.getReturnValue()) {
            cir.setReturnValue(marked);
        }
    }
}
