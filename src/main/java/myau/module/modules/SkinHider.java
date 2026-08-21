package myau.module.modules;

import com.mojang.authlib.GameProfile;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class SkinHider extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int CAPE_FRAME_MS = 100;

    private final HiddenTexture skin = new HiddenTexture("skinhider/skin");
    private final HiddenTexture cape = new HiddenTexture("skinhider/cape");
    private boolean slim = false;

    public SkinHider() {
        super("SkinHider");
    }

    public File getFolder() {
        File folder = new File("config/Myau/SkinHider");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getSkinFile() {
        return new File(this.getFolder(), "skin.png");
    }

    public File getCapeFile() {
        return new File(this.getFolder(), "cape.png");
    }

    public boolean openFolder() {
        File folder = this.getFolder();
        this.ensureSkinFile(folder);
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer.exe", folder.getCanonicalPath()});
                return true;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public boolean isLocalPlayer(AbstractClientPlayer player) {
        return mc.thePlayer != null && player == mc.thePlayer;
    }

    public boolean isLocalPlayer(GameProfile profile) {
        return mc.thePlayer != null && profile != null && profile.getId() != null && profile.getId().equals(mc.thePlayer.getUniqueID());
    }

    public boolean shouldReplace(AbstractClientPlayer player) {
        return this.isLocalPlayer(player) && this.getHiddenSkin() != null;
    }

    public boolean shouldReplace(GameProfile profile) {
        return this.isLocalPlayer(profile) && this.getHiddenSkin() != null;
    }

    public boolean shouldReplaceCape(AbstractClientPlayer player) {
        return this.isEnabled() && this.isLocalPlayer(player) && this.getHiddenCape() != null;
    }

    public boolean shouldReplaceCape(GameProfile profile) {
        return this.isEnabled() && this.isLocalPlayer(profile) && this.getHiddenCape() != null;
    }

    public ResourceLocation getHiddenSkin() {
        this.ensureLoaded();
        return this.skin.current();
    }

    public ResourceLocation getHiddenCape() {
        this.ensureLoaded();
        return this.cape.current();
    }

    public String getHiddenSkinType() {
        this.ensureLoaded();
        return this.slim ? "slim" : "default";
    }

    @Override
    public void onEnabled() {
        this.skin.reset();
        this.cape.reset();
        this.ensureSkinFile(this.getFolder());
        this.ensureLoaded();
    }

    @Override
    public void onDisabled() {
        this.skin.unload();
        this.cape.unload();
    }

    private void ensureSkinFile(File folder) {
        File skinFile = new File(folder, "skin.png");
        if (skinFile.isFile()) {
            return;
        }
        try {
            InputStream stream = mc.getResourceManager().getResource(DefaultPlayerSkin.getDefaultSkinLegacy()).getInputStream();
            try {
                Files.copy(stream, skinFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                stream.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureLoaded() {
        this.skin.load(this.getSkinFile(), false);
        BufferedImage skinImage = this.skin.image;
        if (skinImage != null && skinImage.getWidth() >= 64 && skinImage.getHeight() >= 32) {
            int x = 50 * skinImage.getWidth() / 64;
            int y = 16 * Math.max(skinImage.getHeight(), 64) / 64;
            if (y >= skinImage.getHeight()) {
                y = skinImage.getHeight() / 4;
            }
            this.slim = (skinImage.getRGB(Math.min(x, skinImage.getWidth() - 1), Math.min(y, skinImage.getHeight() - 1)) >>> 24) == 0;
        } else {
            this.slim = false;
        }
        this.cape.load(this.getCapeFile(), true);
    }

    private static class HiddenTexture {
        private final String path;
        private ResourceLocation[] frames;
        private File file;
        private long modified = -1L;
        private long lastFrameTime;
        private int lastFrame;
        private boolean loaded;
        private BufferedImage image;

        private HiddenTexture(String path) {
            this.path = path;
        }

        private ResourceLocation current() {
            if (!this.loaded || this.frames == null || this.frames.length == 0) {
                return null;
            }
            if (this.frames.length == 1) {
                return this.frames[0];
            }
            long time = System.currentTimeMillis();
            if (time > this.lastFrameTime + CAPE_FRAME_MS) {
                this.lastFrame = this.lastFrame + 1 >= this.frames.length ? 0 : this.lastFrame + 1;
                this.lastFrameTime = time;
            }
            return this.frames[this.lastFrame];
        }

        private void reset() {
            this.frames = null;
            this.file = null;
            this.modified = -1L;
            this.lastFrameTime = 0L;
            this.lastFrame = 0;
            this.loaded = false;
            this.image = null;
        }

        private void load(File target, boolean capeLayout) {
            if (target == null || !target.isFile()) {
                this.unload();
                return;
            }
            long lastModified = target.lastModified();
            if (this.loaded && this.file != null && target.getAbsolutePath().equals(this.file.getAbsolutePath()) && lastModified == this.modified) {
                return;
            }
            try {
                BufferedImage parsed = ImageIO.read(target);
                if (parsed == null) {
                    this.unload();
                    return;
                }
                List<BufferedImage> images = capeLayout ? applyCape(parsed) : singleton(parsed);
                if (images.isEmpty()) {
                    this.unload();
                    return;
                }
                this.unload();
                this.frames = new ResourceLocation[images.size()];
                for (int i = 0; i < images.size(); i++) {
                    ResourceLocation location = images.size() == 1 ? new ResourceLocation("myau", this.path) : new ResourceLocation("myau", this.path + "/" + i);
                    this.frames[i] = location;
                    mc.getTextureManager().loadTexture(location, new DynamicTexture(images.get(i)));
                }
                this.image = images.get(0);
                this.loaded = true;
                this.file = target;
                this.modified = lastModified;
                this.lastFrame = 0;
                this.lastFrameTime = System.currentTimeMillis();
            } catch (Exception ignored) {
                this.unload();
            }
        }

        private void unload() {
            if (this.frames != null) {
                for (int i = 0; i < this.frames.length; i++) {
                    try {
                        mc.getTextureManager().deleteTexture(this.frames[i]);
                    } catch (Exception ignored) {
                    }
                }
            }
            this.reset();
        }

        private static List<BufferedImage> singleton(BufferedImage image) {
            List<BufferedImage> images = new ArrayList<BufferedImage>();
            images.add(image);
            return images;
        }

        private static List<BufferedImage> applyCape(BufferedImage capeImage) {
            capeImage = convertOptiFineCape(capeImage);
            List<BufferedImage> frames = new ArrayList<BufferedImage>();
            int width = capeImage.getWidth();
            int frameHeight = width / 2;
            if (frameHeight > 0 && capeImage.getHeight() != frameHeight) {
                int totalFrames = capeImage.getHeight() / frameHeight;
                for (int currentFrame = 0; currentFrame < totalFrames; currentFrame++) {
                    frames.add(copyRegion(capeImage, 0, currentFrame * frameHeight, width, frameHeight));
                }
                if (!frames.isEmpty()) {
                    return frames;
                }
            }
            frames.add(padCape(capeImage));
            return frames;
        }

        private static BufferedImage convertOptiFineCape(BufferedImage image) {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || width % 46 != 0) {
                return image;
            }
            int ratio = width / 46;
            if (height != 22 * ratio) {
                return image;
            }
            BufferedImage out = new BufferedImage(64 * ratio, 32 * ratio, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = out.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            return out;
        }

        private static BufferedImage padCape(BufferedImage capeImage) {
            int imageWidth = 64;
            int imageHeight = 32;
            while (imageWidth < capeImage.getWidth() || imageHeight < capeImage.getHeight()) {
                imageWidth *= 2;
                imageHeight *= 2;
            }
            BufferedImage imgNew = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = imgNew.createGraphics();
            graphics.drawImage(capeImage, 0, 0, null);
            graphics.dispose();
            return imgNew;
        }

        private static BufferedImage copyRegion(BufferedImage src, int x, int y, int w, int h) {
            BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = copy.createGraphics();
            graphics.drawImage(src, 0, 0, w, h, x, y, x + w, y + h, null);
            graphics.dispose();
            return copy;
        }
    }
}
