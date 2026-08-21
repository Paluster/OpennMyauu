package myau.hackerdetector;

import myau.hackerdetector.checks.AutoblockCheck;
import myau.hackerdetector.checks.FastbreakCheck;
import myau.hackerdetector.checks.GhosthandCheck;
import myau.hackerdetector.checks.ICheck;
import myau.hackerdetector.checks.KeepSprintACheck;
import myau.hackerdetector.checks.KeepSprintBCheck;
import myau.hackerdetector.checks.KillAuraACheck;
import myau.hackerdetector.checks.KillAuraBCheck;
import myau.hackerdetector.checks.NoSlowdownCheck;
import myau.hackerdetector.checks.ScaffoldCheck;
import myau.hackerdetector.data.BrokenBlock;
import myau.hackerdetector.data.IEntityPlayerSamples;
import myau.hackerdetector.data.PlayerDataSamples;
import myau.hackerdetector.data.TickingBlockMap;
import myau.module.modules.HackerDetector;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class HackerDetectorEngine {
    private final AttackDetector attackDetector;
    private final List<ICheck> checkList = new ArrayList<ICheck>();
    private final List<BrokenBlock> brokenBlocksList = new ArrayList<BrokenBlock>();
    private final TickingBlockMap recentPlacedBlocks = new TickingBlockMap(20);
    private final Queue<Runnable> scheduledTasks = new ArrayDeque<Runnable>();
    private final FastbreakCheck fastbreakCheck;

    public HackerDetectorEngine() {
        this.attackDetector = new AttackDetector(this);
        this.checkList.add(new AutoblockCheck());
        this.checkList.add(this.fastbreakCheck = new FastbreakCheck(this.brokenBlocksList));
        this.checkList.add(new GhosthandCheck());
        this.checkList.add(new KeepSprintACheck());
        this.checkList.add(new KeepSprintBCheck());
        this.checkList.add(new KillAuraACheck(this.recentPlacedBlocks));
        this.checkList.add(new KillAuraBCheck());
        this.checkList.add(new NoSlowdownCheck());
        this.checkList.add(new ScaffoldCheck());
    }

    public void onTickStart() {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!HackerDetector.isActive() || mc.theWorld == null || mc.thePlayer == null || !mc.theWorld.isRemote) {
            synchronized (this.scheduledTasks) {
                this.scheduledTasks.clear();
            }
            return;
        }
        final List<EntityPlayer> playerList = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities.size());
        for (final EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.ticksExisted >= 20 && !player.isDead && isValidPlayer(player.getUniqueID())) {
                playerList.add(player);
                ((IEntityPlayerSamples) player).getPlayerDataSamples().onTickStart();
            }
        }
        synchronized (this.scheduledTasks) {
            while (!this.scheduledTasks.isEmpty()) {
                this.scheduledTasks.poll().run();
            }
        }
        for (final EntityPlayer player : playerList) {
            this.performChecksOnPlayer(player);
        }
    }

    public void onTickEnd() {
        if (!HackerDetector.isActive()) {
            return;
        }
        this.fastbreakCheck.onTickEnd();
        this.brokenBlocksList.clear();
        this.recentPlacedBlocks.onTick();
    }

    public static boolean isValidPlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        final int version = uuid.version();
        return version == 1 || version == 4;
    }

    private void performChecksOnPlayer(EntityPlayer player) {
        if (player == Minecraft.getMinecraft().thePlayer) {
            this.fastbreakCheck.checkPlayerSP(player);
            return;
        }
        final PlayerDataSamples data = ((IEntityPlayerSamples) player).getPlayerDataSamples();
        if (data.checkedThisTick) {
            return;
        }
        data.onTick(player);
        for (final ICheck check : this.checkList) {
            if (check.isEnabled()) {
                check.performCheck(player, data);
            }
        }
        data.onPostChecks();
    }

    public void addScheduledTask(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        synchronized (this.scheduledTasks) {
            this.scheduledTasks.add(runnable);
        }
    }

    public void addBrokenBlock(Block block, BlockPos blockPos, String tool) {
        this.brokenBlocksList.add(new BrokenBlock(block, blockPos, tool));
    }

    public void addPlacedBlock(BlockPos pos, IBlockState state) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        final double xDiff = Math.abs(mc.thePlayer.posX - pos.getX());
        final double zDiff = Math.abs(mc.thePlayer.posZ - pos.getZ());
        if (xDiff > 70D || zDiff > 70D) {
            return;
        }
        if (!state.getBlock().isFullBlock() || !state.getBlock().canCollideCheck(state, false)) {
            return;
        }
        if (mc.theWorld.getBlockState(pos).getBlock().getMaterial() == Material.air) {
            this.recentPlacedBlocks.add(pos);
        }
    }

    public void onPlayerBlockPacket(BlockPos pos, int placedBlockDirectionIn, Block block) {
        if (block == null || !block.isFullBlock() || !block.canCollideCheck(block.getDefaultState(), false)) {
            return;
        }
        final EnumFacing facing = EnumFacing.getFront(placedBlockDirectionIn);
        if (facing == null) {
            return;
        }
        this.recentPlacedBlocks.add(pos.add(facing.getDirectionVec()));
    }

    public void lookForAttacks(Packet<?> packet) {
        this.attackDetector.lookForAttacks(packet);
    }

    public static void onOtherPlayerPosition(final net.minecraft.client.entity.EntityOtherPlayerMP player, final double x, final double y, final double z, final float yaw, final float pitch) {
        if (!HackerDetector.isActive()) {
            return;
        }
        HackerDetector.get().getEngine().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ((IEntityPlayerSamples) player).getPlayerDataSamples().setPositionAndRotation(x, y, z, yaw, pitch);
            }
        });
    }

    public static void onPlayerYawHead(final net.minecraft.entity.player.EntityPlayer player, final float yawHead) {
        if (!HackerDetector.isActive()) {
            return;
        }
        HackerDetector.get().getEngine().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ((IEntityPlayerSamples) player).getPlayerDataSamples().setRotationYawHead(yawHead);
            }
        });
    }

    public static void onBlockDestroyed(net.minecraft.block.state.IBlockState state, net.minecraft.util.BlockPos pos) {
        if (!HackerDetector.isActive()) {
            return;
        }
        String tool = state.getBlock().getHarvestTool(state);
        if ("pickaxe".equals(tool) || "axe".equals(tool) || tool == null) {
            HackerDetector.get().getEngine().addBrokenBlock(state.getBlock(), pos, tool);
        }
    }

}
