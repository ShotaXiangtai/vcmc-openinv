package net.vcmc.openinv.storage;

import net.vcmc.openinv.OpenInvPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * プレイヤーのインベントリ・エンダーチェストを YAML ファイルに保存・読み込みするクラス。
 * オフラインプレイヤーのデータはここから提供される。
 */
public class PlayerDataStorage {

    private final OpenInvPlugin plugin;
    private final File inventoryDir;
    private final File enderDir;

    public PlayerDataStorage(OpenInvPlugin plugin) {
        this.plugin = plugin;
        this.inventoryDir = new File(plugin.getDataFolder(), "inventories");
        this.enderDir = new File(plugin.getDataFolder(), "enderchests");
        inventoryDir.mkdirs();
        enderDir.mkdirs();
    }

    // ─── インベントリ保存 ──────────────────────────────────────────

    /**
     * プレイヤーのインベントリ（メイン36スロット・防具4スロット・オフハンド）を保存する。
     *
     * @param uuid     対象プレイヤー UUID
     * @param contents getStorageContents() の結果（36スロット）
     * @param armor    getArmorContents() の結果（4スロット: 0=ブーツ, 1=レギンス, 2=チェストプレート, 3=ヘルメット）
     * @param offhand  オフハンドアイテム
     */
    public void saveInventory(UUID uuid, ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        File file = new File(inventoryDir, uuid + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

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

        trySave(cfg, file, "inventory", uuid);
    }

    /** メインインベントリ（36スロット）を読み込む。データがなければ null を返す。 */
    public ItemStack[] loadContents(UUID uuid) {
        File file = new File(inventoryDir, uuid + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int size = cfg.getInt("size", 36);
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            contents[i] = cfg.getItemStack("contents." + i);
        }
        return contents;
    }

    /** 防具（4スロット）を読み込む。ファイルがなければ空配列を返す。 */
    public ItemStack[] loadArmor(UUID uuid) {
        File file = new File(inventoryDir, uuid + ".yml");
        if (!file.exists()) return new ItemStack[4];

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = cfg.getItemStack("armor." + i);
        }
        return armor;
    }

    /** オフハンドアイテムを読み込む。なければ null を返す。 */
    public ItemStack loadOffhand(UUID uuid) {
        File file = new File(inventoryDir, uuid + ".yml");
        if (!file.exists()) return null;
        return YamlConfiguration.loadConfiguration(file).getItemStack("offhand");
    }

    /** インベントリの保存データが存在するかを返す。 */
    public boolean hasInventoryData(UUID uuid) {
        return new File(inventoryDir, uuid + ".yml").exists();
    }

    // ─── エンダーチェスト保存 ─────────────────────────────────────

    /** プレイヤーのエンダーチェスト（27スロット）を保存する。 */
    public void saveEnderChest(UUID uuid, ItemStack[] contents) {
        File file = new File(enderDir, uuid + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("size", contents.length);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                cfg.set("contents." + i, contents[i]);
            }
        }

        trySave(cfg, file, "ender chest", uuid);
    }

    /** エンダーチェスト（27スロット）を読み込む。データがなければ null を返す。 */
    public ItemStack[] loadEnderChest(UUID uuid) {
        File file = new File(enderDir, uuid + ".yml");
        if (!file.exists()) return null;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int size = cfg.getInt("size", 27);
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            contents[i] = cfg.getItemStack("contents." + i);
        }
        return contents;
    }

    /** エンダーチェストの保存データが存在するかを返す。 */
    public boolean hasEnderData(UUID uuid) {
        return new File(enderDir, uuid + ".yml").exists();
    }

    // ─── ユーティリティ ───────────────────────────────────────────

    private void trySave(YamlConfiguration cfg, File file, String label, UUID uuid) {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[vcmc-openinv] " + label + " の保存失敗 (" + uuid + "): " + e.getMessage());
        }
    }
}
