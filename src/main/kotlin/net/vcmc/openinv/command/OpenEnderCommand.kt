package net.vcmc.openinv.command

import net.kyori.adventure.text.Component
import net.vcmc.openinv.OpenInvPlugin
import net.vcmc.openinv.gui.EnderViewHolder
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * /openender <player>
 * プレイヤーエンダーチェスト閲覧・編集
 */
class OpenEnderCommand(private val plugin: OpenInvPlugin) : CommandExecutor, TabCompleter {

    companion object {
        private const val ENDER_SIZE = 27
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。")
            return true
        }

        if (!sender.hasPermission("openinv.openender")) {
            sender.sendMessage(plugin.msg("no-permission"))
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("使用方法: /openender <player>"))
            return true
        }

        val targetName = args[0]
        val target = plugin.findOfflinePlayer(targetName)

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline)) {
            sender.sendMessage(plugin.msg("player-not-found", "player", targetName))
            return true
        }

        val onlineTarget = target.player
        val offline = (onlineTarget == null || !onlineTarget.isOnline)
        val targetUUID = target.uniqueId

        if (!offline && onlineTarget != null) {
            onlineTarget.scheduler.run(plugin, {
                val contents = onlineTarget.enderChest.contents

                sender.scheduler.run(plugin, {
                    openEnderGui(sender, targetUUID, targetName, false, contents)
                }, null)
            }, null)
        } else {
            val storage = plugin.playerDataStorage
            plugin.server.asyncScheduler.runNow(plugin) {
                if (!storage.hasEnderData(targetUUID)) {
                    sender.sendMessage(plugin.msg("no-data", "player", targetName))
                    return@runNow
                }
                val contents = storage.loadEnderChest(targetUUID)

                sender.scheduler.run(plugin, {
                    openEnderGui(sender, targetUUID, targetName, true, contents ?: arrayOfNulls(ENDER_SIZE))
                }, null)
            }
        }

        return true
    }

    private fun openEnderGui(
        viewer: Player,
        targetUUID: UUID,
        targetName: String,
        offline: Boolean,
        contents: Array<ItemStack?>
    ) {
        val title = (if (offline) "§7[オフライン] " else "") + "§b" + targetName + " §fのエンダーチェスト"
        val holder = EnderViewHolder(targetUUID, targetName, offline)
        val gui = plugin.server.createInventory(holder, ENDER_SIZE, Component.text(title))
        holder.setInventory(gui)
        gui.contents = contents

        viewer.openInventory(gui)
        viewer.sendMessage(plugin.msg("opening-ender", "player", targetName))
        if (offline) {
            viewer.sendMessage(plugin.msg("offline-notice", "player", targetName))
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
        return emptyList()
    }
}
