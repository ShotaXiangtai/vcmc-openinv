package net.vcmc.openinv.command

import net.kyori.adventure.text.Component
import net.vcmc.openinv.OpenInvPlugin
import net.vcmc.openinv.storage.DeathStorage
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.inventory.ItemStack
import java.io.File

/**
 * /restoreinv <player> [index] [confirm]
 * 死亡前インベントリの復元コマンド
 */
class RestoreInvCommand(private val plugin: OpenInvPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("openinv.restoreinv")) {
            sender.sendMessage(plugin.msg("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("使用方法: /restoreinv <player> [index] [confirm]"))
            return true
        }

        val targetName = args[0]
        val target = plugin.findOfflinePlayer(targetName)

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline)) {
            sender.sendMessage(plugin.msg("player-not-found", "player", targetName))
            return true
        }

        val targetUUID = target.uniqueId
        val storage = plugin.deathStorage

        // 引数1個: スナップショット一覧表示
        if (args.size == 1) {
            plugin.server.asyncScheduler.runNow(plugin) {
                val snapshots = storage.getSnapshots(targetUUID)
                if (snapshots.isEmpty()) {
                    sender.sendMessage(plugin.msg("no-deaths", "player", targetName))
                    return@runNow
                }
                showSnapshotList(sender, targetName, snapshots, storage)
            }
            return true
        }

        val index = try {
            args[1].toInt() - 1
        } catch (_: NumberFormatException) {
            sender.sendMessage(plugin.msg("invalid-index", "player", targetName))
            return true
        }

        // 引数2個: 復元確認プロンプト
        if (args.size == 2) {
            plugin.server.asyncScheduler.runNow(plugin) {
                val snapshots = storage.getSnapshots(targetUUID)
                if (snapshots.isEmpty()) {
                    sender.sendMessage(plugin.msg("no-deaths", "player", targetName))
                    return@runNow
                }
                if (index !in snapshots.indices) {
                    sender.sendMessage(plugin.msg("invalid-index", "player", targetName))
                    return@runNow
                }
                val snapshot = storage.loadSnapshot(snapshots[index])
                val itemCount = storage.countItems(snapshot.contents, snapshot.armor, snapshot.offhand)
                sender.sendMessage(plugin.msg("restore-prompt", "time", snapshot.timestamp))
                sender.sendMessage("§7  アイテム数: §e$itemCount")
                sender.sendMessage(
                    plugin.msg(
                        "restore-confirm-hint",
                        "player", targetName,
                        "index", (index + 1).toString()
                    )
                )
            }
            return true
        }

        // 引数3個: confirm で復元実行
        if (!args[2].equals("confirm", ignoreCase = true)) {
            sender.sendMessage("§c復元を実行するには末尾に confirm を指定してください。")
            return true
        }

        plugin.server.asyncScheduler.runNow(plugin) {
            val snapshots = storage.getSnapshots(targetUUID)
            if (snapshots.isEmpty()) {
                sender.sendMessage(plugin.msg("no-deaths", "player", targetName))
                return@runNow
            }
            if (index !in snapshots.indices) {
                sender.sendMessage(plugin.msg("invalid-index", "player", targetName))
                return@runNow
            }
            val snapshot = storage.loadSnapshot(snapshots[index])
            applyRestore(sender, target, snapshot, targetName)
        }

        return true
    }

    private fun showSnapshotList(
        sender: CommandSender,
        targetName: String,
        snapshots: List<File>,
        storage: DeathStorage
    ) {
        sender.sendMessage(plugin.msg("death-list-header", "player", targetName))
        for (i in snapshots.indices) {
            val s = storage.loadSnapshot(snapshots[i])
            val count = storage.countItems(s.contents, s.armor, s.offhand)
            sender.sendMessage(
                plugin.msg(
                    "death-list-entry",
                    "index", (i + 1).toString(),
                    "time", s.timestamp,
                    "items", count.toString()
                )
            )
        }
        sender.sendMessage("§7復元確認: §6/restoreinv $targetName <index>")
    }

    private fun applyRestore(
        sender: CommandSender,
        target: OfflinePlayer,
        snapshot: DeathStorage.DeathSnapshot,
        targetName: String
    ) {
        val onlineTarget = target.player
        val isOnline = (onlineTarget != null && onlineTarget.isOnline)

        plugin.playerDataStorage.saveInventory(
            target.uniqueId,
            snapshot.contents,
            snapshot.armor,
            snapshot.offhand,
            pendingSync = !isOnline
        )

        if (isOnline && onlineTarget != null) {
            onlineTarget.scheduler.run(plugin, {
                val inv = onlineTarget.inventory
                inv.storageContents = snapshot.contents
                inv.armorContents = snapshot.armor
                inv.setItemInOffHand(snapshot.offhand ?: ItemStack(Material.AIR))
                onlineTarget.updateInventory()
            }, null)
        }

        sender.sendMessage(
            plugin.msg(
                "restore-success",
                "player", targetName,
                "time", snapshot.timestamp
            )
        )
        if (isOnline) {
            sender.sendMessage(plugin.msg("restore-online-notice", "player", targetName))
        } else {
            sender.sendMessage(plugin.msg("restore-offline-notice", "player", targetName))
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val prefix = args[0].lowercase()
            return plugin.server.onlinePlayers
                .map { it.name }
                .filter { it.lowercase().startsWith(prefix) }
        }
        if (args.size == 3) {
            return listOf("confirm")
        }
        return emptyList()
    }
}
