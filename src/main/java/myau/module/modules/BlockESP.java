package myau.module.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import myau.Myau;
import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.event.types.EventType;
import myau.mixin.IAccessorMinecraft;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.util.RegistryParser;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S22PacketMultiBlockChange.BlockUpdateData;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BlockESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long SCAN_BUDGET_NS = 2000000L;
    private static final double TP_RESCAN_DIST_SQ = 32.0 * 32.0;
    private final List<SearchTarget> targets = new ArrayList<>();
    private final List<JsonObject> unresolvedTargets = new ArrayList<>();
    private final ConcurrentHashMap<BlockPos, Integer> hits = new ConcurrentHashMap<>();
    private final HashSet<Long> scannedChunks = new HashSet<>();
    private final ConcurrentLinkedQueue<Long> pendingChunkInvalidations = new ConcurrentLinkedQueue<>();
    private final boolean[] wantedIds = new boolean[4096];
    private int scanMinCx;
    private int scanMaxCx;
    private int scanMinCz;
    private int scanMaxCz;
    private double lastPlayerX = Double.NaN;
    private double lastPlayerZ = Double.NaN;
    public final PercentProperty opacity = new PercentProperty("opacity", 100);
    public final BooleanProperty tracers = new BooleanProperty("tracers", false);

    public BlockESP() {
        super("BlockESP");
    }

    public List<SearchTarget> getTargets() {
        return this.targets;
    }

    public SearchTarget findTarget(String name) {
        for (SearchTarget target : this.targets) {
            if (target.name.equalsIgnoreCase(name)) {
                return target;
            }
        }
        return null;
    }

    public SearchTarget findTarget(int blockId, int metadata) {
        for (SearchTarget target : this.targets) {
            if (target.matches(blockId, metadata)) {
                return target;
            }
        }
        return null;
    }

    public boolean addTarget(SearchTarget target) {
        if (this.findTarget(target.name) != null) {
            return false;
        }
        for (SearchTarget existing : this.targets) {
            if (existing.blockId == target.blockId && existing.metadata == target.metadata) {
                return false;
            }
        }
        this.targets.add(target);
        this.rebuildLookup();
        this.startScan();
        return true;
    }

    public boolean removeTarget(String name) {
        SearchTarget target = this.findTarget(name);
        if (target == null) {
            SearchTarget parsed = parseTarget(name, 0);
            if (parsed != null) {
                for (SearchTarget existing : this.targets) {
                    if (existing.blockId == parsed.blockId && existing.metadata == parsed.metadata) {
                        target = existing;
                        break;
                    }
                }
            }
        }
        if (target == null) {
            return false;
        }
        this.targets.remove(target);
        this.rebuildLookup();
        this.hits.clear();
        this.startScan();
        return true;
    }

    public void clearTargets() {
        this.targets.clear();
        this.unresolvedTargets.clear();
        this.hits.clear();
        this.rebuildLookup();
        this.resetScanProgress();
    }

    public static SearchTarget fromBlock(Block block, int metadata, int color) {
        return fromParsed(RegistryParser.fromBlock(block, metadata), color);
    }

    public static SearchTarget parseTarget(String input, int color) {
        return fromParsed(RegistryParser.parseBlock(input), color);
    }

    public static RegistryParser.Parsed parseDetailed(String input) {
        return RegistryParser.parseBlock(input);
    }

    private static SearchTarget fromParsed(RegistryParser.Parsed parsed, int color) {
        if (parsed == null || !parsed.ok()) {
            return null;
        }
        if (color < 0) {
            color = randomColor();
        }
        return new SearchTarget(parsed.canonical, parsed.id, parsed.metadata, color & 0xFFFFFF);
    }

    public static Integer parseHexColor(String input) {
        if (input == null) {
            return null;
        }
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (!hex.matches("(?i)[0-9a-f]{6}")) {
            return null;
        }
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }

    public static boolean looksLikeColor(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (input.startsWith("#")) {
            return true;
        }
        return input.length() == 6;
    }

    public static int randomColor() {
        float hue = (float) Math.random();
        float saturation = 0.65F + (float) Math.random() * 0.35F;
        float brightness = 0.75F + (float) Math.random() * 0.25F;
        return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    public void refresh() {
        this.startScan();
    }

    public JsonArray writeBlocks() {
        JsonArray array = new JsonArray();
        for (SearchTarget target : this.targets) {
            array.add(this.writeTarget(target));
        }
        for (JsonObject leftover : this.unresolvedTargets) {
            array.add(leftover);
        }
        return array;
    }

    private JsonObject writeTarget(SearchTarget target) {
        JsonObject object = new JsonObject();
        object.addProperty("block", target.name);
        object.addProperty("id", target.blockId);
        object.addProperty("meta", target.metadata);
        object.addProperty("color", String.format("%06X", target.color));
        return object;
    }

    public void readBlocks(JsonArray array) {
        this.targets.clear();
        this.hits.clear();
        this.unresolvedTargets.clear();
        for (JsonElement element : array) {
            try {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                SearchTarget target = this.readTarget(object);
                if (target != null) {
                    this.targets.add(target);
                } else {
                    this.unresolvedTargets.add(object);
                }
            } catch (Exception ignored) {
                if (element != null && element.isJsonObject()) {
                    this.unresolvedTargets.add(element.getAsJsonObject());
                }
            }
        }
        this.rebuildLookup();
        this.startScan();
    }

    private SearchTarget readTarget(JsonObject object) {
        int color = -1;
        if (object.has("color") && object.get("color").isJsonPrimitive()) {
            Integer parsed = parseHexColor(object.get("color").getAsString());
            if (parsed != null) {
                color = parsed;
            }
        }
        Integer meta = this.readInt(object, "meta");
        if (object.has("block") && object.get("block").isJsonPrimitive()) {
            SearchTarget byName = fromParsed(RegistryParser.parseBlockForConfig(object.get("block").getAsString(), meta), color);
            if (byName != null) {
                return byName;
            }
        }
        Integer id = this.readInt(object, "id");
        if (id != null) {
            Block block = Block.getBlockById(id);
            return fromBlock(block, meta == null ? 0 : meta, color);
        }
        return null;
    }

    private Integer readInt(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void rebuildLookup() {
        for (int i = 0; i < this.wantedIds.length; i++) {
            this.wantedIds[i] = false;
        }
        for (SearchTarget target : this.targets) {
            if (target.blockId >= 0 && target.blockId < this.wantedIds.length) {
                this.wantedIds[target.blockId] = true;
            }
        }
    }

    private SearchTarget matchState(IBlockState state) {
        if (state == null) {
            return null;
        }
        Block block = state.getBlock();
        int id = Block.getIdFromBlock(block);
        if (id < 0 || id >= this.wantedIds.length || !this.wantedIds[id]) {
            return null;
        }
        return this.findVariant(id, block.getMetaFromState(state));
    }

    private SearchTarget findVariant(int id, int packedMeta) {
        SearchTarget exact = this.findTarget(id, packedMeta);
        if (exact != null) {
            return exact;
        }
        int variant = RegistryParser.variantMeta(Block.getBlockById(id), packedMeta);
        if (variant == packedMeta) {
            return null;
        }
        return this.findTarget(id, variant);
    }

    private boolean inScanBounds(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return cx >= this.scanMinCx && cx <= this.scanMaxCx && cz >= this.scanMinCz && cz <= this.scanMaxCz;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | ((long) z & 0xFFFFFFFFL);
    }

    private void resetScanProgress() {
        this.pendingChunkInvalidations.clear();
        this.scannedChunks.clear();
        this.lastPlayerX = Double.NaN;
        this.lastPlayerZ = Double.NaN;
    }

    private void invalidateChunk(int cx, int cz) {
        this.pendingChunkInvalidations.add(chunkKey(cx, cz));
    }

    private void drainChunkInvalidations() {
        Long key;
        while ((key = this.pendingChunkInvalidations.poll()) != null) {
            this.scannedChunks.remove(key);
        }
    }

    private void startScan() {
        this.resetScanProgress();
        this.updateScanBounds();
    }

    private void updateScanBounds() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        int chunkRange = Math.max(mc.gameSettings.renderDistanceChunks, 2);
        int playerChunkX = (int) Math.floor(mc.thePlayer.posX) >> 4;
        int playerChunkZ = (int) Math.floor(mc.thePlayer.posZ) >> 4;
        this.scanMinCx = playerChunkX - chunkRange;
        this.scanMaxCx = playerChunkX + chunkRange;
        this.scanMinCz = playerChunkZ - chunkRange;
        this.scanMaxCz = playerChunkZ + chunkRange;
        this.pruneScannedChunks();
    }

    private void pruneScannedChunks() {
        Iterator<Long> iterator = this.scannedChunks.iterator();
        while (iterator.hasNext()) {
            long key = iterator.next();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (cx < this.scanMinCx || cx > this.scanMaxCx || cz < this.scanMinCz || cz > this.scanMaxCz) {
                iterator.remove();
            }
        }
    }

    private boolean isChunkPopulated(int cx, int cz) {
        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
        if (chunk == null || chunk.isEmpty()) {
            return false;
        }
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        if (storages == null) {
            return false;
        }
        for (int i = 0; i < storages.length; i++) {
            ExtendedBlockStorage storage = storages[i];
            if (storage != null && !storage.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int[] findNearestUnscannedChunk() {
        int bestCx = 0;
        int bestCz = 0;
        int bestDist = Integer.MAX_VALUE;
        boolean found = false;
        int playerChunkX = (int) Math.floor(mc.thePlayer.posX) >> 4;
        int playerChunkZ = (int) Math.floor(mc.thePlayer.posZ) >> 4;
        for (int cz = this.scanMinCz; cz <= this.scanMaxCz; cz++) {
            for (int cx = this.scanMinCx; cx <= this.scanMaxCx; cx++) {
                if (this.scannedChunks.contains(chunkKey(cx, cz))) {
                    continue;
                }
                if (!this.isChunkPopulated(cx, cz)) {
                    continue;
                }
                int dx = cx - playerChunkX;
                int dz = cz - playerChunkZ;
                int dist = dx * dx + dz * dz;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestCx = cx;
                    bestCz = cz;
                    found = true;
                    if (dist == 0) {
                        return new int[]{cx, cz};
                    }
                }
            }
        }
        return found ? new int[]{bestCx, bestCz} : null;
    }

    private void scanLoadedChunks() {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null || this.targets.isEmpty()) {
            return;
        }
        this.drainChunkInvalidations();
        long deadline = System.nanoTime() + SCAN_BUDGET_NS;
        boolean scannedOne = false;
        while (true) {
            int[] next = this.findNearestUnscannedChunk();
            if (next == null) {
                return;
            }
            if (scannedOne && System.nanoTime() >= deadline) {
                return;
            }
            if (!this.scanChunk(next[0], next[1])) {
                return;
            }
            this.scannedChunks.add(chunkKey(next[0], next[1]));
            scannedOne = true;
        }
    }

    private boolean scanChunk(int cx, int cz) {
        if (!this.isChunkPopulated(cx, cz)) {
            return false;
        }
        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        if (storages == null) {
            return false;
        }
        for (int i = 0; i < storages.length; i++) {
            ExtendedBlockStorage storage = storages[i];
            if (storage != null && !storage.isEmpty()) {
                this.scanSectionData(chunk, storage);
            }
        }
        return true;
    }

    private void scanSectionData(Chunk chunk, ExtendedBlockStorage storage) {
        char[] data = storage.getData();
        if (data == null) {
            return;
        }
        int yBase = storage.getYLocation();
        int cx = chunk.xPosition;
        int cz = chunk.zPosition;
        for (int i = 0; i < data.length; i++) {
            int packed = data[i];
            int id = packed >> 4;
            if (id == 0 || id >= this.wantedIds.length || !this.wantedIds[id]) {
                continue;
            }
            SearchTarget target = this.findVariant(id, packed & 15);
            if (target == null) {
                continue;
            }
            this.hits.put(new BlockPos((cx << 4) + (i & 15), yBase + (i >> 8), (cz << 4) + (i >> 4 & 15)), target.color);
        }
    }

    private void collectTileEntities() {
        if (mc.theWorld.loadedTileEntityList == null) {
            return;
        }
        List<TileEntity> tiles = new ArrayList<TileEntity>(mc.theWorld.loadedTileEntityList);
        for (int i = 0; i < tiles.size(); i++) {
            TileEntity tile = tiles.get(i);
            if (tile == null) {
                continue;
            }
            BlockPos pos = tile.getPos();
            if (pos == null || !this.inScanBounds(pos) || !mc.theWorld.isBlockLoaded(pos, false)) {
                continue;
            }
            SearchTarget target = this.matchState(mc.theWorld.getBlockState(pos));
            if (target != null) {
                this.hits.put(new BlockPos(pos), target.color);
            }
        }
    }

    private boolean playerTeleported() {
        double x = mc.thePlayer.posX;
        double z = mc.thePlayer.posZ;
        if (Double.isNaN(this.lastPlayerX) || Double.isNaN(this.lastPlayerZ)) {
            this.lastPlayerX = x;
            this.lastPlayerZ = z;
            return false;
        }
        double dx = x - this.lastPlayerX;
        double dz = z - this.lastPlayerZ;
        this.lastPlayerX = x;
        this.lastPlayerZ = z;
        return dx * dx + dz * dz > TP_RESCAN_DIST_SQ;
    }

    private void offerBlock(BlockPos blockPos, IBlockState state) {
        if (!this.isEnabled() || state == null || this.targets.isEmpty()) {
            return;
        }
        SearchTarget target = this.matchState(state);
        if (target == null || !this.inScanBounds(blockPos)) {
            this.hits.remove(blockPos);
            return;
        }
        this.hits.put(new BlockPos(blockPos), target.color);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (this.targets.isEmpty()) {
            if (!this.hits.isEmpty()) {
                this.hits.clear();
            }
            return;
        }
        if (this.playerTeleported()) {
            this.hits.clear();
            this.scannedChunks.clear();
            this.pendingChunkInvalidations.clear();
        }
        this.updateScanBounds();
        this.scanLoadedChunks();
        this.collectTileEntities();
        if (this.hits.isEmpty()) {
            return;
        }
        double renderX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        int alpha = (int) ((float) this.opacity.getValue() / 100.0F * 255.0F);
        Vec3 tracerStart = null;
        float tracerOpacity = 1.0F;
        if (this.tracers.getValue()) {
            tracerStart = this.getTracerVector();
            tracerStart = new Vec3(tracerStart.xCoord, tracerStart.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), tracerStart.zCoord);
            tracerOpacity = (float) ((Tracers) Myau.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
        }
        RenderUtil.enableRenderState();
        Iterator<Map.Entry<BlockPos, Integer>> iterator = this.hits.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (!this.inScanBounds(pos) || !mc.theWorld.isBlockLoaded(pos, false)) {
                iterator.remove();
                continue;
            }
            IBlockState state = mc.theWorld.getBlockState(pos);
            SearchTarget target = this.matchState(state);
            if (target == null) {
                iterator.remove();
                continue;
            }
            AxisAlignedBB aabb = state.getBlock().getSelectedBoundingBox(mc.theWorld, pos);
            if (aabb == null) {
                aabb = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), (double) pos.getX() + 1.0, (double) pos.getY() + 1.0, (double) pos.getZ() + 1.0);
            }
            aabb = aabb.offset(-renderX, -renderY, -renderZ);
            Color color = new Color(target.color);
            RenderUtil.drawBoundingBox(aabb, color.getRed(), color.getGreen(), color.getBlue(), alpha, 1.5F);
            if (tracerStart != null) {
                RenderUtil.drawLine3D(
                        tracerStart,
                        (double) pos.getX() + 0.5,
                        (double) pos.getY() + 0.5,
                        (double) pos.getZ() + 0.5,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        tracerOpacity,
                        1.5F
                );
            }
        }
        RenderUtil.disableRenderState();
    }

    private Vec3 getTracerVector() {
        if (mc.gameSettings.thirdPersonView == 0) {
            return new Vec3(0.0, 0.0, 1.0)
                    .rotatePitch(
                            (float) (
                                    -Math.toRadians(
                                            RenderUtil.lerpFloat(
                                                    mc.getRenderViewEntity().rotationPitch,
                                                    mc.getRenderViewEntity().prevRotationPitch,
                                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                            )
                                    )
                            )
                    )
                    .rotateYaw(
                            (float) (
                                    -Math.toRadians(
                                            RenderUtil.lerpFloat(
                                                    mc.getRenderViewEntity().rotationYaw,
                                                    mc.getRenderViewEntity().prevRotationYaw,
                                                    ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                            )
                                    )
                            )
                    );
        }
        return new Vec3(0.0, 0.0, 0.0)
                .rotatePitch(
                        (float) (
                                -Math.toRadians(
                                        RenderUtil.lerpFloat(
                                                mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks
                                        )
                                )
                        )
                )
                .rotateYaw(
                        (float) (
                                -Math.toRadians(
                                        RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks)
                                )
                        )
                );
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || this.targets.isEmpty()) {
            return;
        }
        if (event.getPacket() instanceof S22PacketMultiBlockChange) {
            for (BlockUpdateData data : ((S22PacketMultiBlockChange) event.getPacket()).getChangedBlocks()) {
                this.offerBlock(data.getPos(), data.getBlockState());
            }
        } else if (event.getPacket() instanceof S23PacketBlockChange) {
            S23PacketBlockChange packet = (S23PacketBlockChange) event.getPacket();
            this.offerBlock(packet.getBlockPosition(), packet.getBlockState());
        } else if (event.getPacket() instanceof S21PacketChunkData) {
            S21PacketChunkData packet = (S21PacketChunkData) event.getPacket();
            this.invalidateChunk(packet.getChunkX(), packet.getChunkZ());
        } else if (event.getPacket() instanceof S26PacketMapChunkBulk) {
            S26PacketMapChunkBulk packet = (S26PacketMapChunkBulk) event.getPacket();
            for (int i = 0; i < packet.getChunkCount(); i++) {
                this.invalidateChunk(packet.getChunkX(i), packet.getChunkZ(i));
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.hits.clear();
        this.resetScanProgress();
    }

    @Override
    public void onEnabled() {
        this.rebuildLookup();
        this.hits.clear();
        this.startScan();
    }

    @Override
    public void onDisabled() {
        this.hits.clear();
        this.resetScanProgress();
    }

    public static class SearchTarget {
        public final String name;
        public final int blockId;
        public final int metadata;
        public int color;

        public SearchTarget(String name, int blockId, int metadata, int color) {
            this.name = name;
            this.blockId = blockId;
            this.metadata = metadata;
            this.color = color;
        }

        public boolean matches(int id, int meta) {
            return this.blockId == id && this.metadata == meta;
        }
    }
}
