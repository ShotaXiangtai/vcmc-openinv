package net.vcmc.openinv.command;

import net.kyori.adventure.text.Component;
import net.vcmc.openinv.OpenInvPlugin;
import net.vcmc.openinv.gui.InvViewHolder;
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
 * /openinv <プレイヤー名>
 * オンライン・オフライン問わずプレイヤーのインベントリを GUI で開く。
 */
public class OpenInvCommand implements CommandExecutor, TabCompleter {

    private final OpenInvPlugin plugin;

    public OpenInvCommand(OpenInvPlugin plugin) {
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

        if (!viewer.hasPermission("openinv.openinv")) {
            viewer.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length < 1) {
            viewer.sendMessage("§c使い方: /openinv <プレイヤー名>");
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
                ItemStack[] contents = onlineTarget.getInventory().getStorageContents();
                ItemStack[] armor    = onlineTarget.getInventory().getArmorContents();
                ItemStack   offhand  = onlineTarget.getInventory().getItemInOffHand();

                viewer.getScheduler().run(plugin, t2 ->
                        openInvGui(viewer, targetUUID, targetName, false, contents, armor, offhand), null);
            }, null);
        } else {
            // オフライン：非同期でYAML読み込み → ビューワーのリージョンで GUI を開く
            PlayerDataStorage storage = plugin.getPlayerDataStorage();
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                if (!storage.hasInventoryData(targetUUID)) {
                    viewer.sendMessage(plugin.msg("no-data", "player", targetName));
                    return;
                }
                ItemStack[] contents = storage.loadContents(targetUUID);
                ItemStack[] armor    = storage.loadArmor(targetUUID);
                ItemStack   offhand  = storage.loadOffhand(targetUUID);

                viewer.getScheduler().run(plugin, t ->
                        openInvGui(viewer, targetUUID, targetName, true,
                                contents != null ? contents : new ItemStack[36],
                                armor   != null ? armor    : new ItemStack[4],
                                offhand), null);
            });
        }

        return true;
    }

    private void openInvGui(Player viewer, UUID targetUUID, String targetName, boolean offline,
                             ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        String title = (offline ? "§7[オフライン] " : "") + "§c" + targetName + " §fのインベントリ";
        InvViewHolder holder = new InvViewHolder(targetUUID, targetName, offline);
        Inventory gui = plugin.getServer().createInventory(holder, InvViewHolder.GUI_SIZE,
                Component.text(title));
        holder.setInventory(gui);
        holder.populate(contents, armor, offhand);

        viewer.openInventory(gui);
        viewer.sendMessage(plugin.msg("opening-inv", "player", targetName));
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
