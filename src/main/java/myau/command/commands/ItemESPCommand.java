package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.enums.ChatColors;
import myau.module.modules.ItemESP;
import myau.module.modules.ItemESP.ItemTarget;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.util.ChatUtil;
import myau.util.RegistryParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ItemESPCommand extends Command {
    public ItemESPCommand() {
        super(new ArrayList<>(Arrays.asList("itemesp")));
    }

    private ItemESP getModule() {
        return (ItemESP) Myau.moduleManager.modules.get(ItemESP.class);
    }

    private boolean applyProperty(ItemESP module, String name, String value) {
        Property<?> property = Myau.propertyManager.getProperty(module, name);
        if (property == null) {
            return false;
        }
        if (value == null && !(property instanceof BooleanProperty)) {
            ChatUtil.sendFormatted(
                    String.format(
                            "%s%s: &o%s&r is set to %s&r (%s)&r",
                            Myau.clientName,
                            module.getName(),
                            property.getName(),
                            property.formatValue(),
                            property.getValuePrompt()
                    )
            );
            return true;
        }
        try {
            if (property.parseString(value)) {
                ChatUtil.sendFormatted(
                        String.format("%s%s: &o%s&r has been set to %s&r", Myau.clientName, module.getName(), property.getName(), property.formatValue())
                );
                return true;
            }
        } catch (Exception ignored) {
        }
        ChatUtil.sendFormatted(
                String.format("%sInvalid value for property &o%s&r (%s)&r", Myau.clientName, property.getName(), property.getValuePrompt())
        );
        return true;
    }

    private String joinArgs(ArrayList<String> args, int start, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args.get(i));
        }
        return builder.toString();
    }

    private ItemColor takeItemAndColor(ArrayList<String> args, int start) {
        if (args.size() <= start) {
            return null;
        }
        int nameEnd = args.size();
        Integer color = null;
        if (args.size() > start + 1) {
            String last = args.get(args.size() - 1);
            Integer parsed = ItemESP.parseHexColor(last);
            if (parsed != null) {
                color = parsed;
                nameEnd = args.size() - 1;
            } else if (ItemESP.looksLikeColor(last)) {
                return ItemColor.invalidColor(last);
            }
        }
        String name = this.joinArgs(args, start, nameEnd);
        if (name.isEmpty()) {
            return null;
        }
        if (name.indexOf(' ') >= 0) {
            return ItemColor.invalidName(name);
        }
        return ItemColor.ok(name, color);
    }

    private ItemTarget findListed(ItemESP module, ItemTarget parsed) {
        for (ItemTarget existing : module.getTargets()) {
            if (existing.itemId == parsed.itemId && existing.metadata == parsed.metadata) {
                return existing;
            }
            if (existing.name.equalsIgnoreCase(parsed.name)) {
                return existing;
            }
        }
        return null;
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        ItemESP module = this.getModule();
        String command = args.get(0).toLowerCase(Locale.ROOT);
        if (args.size() >= 2) {
            String subCommand = args.get(1).toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "add": {
                    ItemColor itemColor = this.takeItemAndColor(args, 2);
                    if (itemColor == null) {
                        ChatUtil.sendFormatted(String.format("%sUsage: .%s add <&oregistry[:data]&r> [color]&r", Myau.clientName, command));
                        return;
                    }
                    if (itemColor.invalidColor) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(itemColor.token)));
                        return;
                    }
                    if (itemColor.invalidName) {
                        ChatUtil.sendFormatted(
                                String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(itemColor.token))
                        );
                        return;
                    }
                    RegistryParser.Parsed detailed = ItemESP.parseDetailed(itemColor.name);
                    if (!detailed.ok()) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(itemColor.name)));
                        return;
                    }
                    int color = itemColor.color == null ? -1 : itemColor.color;
                    ItemTarget parsed = ItemESP.parseTarget(itemColor.name, color);
                    if (parsed == null) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(itemColor.name)));
                        return;
                    }
                    ItemTarget existing = this.findListed(module, parsed);
                    if (existing != null) {
                        existing.color = parsed.color;
                        ChatUtil.sendFormatted(
                                String.format("%s%s color set to %s&r", Myau.clientName, existing.name, String.format("%06X", existing.color))
                        );
                        return;
                    }
                    module.addTarget(parsed);
                    ChatUtil.sendFormatted(
                            String.format("%sAdded &o%s&r to ItemESP (%s)&r", Myau.clientName, parsed.name, String.format("%06X", parsed.color))
                    );
                    return;
                }
                case "remove": {
                    if (args.size() < 3) {
                        ChatUtil.sendFormatted(String.format("%sUsage: .%s remove <&oregistry[:data]&r>&r", Myau.clientName, command));
                        return;
                    }
                    String name = this.joinArgs(args, 2, args.size());
                    RegistryParser.Parsed detailed = ItemESP.parseDetailed(name);
                    if (!detailed.ok()) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(name)));
                        return;
                    }
                    if (!module.removeTarget(name)) {
                        ChatUtil.sendFormatted(String.format("%s&o%s&r is not in ItemESP&r", Myau.clientName, detailed.canonical));
                        return;
                    }
                    ChatUtil.sendFormatted(String.format("%sRemoved &o%s&r from ItemESP&r", Myau.clientName, detailed.canonical));
                    return;
                }
                case "list": {
                    List<ItemTarget> list = module.getTargets();
                    if (list.isEmpty()) {
                        ChatUtil.sendFormatted(String.format("%sNo ItemESP items&r", Myau.clientName));
                        return;
                    }
                    ChatUtil.sendFormatted(String.format("%sItemESP items:&r", Myau.clientName));
                    for (ItemTarget target : list) {
                        ChatUtil.sendRaw(String.format(ChatColors.formatColor("   &o%s&r &7#%s&r"), target.name, String.format("%06X", target.color)));
                    }
                    return;
                }
                case "clear":
                    module.clearTargets();
                    ChatUtil.sendFormatted(String.format("%sCleared ItemESP items&r", Myau.clientName));
                    return;
                default:
                    String value = args.size() < 3 ? null : String.join(" ", args.subList(2, args.size()));
                    if (this.applyProperty(module, args.get(1), value)) {
                        return;
                    }
            }
        }
        List<Property<?>> properties = Myau.propertyManager.properties.get(module.getClass());
        ChatUtil.sendFormatted(String.format("%s%s:&r", Myau.clientName, module.formatModule()));
        if (properties != null) {
            for (Property<?> property : properties) {
                if (property.isVisible()) {
                    ChatUtil.sendFormatted(String.format("&7»&r %s: %s&r", property.getName(), property.formatValue()));
                }
            }
        }
        ChatUtil.sendFormatted(
                String.format("%sUsage: .%s <&oadd&r/&oremove&r/&olist&r/&oclear&r>&r", Myau.clientName, command)
        );
    }

    private static class ItemColor {
        private final String name;
        private final Integer color;
        private final boolean invalidColor;
        private final boolean invalidName;
        private final String token;

        private ItemColor(String name, Integer color, boolean invalidColor, boolean invalidName, String token) {
            this.name = name;
            this.color = color;
            this.invalidColor = invalidColor;
            this.invalidName = invalidName;
            this.token = token;
        }

        private static ItemColor ok(String name, Integer color) {
            return new ItemColor(name, color, false, false, null);
        }

        private static ItemColor invalidColor(String colorToken) {
            return new ItemColor("", null, true, false, colorToken);
        }

        private static ItemColor invalidName(String name) {
            return new ItemColor("", null, false, true, name);
        }
    }
}
