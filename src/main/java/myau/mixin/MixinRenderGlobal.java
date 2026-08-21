package myau.mixin;

import myau.hackerdetector.HackerDetectorEngine;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin({RenderGlobal.class})
public abstract class MixinRenderGlobal {
    @Inject(
            method = {"playAuxSFX"},
            at = {@At("HEAD")}
    )
    private void myauListenDestroyedBlocks(EntityPlayer player, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type != 2001) {
            return;
        }
        Block block = Block.getBlockById(data & 4095);
        IBlockState state = block.getStateFromMeta(data >> 12 & 255);
        HackerDetectorEngine.onBlockDestroyed(state, pos);
    }
}
