package myau.mixin;

import myau.Myau;
import myau.module.modules.ShowNick;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin({GuiPlayerTabOverlay.class})
public abstract class MixinGuiPlayerTabOverlay {
    @Inject(
            method = {"getPlayerName"},
            at = {@At("RETURN")},
            cancellable = true
    )
    private void getPlayerName(NetworkPlayerInfo info, CallbackInfoReturnable<String> cir) {
        if (Myau.moduleManager == null) {
            return;
        }
        ShowNick showNick = (ShowNick) Myau.moduleManager.modules.get(ShowNick.class);
        if (showNick != null && showNick.isEnabled()) {
            cir.setReturnValue(showNick.formatTabName(info, cir.getReturnValue()));
        }
    }
}
