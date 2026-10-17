package net.vcmc.openinv.storage

import net.vcmc.openinv.OpenInvPlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 死亡時インベントリスナップショット管理
 */
class DeathStorage(private val plugin: OpenInvPlugin) {

    private val deathDir = File(plugin.dataFolder, "deaths").apply { mkdirs() }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }

    @Synchronized
    fun saveDeathInventory(uuid: UUID, contents: Array<ItemStack?>, armor: Array<ItemStack?>, offhand: ItemStack?) {
        val playerDir = File(deathDir, uuid.toString()).apply { mkdirs() }
        val timestamp = DATE_FORMAT.format(LocalDateTime.now())
        var file = File(playerDir, "$timestamp.yml")
        var suffix = 1
        while (file.exists()) {
            file = File(playerDir, "${timestamp}_$suffix.yml")
            suffix++
        }

        val cfg = YamlConfiguration()
        cfg.set("timestamp", timestamp)
        cfg.set("size", contents.size)

        for (i in contents.indices) {
            val item = contents[i]
            if (item != null && !item.type.isAir) {
                cfg.set("contents.$i", item)
            }
        }
        for (i in armor.indices) {
            val item = armor[i]
            if (item != null && !item.type.isAir) {
                cfg.set("armor.$i", item)
            }
        }
        if (offhand != null && !offhand.type.isAir) {
            cfg.set("offhand", offhand)
        }

        try {
            cfg.save(file)
        } catch (e: IOException) {
            plugin.logger.severe("[vcmc-openinv] 死亡スナップショット保存失敗 ($uuid): ${e.message}")
            return
        }

        pruneOldSnapshots(playerDir)
    }

    fun getSnapshots(uuid: UUID): List<File> {
        val playerDir = File(deathDir, uuid.toString())
        if (!playerDir.exists()) return emptyList()

        val files = playerDir.listFiles { _, name -> name.endsWith(".yml") } ?: return emptyList()
        return files.sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
    }

    fun loadSnapshot(file: File): DeathSnapshot {
        val cfg = YamlConfiguration.loadConfiguration(file)
        val size = cfg.getInt("size", 36)

        val contents = arrayOfNulls<ItemStack>(size)
        for (i in 0 until size) {
            contents[i] = cfg.getItemStack("contents.$i")
        }

        val armor = arrayOfNulls<ItemStack>(4)
        for (i in 0 until 4) {
            armor[i] = cfg.getItemStack("armor.$i")
        }

        val offhand = cfg.getItemStack("offhand")
        val timestamp = cfg.getString("timestamp") ?: file.name.removeSuffix(".yml")

        return DeathSnapshot(contents, armor, offhand, timestamp)
    }

    fun countItems(contents: Array<ItemStack?>, armor: Array<ItemStack?>, offhand: ItemStack?): Int {
        var count = 0
        for (item in contents) {
            if (item != null && !item.type.isAir) count++
        }
        for (item in armor) {
            if (item != null && !item.type.isAir) count++
        }
        if (offhand != null && !offhand.type.isAir) count++
        return count
    }

    private fun pruneOldSnapshots(playerDir: File) {
        val maxSnapshots = plugin.config.getInt("max-death-snapshots", 10)
        val files = playerDir.listFiles { _, name -> name.endsWith(".yml") } ?: return
        if (files.size <= maxSnapshots) return

        val sorted = files.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
        for (i in 0 until sorted.size - maxSnapshots) {
            sorted[i].delete()
        }
    }

    class DeathSnapshot(
        val contents: Array<ItemStack?>,
        val armor: Array<ItemStack?>,
        val offhand: ItemStack?,
        val timestamp: String
    )
}
