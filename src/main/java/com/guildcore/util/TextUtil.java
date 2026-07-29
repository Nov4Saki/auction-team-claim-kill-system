package com.guildcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class TextUtil {
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public static Component format(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        if (input.contains("<") && input.contains(">")) {
            return miniMessage.deserialize(input).decoration(TextDecoration.ITALIC, false);
        }
        return legacySerializer.deserialize(input).decoration(TextDecoration.ITALIC, false);
    }

    public static String toLegacy(Component component) {
        if (component == null) return "";
        return legacySerializer.serialize(component);
    }
}
