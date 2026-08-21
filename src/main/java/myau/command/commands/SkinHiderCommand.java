package myau.command.commands;

import myau.Myau;
import myau.command.Command;
import myau.module.modules.SkinHider;
import myau.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class SkinHiderCommand extends Command {
    public SkinHiderCommand() {
        super(new ArrayList<String>(Arrays.asList("skinhider")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        SkinHider module = (SkinHider) Myau.moduleManager.modules.get(SkinHider.class);
        String command = args.get(0).toLowerCase(Locale.ROOT);
        if (args.size() >= 2 && args.get(1).equalsIgnoreCase("folder")) {
            if (module.openFolder()) {
                ChatUtil.sendFormatted(String.format("%sOpened SkinHider folder. Put &oskin.png&r and &ocape.png&r there.&r", Myau.clientName));
            } else {
                ChatUtil.sendFormatted(String.format("%sCould not open folder (&o%s&r)&r", Myau.clientName, module.getFolder().getAbsolutePath()));
            }
            return;
        }
        ChatUtil.sendFormatted(String.format("%s%s:&r", Myau.clientName, module.formatModule()));
        ChatUtil.sendFormatted(String.format("%sUsage: .%s folder&r", Myau.clientName, command));
    }
}
