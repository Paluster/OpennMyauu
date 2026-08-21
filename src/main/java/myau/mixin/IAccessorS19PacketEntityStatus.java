package myau.mixin;

import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({S19PacketEntityStatus.class})
public interface IAccessorS19PacketEntityStatus {
    @Accessor
    int getEntityId();
}