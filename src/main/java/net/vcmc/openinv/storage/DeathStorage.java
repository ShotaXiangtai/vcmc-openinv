package net.vcmc.openinv.storage;

import net.vcmc.openinv.OpenInvPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 死亡前インベントリのスナップショットを管理するクラス。
 * plugins/vcmc-openinv/deaths/<uuid>/<timestamp>.yml に保存する。
 */
public class DeathStorage {

    private final OpenInvPlugin plugin;
    private final File deathDir;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public DeathStorage(OpenInvPlugin plugin) {
        this.plugin = plugin;
        this.deathDir = new File(plugin.getDataFolder(), "deaths");
        deathDir.mkdirs();
    }

    /**
     * 死亡時のインベントリスナップショットを保存する。
     * 保存後、上限を超えた古いスナップショットを削除する。
     */
    public void saveDeathInventory(UUID uuid, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        File playerDir = new File(deathDir, uuid.toString());
        playerDir.mkdirs();

        String timestamp = DATE_FORMAT.format(new Date());
        File file = new File(playerDir, timestamp + ".yml");

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("timestamp", timestamp);
        cfg.set("size", contents.length);

        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                cfg.set("contents." + i, contents[i]);
            }
        }
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && !armor[i].getType().isAir()) {
                cfg.set("armor." + i, armor[i]);
            }
        }
        if (offhand != null && !offhand.getType().isAir()) {
            cfg.set("offhand", offhand);
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[vcmc-openinv] 死亡スナップショット保存失敗 (" + uuid + "): " + e.getMessage());
            return;
        }

        pruneOldSnapshots(playerDir);
    }

    /**
     * 指定プレイヤーの死亡スナップショット一覧を新しい順で返す。
     */
    public List<File> getSnapshots(UUID uuid) {
        File playerDir = new File(deathDir, uuid.toString());
        if (!playerDir.exists()) return Collections.emptyList();

        File[] files = playerDir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return Collections.emptyList();

        // 新しいものが先頭
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return Arrays.asList(files);
    }

    /**
     * スナップショットファイルを読み込んで DeathSnapshot レコードを返す。
     */
    public DeathSnapshot loadSnapshot(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int size = cfg.getInt("size", 36);

        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            contents[i] = cfg.getItemStack("contents." + i);
        }

        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = cfg.getItemStack("armor." + i);
        }

        ItemStack offhand = cfg.getItemStack("offhand");
        String timestamp = cfg.getString("timestamp", file.getName().replace(".yml", ""));

        return new DeathSnapshot(contents, armor, offhand, timestamp);
    }

    /** アイテム数のカウント（null・AIR を除く）。 */
    public int countItems(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) count++;
        }
        for (ItemStack item : armor) {
            if (item != null && !item.getType().isAir()) count++;
        }
        if (offhand != null && !offhand.getType().isAir()) count++;
        return count;
    }

    // ─── 内部処理 ─────────────────────────────────────────────────

    private void pruneOldSnapshots(File playerDir) {
        int maxSnapshots = plugin.getConfig().getInt("max-death-snapshots", 10);
        File[] files = playerDir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length <= maxSnapshots) return;

        // 古いものから削除
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - maxSnapshots; i++) {
            files[i].delete();
        }
    }

    // ─── データクラス ──────────────────────────────────────────────

    public record DeathSnapshot(
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offhand,
            String timestamp
    ) {}
}
