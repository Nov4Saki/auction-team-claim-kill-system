package com.guildcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class TextUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer sectionSerializer = LegacyComponentSerializer.legacySection();

    public static Component format(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        if (input.contains("<") && input.contains(">")) {
            return miniMessage.deserialize("<!italic>" + input);
        }
        return legacySerializer.deserialize("&r" + input);
    }

    public static String toLegacy(Component component) {
        if (component == null) return "";
        return legacySerializer.serialize(component);
    }

    public static List<String> toLegacyList(List<Component> components) {
        List<String> list = new ArrayList<>();
        if (components == null) return list;
        for (Component c : components) {
            list.add(sectionSerializer.serialize(c));
        }
        return list;
    }
}
