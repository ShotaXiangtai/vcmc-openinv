package net.vcmc.openinv

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.vcmc.openinv.command.OpenEnderCommand
import net.vcmc.openinv.command.OpenInvCommand
import net.vcmc.openinv.command.RestoreInvCommand
import net.vcmc.openinv.listener.PlayerListener
import net.vcmc.openinv.storage.DeathStorage
import net.vcmc.openinv.storage.PlayerDataStorage
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

class OpenInvPlugin : JavaPlugin() {

    lateinit var playerDataStorage: PlayerDataStorage
        private set
    lateinit var deathStorage: DeathStorage
        private set

    override fun onEnable() {
        saveDefaultConfig()

        playerDataStorage = PlayerDataStorage(this)
        deathStorage = DeathStorage(this)

        val oiCmd = OpenInvCommand(this)
        val bukkitOi = object : Command("openinv") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                return oiCmd.onCommand(sender, this, commandLabel, args)
            }

            override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
                return oiCmd.onTabComplete(sender, this, alias, args)
            }
        }
        bukkitOi.aliases = listOf("oi")
        bukkitOi.permission = "openinv.openinv"
        server.commandMap.register(name, bukkitOi)

        val oeCmd = OpenEnderCommand(this)
        val bukkitOe = object : Command("openender") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                return oeCmd.onCommand(sender, this, commandLabel, args)
            }

            override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
                return oeCmd.onTabComplete(sender, this, alias, args)
            }
        }
        bukkitOe.aliases = listOf("oe")
        bukkitOe.permission = "openinv.openender"
        server.commandMap.register(name, bukkitOe)

        val riCmd = RestoreInvCommand(this)
        val bukkitRi = object : Command("restoreinv") {
            override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>): Boolean {
                return riCmd.onCommand(sender, this, commandLabel, args)
            }

            override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
                return riCmd.onTabComplete(sender, this, alias, args)
            }
        }
        bukkitRi.aliases = listOf("ri")
        bukkitRi.permission = "openinv.restoreinv"
        server.commandMap.register(name, bukkitRi)

        server.pluginManager.registerEvents(PlayerListener(this), this)

        logger.info("vcmc-openinv が有効化されました。")
    }

    override fun onDisable() {
        logger.info("vcmc-openinv が無効化されました。")
    }

    fun msg(key: String, vararg kvPairs: String): Component {
        var raw = config.getString("messages.$key") ?: "&cメッセージが見つかりません: $key"
        for (i in 0 until kvPairs.size step 2) {
            if (i + 1 < kvPairs.size) {
                raw = raw.replace("%${kvPairs[i]}%", kvPairs[i + 1])
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
    }

    fun findOfflinePlayer(name: String): OfflinePlayer? {
        val online = server.getPlayerExact(name)
        if (online != null) return online

        val cached = server.getOfflinePlayerIfCached(name)
        if (cached != null) return cached

        return server.offlinePlayers.firstOrNull { name.equals(it.name, ignoreCase = true) }
    }
}
