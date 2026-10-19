package tr.pyro.minions;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Texture profiles are constructed locally: no blocking account/skin lookups. */
final class SkinService {
    private final PyroMinions plugin;
    private final Map<String, ItemStack> heads = new HashMap<>();
    SkinService(PyroMinions plugin) { this.plugin = plugin; }
    void clear() { heads.clear(); }
    String key(String skin) { return plugin.data().isConfigurationSection("skins." + skin) ? skin : "classic"; }
    static URL texture(String value) {
        try {
            String text = value == null ? "" : value.strip();
            if (text.matches("[a-fA-F0-9]{32,64}")) text = "https://textures.minecraft.net/texture/" + text;
            URI uri = URI.create(text);
            if (!Set.of("http", "https").contains(uri.getScheme()) || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().matches("/texture/[a-fA-F0-9]{32,64}")) throw new IllegalArgumentException();
            return uri.toURL();
        } catch (Exception e) { throw new IllegalArgumentException("Kafa dokusu Minecraft texture adresi veya texture hash'i olmalı: " + value); }
    }
    ItemStack head(String selected, MinionType type) {
        String skin = key(selected);
        String path = "skins." + skin;
        String texture = plugin.data().getString(path + ".type-heads." + type.key(), plugin.data().getString(path + ".head-texture", ""));
        return heads.computeIfAbsent(texture, unused -> {
            URL url = texture(texture);
            var item = new ItemStack(Material.PLAYER_HEAD);
            var meta = (SkullMeta)item.getItemMeta();
            var profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(url.toString().getBytes(StandardCharsets.UTF_8)), "PyroMinion");
            var textures = profile.getTextures(); textures.setSkin(url); profile.setTextures(textures);
            meta.setOwnerProfile(profile); item.setItemMeta(meta); return item;
        }).clone();
    }
    int color(String skin, String part) {
        var config = plugin.data(); String path = "skins." + key(skin);
        return Integer.parseInt(config.getString(path + "." + part + "-color", config.getString(path + ".color", "808080")), 16);
    }
}
