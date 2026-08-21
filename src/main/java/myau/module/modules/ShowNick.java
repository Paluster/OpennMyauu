package myau.module.modules;

import myau.module.Module;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.UUID;

public class ShowNick extends Module {
    private static final String FAKE_MARK = EnumChatFormatting.DARK_RED.toString() + EnumChatFormatting.BOLD + " *";

    public ShowNick() {
        super("ShowNick");
    }

    public boolean isNickedPlayer(UUID uuid) {
        return uuid != null && uuid.version() == 1;
    }

    public String formatTabName(NetworkPlayerInfo info, String name) {
        if (name == null || info == null || info.getGameProfile() == null) {
            return name;
        }
        if (!this.isNickedPlayer(info.getGameProfile().getId())) {
            return name;
        }
        if (name.contains(FAKE_MARK)) {
            return name;
        }
        return name + FAKE_MARK;
    }

    public IChatComponent formatTabDisplayName(NetworkPlayerInfo info, IChatComponent displayName) {
        if (info == null || info.getGameProfile() == null || !this.isNickedPlayer(info.getGameProfile().getId())) {
            return displayName;
        }
        String name = displayName != null
                ? displayName.getFormattedText()
                : ScorePlayerTeam.formatPlayerName(info.getPlayerTeam(), info.getGameProfile().getName());
        String marked = this.formatTabName(info, name);
        if (displayName != null && marked.equals(name)) {
            return displayName;
        }
        return new ChatComponentText(marked);
    }
}
