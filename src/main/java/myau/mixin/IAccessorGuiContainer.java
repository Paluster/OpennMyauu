package myau.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@SideOnly(Side.CLIENT)
@Mixin({GuiContainer.class})
public interface IAccessorGuiContainer {
    @Invoker("getSlotAtPosition")
    Slot invokeGetSlotAtPosition(int x, int y);
}
