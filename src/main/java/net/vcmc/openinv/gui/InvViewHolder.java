package net.vcmc.openinv.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;

/**
 * プレイヤーインベントリ閲覧用の GUI ホルダー。
 *
 * GUI レイアウト（45スロット = 5行 × 9列）:
 *   GUI  0-26  → プレイヤーインベントリ contents[9-35]  （メイン3行）
 *   GUI 27-35  → プレイヤーインベントリ contents[0-8]   （ホットバー）
 *   GUI 36     → armor[3] ヘルメット
 *   GUI 37     → armor[2] チェストプレート
 *   GUI 38     → armor[1] レギンス
 *   GUI 39     → armor[0] ブーツ
 *   GUI 40     → オフハンド
 *   GUI 41-44  → 未使用
 *
 * armor 配列インデックスは getArmorContents() 準拠:
 *   armor[0]=ブーツ, armor[1]=レギンス, armor[2]=チェストプレート, armor[3]=ヘルメット
 */
public class InvViewHolder implements InventoryHolder {

    // GUI スロット → プレイヤースロット種別ごとのインデックス
    public static final int GUI_SIZE = 45;

    // GUI slot → contents[] インデックス (-1 = 対応なし)
    public static final int[] GUI_TO_CONTENTS = new int[GUI_SIZE];
    // GUI slot → armor[] インデックス (-1 = 対応なし)
    public static final int[] GUI_TO_ARMOR = new int[GUI_SIZE];
    // GUI slot がオフハンドか
    public static final boolean[] GUI_IS_OFFHAND = new boolean[GUI_SIZE];
    // GUI slot が未使用（フィラーで埋める）か
    public static final boolean[] GUI_IS_UNUSED = new boolean[GUI_SIZE];

    // 未使用スロットを塞ぐフィラーアイテム（グレーのガラス板）
    public static final ItemStack FILLER;

    static {
        Arrays.fill(GUI_TO_CONTENTS, -1);
        Arrays.fill(GUI_TO_ARMOR, -1);

        // GUI 0-26 → contents[9-35]
        for (int i = 0; i < 27; i++) {
            GUI_TO_CONTENTS[i] = i + 9;
        }
        // GUI 27-35 → contents[0-8] (ホットバー)
        for (int i = 0; i < 9; i++) {
            GUI_TO_CONTENTS[27 + i] = i;
        }
        // 防具（上から下へ表示：ヘルメット, チェストプレート, レギンス, ブーツ）
        GUI_TO_ARMOR[36] = 3; // ヘルメット
        GUI_TO_ARMOR[37] = 2; // チェストプレート
        GUI_TO_ARMOR[38] = 1; // レギンス
        GUI_TO_ARMOR[39] = 0; // ブーツ
        // オフハンド
        GUI_IS_OFFHAND[40] = true;
        // GUI 41-44 は未使用
        for (int i = 41; i <= 44; i++) {
            GUI_IS_UNUSED[i] = true;
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        FILLER = filler;
    }

    private final UUID targetUUID;
    private final String targetName;
    private final boolean offline;
    private Inventory inventory;

    public InvViewHolder(UUID targetUUID, String targetName, boolean offline) {
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.offline = offline;
    }

    /**
     * プレイヤーインベントリの内容を GUI インベントリに流し込む。
     *
     * @param contents getStorageContents() の配列（36要素）
     * @param armor    getArmorContents() の配列（4要素）
     * @param offhand  オフハンドアイテム
     */
    public void populate(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        for (int gui = 0; gui < GUI_SIZE; gui++) {
            int contSlot = GUI_TO_CONTENTS[gui];
            int armorSlot = GUI_TO_ARMOR[gui];

            if (contSlot >= 0 && contSlot < contents.length) {
                inventory.setItem(gui, contents[contSlot]);
            } else if (armorSlot >= 0 && armor != null && armorSlot < armor.length) {
                inventory.setItem(gui, armor[armorSlot]);
            } else if (GUI_IS_OFFHAND[gui]) {
                inventory.setItem(gui, offhand);
            } else if (GUI_IS_UNUSED[gui]) {
                inventory.setItem(gui, FILLER);
            }
        }
    }

    /**
     * GUI インベントリの内容をプレイヤースロット配列に抽出する。
     */
    public ExtractedInventory extract() {
        ItemStack[] contents = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4];
        ItemStack offhand = null;

        for (int gui = 0; gui < GUI_SIZE; gui++) {
            ItemStack item = inventory.getItem(gui);
            int contSlot = GUI_TO_CONTENTS[gui];
            int armorSlot = GUI_TO_ARMOR[gui];

            if (contSlot >= 0) {
                contents[contSlot] = item;
            } else if (armorSlot >= 0) {
                armor[armorSlot] = item;
            } else if (GUI_IS_OFFHAND[gui]) {
                offhand = item;
            }
        }

        return new ExtractedInventory(contents, armor, offhand);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID getTargetUUID() { return targetUUID; }
    public String getTargetName() { return targetName; }
    public boolean isOffline() { return offline; }

    public record ExtractedInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {}
}
