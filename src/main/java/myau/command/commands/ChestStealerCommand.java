package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.enums.ChatColors;
import myau.module.modules.ChestStealer;
import myau.module.modules.ChestStealer.SkipItem;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.util.ChatUtil;
import myau.util.RegistryParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ChestStealerCommand extends Command {
    public ChestStealerCommand() {
        super(new ArrayList<>(Arrays.asList("cheststealer")));
    }

    private ChestStealer getModule() {
        return (ChestStealer) Myau.moduleManager.modules.get(ChestStealer.class);
    }

    private boolean applyProperty(ChestStealer module, String name, String value) {
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

    @Override
    public void runCommand(ArrayList<String> args) {
        ChestStealer module = this.getModule();
        String command = args.get(0).toLowerCase(Locale.ROOT);
        if (args.size() >= 2) {
            String subCommand = args.get(1).toLowerCase(Locale.ROOT);
            switch (subCommand) {
                case "skip": {
                    if (args.size() < 3) {
                        ChatUtil.sendFormatted(String.format("%sUsage: .%s skip <&oregistry[:data]&r>&r", Myau.clientName, command));
                        return;
                    }
                    String name = this.joinArgs(args, 2, args.size());
                    RegistryParser.Parsed detailed = ChestStealer.parseDetailed(name);
                    if (!detailed.ok()) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(name)));
                        return;
                    }
                    SkipItem parsed = ChestStealer.parseItem(name);
                    if (parsed == null) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(name)));
                        return;
                    }
                    if (!module.addSkip(parsed)) {
                        ChatUtil.sendFormatted(String.format("%sAlready skipping &o%s&r", Myau.clientName, parsed.name));
                        return;
                    }
                    ChatUtil.sendFormatted(String.format("%sNow skipping &o%s&r", Myau.clientName, parsed.name));
                    return;
                }
                case "unskip": {
                    if (args.size() < 3) {
                        ChatUtil.sendFormatted(String.format("%sUsage: .%s unskip <&oregistry[:data]&r>&r", Myau.clientName, command));
                        return;
                    }
                    String name = this.joinArgs(args, 2, args.size());
                    RegistryParser.Parsed detailed = ChestStealer.parseDetailed(name);
                    if (!detailed.ok()) {
                        ChatUtil.sendFormatted(String.format("%s%s&r", Myau.clientName, RegistryParser.failMessage(name)));
                        return;
                    }
                    if (!module.removeSkip(name)) {
                        ChatUtil.sendFormatted(String.format("%sNot skipping &o%s&r", Myau.clientName, detailed.canonical));
                        return;
                    }
                    ChatUtil.sendFormatted(String.format("%sNo longer skipping &o%s&r", Myau.clientName, detailed.canonical));
                    return;
                }
                case "list": {
                    List<SkipItem> list = module.getSkipItems();
                    if (list.isEmpty()) {
                        ChatUtil.sendFormatted(String.format("%sNot skipping any items&r", Myau.clientName));
                        return;
                    }
                    ChatUtil.sendFormatted(String.format("%sSkipping:&r", Myau.clientName));
                    for (SkipItem skip : list) {
                        ChatUtil.sendRaw(String.format(ChatColors.formatColor("   &o%s&r"), skip.name));
                    }
                    return;
                }
                case "clear":
                    module.clearSkipItems();
                    ChatUtil.sendFormatted(String.format("%sNo longer skipping any items&r", Myau.clientName));
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
                String.format("%sUsage: .%s <&oskip&r/&ounskip&r/&olist&r/&oclear&r>&r", Myau.clientName, command)
        );
    }
}
