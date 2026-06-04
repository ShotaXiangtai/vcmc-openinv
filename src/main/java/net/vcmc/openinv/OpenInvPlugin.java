package net.vcmc.openinv;

import net.vcmc.openinv.command.OpenEnderCommand;
import net.vcmc.openinv.command.OpenInvCommand;
import net.vcmc.openinv.command.RestoreInvCommand;
import net.vcmc.openinv.listener.PlayerListener;
import net.vcmc.openinv.storage.DeathStorage;
import net.vcmc.openinv.storage.PlayerDataStorage;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Objects;

public class OpenInvPlugin extends JavaPlugin {

    private PlayerDataStorage playerDataStorage;
    private DeathStorage deathStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        playerDataStorage = new PlayerDataStorage(this);
        deathStorage = new DeathStorage(this);

        Objects.requireNonNull(getCommand("openinv")).setExecutor(new OpenInvCommand(this));
        Objects.requireNonNull(getCommand("openender")).setExecutor(new OpenEnderCommand(this));
        Objects.requireNonNull(getCommand("restoreinv")).setExecutor(new RestoreInvCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("vcmc-openinv が有効化されました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("vcmc-openinv が無効化されました。");
    }

    /** メッセージを config から取得し & → § 変換して返す */
    public String msg(String key) {
        String raw = getConfig().getString("messages." + key, "&cメッセージ未設定: " + key);
        return raw.replace("&", "§");
    }

    /** %key% → value 形式でプレースホルダーを置換してメッセージを返す */
    public String msg(String key, String... kvPairs) {
        String s = msg(key);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            s = s.replace("%" + kvPairs[i] + "%", kvPairs[i + 1]);
        }
        return s;
    }

    /**
     * 名前からオフラインプレイヤーを検索する。
     * オンラインプレイヤーを優先し、次にキャッシュ済みオフラインプレイヤーを検索する。
     */
    public OfflinePlayer findOfflinePlayer(String name) {
        // オンライン検索
        var online = getServer().getPlayerExact(name);
        if (online != null) return online;

        // キャッシュ済みオフラインプレイヤーを検索
        return Arrays.stream(getServer().getOfflinePlayers())
                .filter(p -> name.equalsIgnoreCase(p.getName()))
                .findFirst()
                .orElse(null);
    }

    public PlayerDataStorage getPlayerDataStorage() {
        return playerDataStorage;
    }

    public DeathStorage getDeathStorage() {
        return deathStorage;
    }
}
