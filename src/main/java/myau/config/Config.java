package myau.config;

import com.google.gson.*;
import myau.Myau;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.module.modules.BlockESP;
import myau.module.modules.ChestStealer;
import myau.module.modules.ItemESP;
import myau.util.ChatUtil;
import myau.property.Property;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.ArrayList;

public class Config {
    public static Minecraft mc = Minecraft.getMinecraft();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public String name;
    public File file;
    private boolean allowSave;

    public Config(String name, boolean newConfig) {
        this.name = name;
        this.allowSave = true;
        if (name.equals("!") || name.equals("default")) {
            this.name = "default";
        }
        this.file = new File("./config/Myau/", String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                ((IAccessorMinecraft) mc).getLogger().info(String.format("Created: %s", this.file.getName()));
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error(e.getMessage());
        }
    }

    public boolean canSave() {
        return this.allowSave;
    }

    public void load() {
        try {

            if (!file.exists()) {
                ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r). Creating default config...&r", Myau.clientName, file.getName()));
                save();
                return;
            }

            JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(file)));
            if (parsed == null || !parsed.isJsonObject()) {
                ChatUtil.sendFormatted(String.format("%sInvalid config format (&c&o%s&r)&r", Myau.clientName, file.getName()));
                return;
            }

            JsonObject jsonObject = parsed.getAsJsonObject();
            for (Module module : Myau.moduleManager.modules.values()) {
                JsonElement moduleObj = jsonObject.get(module.getName());
                if (moduleObj != null && moduleObj.isJsonObject()) {
                    JsonObject object = moduleObj.getAsJsonObject();

                    if (object.has("toggled")) {
                        JsonElement toggled = object.get("toggled");
                        if (toggled != null && toggled.isJsonPrimitive()) {
                            module.setEnabled(toggled.getAsBoolean());
                        }
                    }

                    if (object.has("key")) {
                        JsonElement key = object.get("key");
                        if (key != null && key.isJsonPrimitive()) {
                            module.setKey(key.getAsInt());
                        }
                    }

                    if (object.has("hidden")) {
                        JsonElement hidden = object.get("hidden");
                        if (hidden != null && hidden.isJsonPrimitive()) {
                            module.setHidden(hidden.getAsBoolean());
                        }
                    }

                    ArrayList<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
                    if (list != null) {
                        for (Property<?> property : list) {
                            if (object.has(property.getName())) {
                                try {
                                    property.read(object);
                                } catch (Exception e) {
                                    ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to load property %s for module %s", property.getName(), module.getName()));
                                }
                            }
                        }
                    }
                    if (module instanceof BlockESP && object.has("blocks") && object.get("blocks").isJsonArray()) {
                        ((BlockESP) module).readBlocks(object.getAsJsonArray("blocks"));
                    }
                    if (module instanceof ItemESP && object.has("items") && object.get("items").isJsonArray()) {
                        ((ItemESP) module).readItems(object.getAsJsonArray("items"));
                    }
                    if (module instanceof ChestStealer && object.has("skip-items") && object.get("skip-items").isJsonArray()) {
                        ((ChestStealer) module).readSkipItems(object.getAsJsonArray("skip-items"));
                    }
                }
            }
            ChatUtil.sendFormatted(String.format("%sConfig has been loaded (&a&o%s&r)&r", Myau.clientName, file.getName()));
            this.allowSave = true;
        } catch (FileNotFoundException e) {
            ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r)&r", Myau.clientName, file.getName()));
        } catch (JsonSyntaxException e) {
            this.allowSave = false;
            ChatUtil.sendFormatted(String.format("%sConfig has invalid JSON syntax (&c&o%s&r)&r", Myau.clientName, file.getName()));
            ((IAccessorMinecraft) mc).getLogger().error("JSON Syntax Error: " + e.getMessage());
        } catch (Exception e) {
            this.allowSave = false;
            ((IAccessorMinecraft) mc).getLogger().error("Error loading config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", Myau.clientName, file.getName()));
        }
    }

    public void save() {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject object = new JsonObject();
            for (Module module : Myau.moduleManager.modules.values()) {
                JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("toggled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());
                moduleObject.addProperty("hidden", module.isHidden());

                ArrayList<Property<?>> list = Myau.propertyManager.properties.get(module.getClass());
                if (list != null) {
                    for (Property<?> property : list) {
                        try {
                            property.write(moduleObject);
                        } catch (Exception e) {
                            ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to save property %s for module %s", property.getName(), module.getName()));
                        }
                    }
                }
                if (module instanceof BlockESP) {
                    moduleObject.add("blocks", ((BlockESP) module).writeBlocks());
                }
                if (module instanceof ItemESP) {
                    moduleObject.add("items", ((ItemESP) module).writeItems());
                }
                if (module instanceof ChestStealer) {
                    moduleObject.add("skip-items", ((ChestStealer) module).writeSkipItems());
                }
                object.add(module.getName(), moduleObject);
            }

            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            PrintWriter printWriter = new PrintWriter(new FileWriter(temp));
            printWriter.println(gson.toJson(object));
            printWriter.close();
            if (file.exists() && !file.delete()) {
                ((IAccessorMinecraft) mc).getLogger().warn("Could not replace config " + file.getName());
            }
            if (!temp.renameTo(file)) {
                PrintWriter fallback = new PrintWriter(new FileWriter(file));
                fallback.println(gson.toJson(object));
                fallback.close();
            }
            this.allowSave = true;
            ChatUtil.sendFormatted(String.format("%sConfig has been saved (&a&o%s&r)&r", Myau.clientName, file.getName()));
        } catch (IOException e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error saving config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", Myau.clientName, file.getName()));
        }
    }
}
