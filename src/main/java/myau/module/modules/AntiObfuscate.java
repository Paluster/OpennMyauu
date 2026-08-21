package myau.module.modules;

import myau.module.Module;

public class AntiObfuscate extends Module {
    public AntiObfuscate() {
        super("AntiObfuscate");
    }

    public String stripObfuscated(String input) {
        return input.replaceAll("§k", "");
    }
}
