package net.vcmc.openinv.command;

import net.vcmc.openinv.OpenInvPlugin;
import net.vcmc.openinv.storage.DeathStorage;
import net.vcmc.openinv.storage.DeathStorage.DeathSnapshot;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /restoreinv <プレイヤー名> [インデックス] [confirm]
 *
 * - 引数 1つ → スナップショット一覧を表示
 * - 引数 2つ → 指定スナップショットの内容プレビュー + confirm の案内
 * - 引数 3つ (confirm) → 実際に復元する
 */
public class RestoreInvCommand implements CommandExecutor, TabCompleter {

    private final OpenInvPlugin plugin;

    public RestoreInvCommand(OpenInvPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission("openinv.restoreinv")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§c使い方: /restoreinv <プレイヤー名> [インデックス] [confirm]");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = plugin.findOfflinePlayer(targetName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage(plugin.msg("player-not-found", "player", targetName));
            return true;
        }

        UUID targetUUID = target.getUniqueId();
        DeathStorage storage = plugin.getDeathStorage();

        // ─── 一覧表示 (/restoreinv <name>) ───────────────────────
        if (args.length == 1) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                List<File> snapshots = storage.getSnapshots(targetUUID);
                if (snapshots.isEmpty()) {
                    sender.sendMessage(plugin.msg("no-deaths", "player", targetName));
                    return;
                }
                showSnapshotList(sender, targetName, snapshots, storage);
            });
            return true;
        }

        // ─── インデックス解析 ─────────────────────────────────────
        int index;
        try {
            index = Integer.parseInt(args[1]) - 1; // 1-based → 0-based
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.msg("invalid-index", "player", targetName));
            return true;
        }

        // ─── プレビュー表示 (/restoreinv <name> <index>) ─────────
        if (args.length == 2) {
            final int finalIndex = index;
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                List<File> snapshots = storage.getSnapshots(targetUUID);
                if (snapshots.isEmpty()) {
                    sender.sendMessage(plugin.msg("no-deaths", "player", targetName));
                    return;
                }
                if (finalIndex < 0 || finalIndex >= snapshots.size()) {
                    sender.sendMessage(plugin.msg("invalid-index", "player", targetName));
                    return;
                }
                DeathSnapshot snapshot = storage.loadSnapshot(snapshots.get(finalIndex));
                int itemCount = storage.countItems(snapshot.contents(), snapshot.armor(), snapshot.offhand());
                sender.sendMessage(plugin.msg("restore-prompt", "time", snapshot.timestamp()));
                sender.sendMessage("§7  アイテム数: §e" + itemCount);
                sender.sendMessage(plugin.msg("restore-confirm-hint",
                        "player", targetName,
                        "index", String.valueOf(finalIndex + 1)));
            });
            return true;
        }

        // ─── 復元実行 (/restoreinv <name> <index> confirm) ───────
        if (!args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§c確認するには 'confirm' を末尾に付けてください。");
            return true;
        }

        final int finalIndex = index;
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            List<File> snapshots = storage.getSnapshots(targetUUID);
            if (snapshots.isEmpty()) {
                sender.sendMessage(plugin.msg("no-deaths", "player", targetName));
                return;
            }
            if (finalIndex < 0 || finalIndex >= snapshots.size()) {
                sender.sendMessage(plugin.msg("invalid-index", "player", targetName));
                return;
            }
            DeathSnapshot snapshot = storage.loadSnapshot(snapshots.get(finalIndex));
            applyRestore(sender, target, snapshot, targetName);
        });

        return true;
    }

    private void showSnapshotList(CommandSender sender, String targetName,
                                  List<File> snapshots, DeathStorage storage) {
        sender.sendMessage(plugin.msg("death-list-header", "player", targetName));
        for (int i = 0; i < snapshots.size(); i++) {
            DeathSnapshot s = storage.loadSnapshot(snapshots.get(i));
            int count = storage.countItems(s.contents(), s.armor(), s.offhand());
            sender.sendMessage(plugin.msg("death-list-entry",
                    "index", String.valueOf(i + 1),
                    "time", s.timestamp(),
                    "items", String.valueOf(count)));
        }
        sender.sendMessage("§7復元するには: §6/restoreinv " + targetName + " <インデックス>");
    }

    /**
     * 非同期スレッドから呼び出す。YAML への保存を行い、オンラインならプレイヤーのスケジューラで適用する。
     */
    private void applyRestore(CommandSender sender, OfflinePlayer target,
                               DeathSnapshot snapshot, String targetName) {
        // YAML に保存（非同期スレッド上なのでそのまま実行）
        plugin.getPlayerDataStorage().saveInventory(
                target.getUniqueId(),
                snapshot.contents(),
                snapshot.armor(),
                snapshot.offhand()
        );

        Player onlineTarget = target.getPlayer();
        boolean isOnline = (onlineTarget != null && onlineTarget.isOnline());

        if (isOnline) {
            // オンライン → ターゲットのリージョンスレッドでインベントリ反映
            onlineTarget.getScheduler().run(plugin, t -> {
                PlayerInventory inv = onlineTarget.getInventory();
                inv.setStorageContents(snapshot.contents());
                inv.setArmorContents(snapshot.armor());
                inv.setItemInOffHand(snapshot.offhand() != null ? snapshot.offhand() : new ItemStack(Material.AIR));
                onlineTarget.updateInventory();
            }, null);
        }

        sender.sendMessage(plugin.msg("restore-success", "player", targetName, "time", snapshot.timestamp()));
        if (isOnline) {
            sender.sendMessage(plugin.msg("restore-online-notice", "player", targetName));
        } else {
            sender.sendMessage(plugin.msg("restore-offline-notice", "player", targetName));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                     @NotNull Command command,
                                     @NotNull String alias,
                                     @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return List.of("confirm");
        }
        return List.of();
    }
}
