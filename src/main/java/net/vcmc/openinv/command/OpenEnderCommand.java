package net.vcmc.openinv.command;

import net.kyori.adventure.text.Component;
import net.vcmc.openinv.OpenInvPlugin;
import net.vcmc.openinv.gui.EnderViewHolder;
import net.vcmc.openinv.storage.PlayerDataStorage;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /openender <プレイヤー名>
 * オンライン・オフライン問わずプレイヤーのエンダーチェストを GUI で開く。
 */
public class OpenEnderCommand implements CommandExecutor, TabCompleter {

    private static final int ENDER_SIZE = 27;

    private final OpenInvPlugin plugin;

    public OpenEnderCommand(OpenInvPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (!viewer.hasPermission("openinv.openender")) {
            viewer.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length < 1) {
            viewer.sendMessage("§c使い方: /openender <プレイヤー名>");
            return true;
        }

        String targetName = args[0];
        OfflinePlayer target = plugin.findOfflinePlayer(targetName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            viewer.sendMessage(plugin.msg("player-not-found", "player", targetName));
            return true;
        }

        Player onlineTarget = target.getPlayer();
        boolean offline = (onlineTarget == null || !onlineTarget.isOnline());
        UUID targetUUID = target.getUniqueId();

        if (!offline) {
            // オンライン：ターゲットのリージョンでデータ取得 → ビューワーのリージョンで GUI を開く
            onlineTarget.getScheduler().run(plugin, t -> {
                ItemStack[] contents = onlineTarget.getEnderChest().getContents();

                viewer.getScheduler().run(plugin, t2 ->
                        openEnderGui(viewer, targetUUID, targetName, false, contents), null);
            }, null);
        } else {
            // オフライン：非同期でYAML読み込み → ビューワーのリージョンで GUI を開く
            PlayerDataStorage storage = plugin.getPlayerDataStorage();
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                if (!storage.hasEnderData(targetUUID)) {
                    viewer.sendMessage(plugin.msg("no-data", "player", targetName));
                    return;
                }
                ItemStack[] contents = storage.loadEnderChest(targetUUID);

                viewer.getScheduler().run(plugin, t ->
                        openEnderGui(viewer, targetUUID, targetName, true,
                                contents != null ? contents : new ItemStack[ENDER_SIZE]), null);
            });
        }

        return true;
    }

    private void openEnderGui(Player viewer, UUID targetUUID, String targetName, boolean offline,
                               ItemStack[] contents) {
        String title = (offline ? "§7[オフライン] " : "") + "§b" + targetName + " §fのエンダーチェスト";
        EnderViewHolder holder = new EnderViewHolder(targetUUID, targetName, offline);
        Inventory gui = plugin.getServer().createInventory(holder, ENDER_SIZE,
                Component.text(title));
        holder.setInventory(gui);
        gui.setContents(contents);

        viewer.openInventory(gui);
        viewer.sendMessage(plugin.msg("opening-ender", "player", targetName));
        if (offline) {
            viewer.sendMessage(plugin.msg("offline-notice", "player", targetName));
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
        return List.of();
    }
}
