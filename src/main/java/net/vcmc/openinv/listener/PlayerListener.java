package net.vcmc.openinv.listener;

import net.vcmc.openinv.OpenInvPlugin;
import net.vcmc.openinv.gui.EnderViewHolder;
import net.vcmc.openinv.gui.InvViewHolder;
import net.vcmc.openinv.storage.PlayerDataStorage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final OpenInvPlugin plugin;

    /**
     * 管理者がオフライン中に変更したインベントリを持つプレイヤーの UUID セット。
     * 次回ログイン時に YAML データを適用して自動クリアする。
     */
    private final Set<UUID> pendingInvSync   = Collections.synchronizedSet(new HashSet<>());
    private final Set<UUID> pendingEnderSync = Collections.synchronizedSet(new HashSet<>());

    public PlayerListener(OpenInvPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── プレイヤー退出時：YAML にスナップショット保存（非同期） ────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();

        // リージョンスレッド上でデータをコピーしてから非同期で保存
        ItemStack[] contents = inv.getStorageContents();
        ItemStack[] armor    = inv.getArmorContents();
        ItemStack   offhand  = inv.getItemInOffHand();
        ItemStack[] ender    = player.getEnderChest().getContents();
        UUID uuid = player.getUniqueId();

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            plugin.getPlayerDataStorage().saveInventory(uuid, contents, armor, offhand);
            plugin.getPlayerDataStorage().saveEnderChest(uuid, ender);
        });
    }

    // ─── プレイヤー参加時：オフライン編集データを適用 ─────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean needsInv   = pendingInvSync.remove(uuid);
        boolean needsEnder = pendingEnderSync.remove(uuid);
        if (!needsInv && !needsEnder) return;

        PlayerDataStorage storage = plugin.getPlayerDataStorage();

        // 非同期でファイル読み込み → プレイヤーのスケジューラで適用
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            ItemStack[] contents = needsInv   ? storage.loadContents(uuid)   : null;
            ItemStack[] armor    = needsInv   ? storage.loadArmor(uuid)      : null;
            ItemStack   offhand  = needsInv   ? storage.loadOffhand(uuid)    : null;
            ItemStack[] ender    = needsEnder ? storage.loadEnderChest(uuid) : null;

            player.getScheduler().run(plugin, t -> {
                if (needsInv && contents != null) {
                    PlayerInventory inv = player.getInventory();
                    inv.setStorageContents(contents);
                    inv.setArmorContents(armor);
                    inv.setItemInOffHand(offhand != null ? offhand : new ItemStack(Material.AIR));
                    player.updateInventory();
                }
                if (needsEnder && ender != null) {
                    player.getEnderChest().setContents(ender);
                }
            }, null);
        });
    }

    // ─── 死亡時：死亡前インベントリを LOWEST 優先度で保存（非同期）─

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();

        // リージョンスレッド上でコピーしてから非同期で保存
        ItemStack[] contents = inv.getStorageContents();
        ItemStack[] armor    = inv.getArmorContents();
        ItemStack   offhand  = inv.getItemInOffHand();
        UUID uuid = player.getUniqueId();

        plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                plugin.getDeathStorage().saveDeathInventory(uuid, contents, armor, offhand));
    }

    // ─── GUI クリック時：未使用スロットへの操作をキャンセル ──────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvViewHolder)) return;
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < InvViewHolder.GUI_SIZE && InvViewHolder.GUI_IS_UNUSED[slot]) {
            event.setCancelled(true);
        }
    }

    // ─── GUI クローズ時：変更を保存/適用 ─────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory gui = event.getInventory();

        if (gui.getHolder() instanceof InvViewHolder holder) {
            applyInvChanges(holder, gui);
        } else if (gui.getHolder() instanceof EnderViewHolder holder) {
            applyEnderChanges(holder, gui);
        }
    }

    private void applyInvChanges(InvViewHolder holder, Inventory gui) {
        InvViewHolder.ExtractedInventory extracted = holder.extract();
        UUID targetUUID = holder.getTargetUUID();
        Player target = plugin.getServer().getPlayer(targetUUID);

        if (target != null && target.isOnline()) {
            // オンライン → ターゲットのリージョンスレッドで直接反映
            target.getScheduler().run(plugin, t -> {
                PlayerInventory inv = target.getInventory();
                inv.setStorageContents(extracted.contents());
                inv.setArmorContents(extracted.armor());
                inv.setItemInOffHand(extracted.offhand() != null
                        ? extracted.offhand()
                        : new ItemStack(Material.AIR));
                target.updateInventory();
            }, null);
        } else {
            // オフライン → pendingInvSync に先に追加してから非同期保存
            pendingInvSync.add(targetUUID);
            plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                    plugin.getPlayerDataStorage().saveInventory(
                            targetUUID,
                            extracted.contents(),
                            extracted.armor(),
                            extracted.offhand()
                    ));
        }
    }

    private void applyEnderChanges(EnderViewHolder holder, Inventory gui) {
        ItemStack[] contents = gui.getContents();
        UUID targetUUID = holder.getTargetUUID();
        Player target = plugin.getServer().getPlayer(targetUUID);

        if (target != null && target.isOnline()) {
            // オンライン → ターゲットのリージョンスレッドで直接反映
            target.getScheduler().run(plugin, t ->
                    target.getEnderChest().setContents(contents), null);
        } else {
            // オフライン → pendingEnderSync に先に追加してから非同期保存
            pendingEnderSync.add(targetUUID);
            plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                    plugin.getPlayerDataStorage().saveEnderChest(targetUUID, contents));
        }
    }
}
