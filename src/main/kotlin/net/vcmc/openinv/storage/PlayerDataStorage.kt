package net.vcmc.openinv.storage

import net.vcmc.openinv.OpenInvPlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * プレイヤーインベントリ・エンダーチェストの YAML ファイル保存・読み込み
 */
class PlayerDataStorage(private val plugin: OpenInvPlugin) {

    private val inventoryDir = File(plugin.dataFolder, "inventories").apply { mkdirs() }
    private val enderDir = File(plugin.dataFolder, "enderchests").apply { mkdirs() }

    @Synchronized
    fun saveInventory(
        uuid: UUID,
        contents: Array<ItemStack?>,
        armor: Array<ItemStack?>,
        offhand: ItemStack?,
        pendingSync: Boolean = false
    ) {
        val file = File(inventoryDir, "$uuid.yml")
        val cfg = YamlConfiguration()

        cfg.set("pending-sync", pendingSync)
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

        trySave(cfg, file, "inventory", uuid)
    }

    @Synchronized
    fun consumePendingInvSync(uuid: UUID): Boolean {
        val file = File(inventoryDir, "$uuid.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        if (!cfg.getBoolean("pending-sync", false)) return false
        cfg.set("pending-sync", false)
        trySave(cfg, file, "inventory", uuid)
        return true
    }

    @Synchronized
    fun loadContents(uuid: UUID): Array<ItemStack?>? {
        val file = File(inventoryDir, "$uuid.yml")
        if (!file.exists()) return null

        val cfg = YamlConfiguration.loadConfiguration(file)
        val size = cfg.getInt("size", 36)
        val contents = arrayOfNulls<ItemStack>(size)
        for (i in 0 until size) {
            contents[i] = cfg.getItemStack("contents.$i")
        }
        return contents
    }

    @Synchronized
    fun loadArmor(uuid: UUID): Array<ItemStack?> {
        val file = File(inventoryDir, "$uuid.yml")
        if (!file.exists()) return arrayOfNulls(4)

        val cfg = YamlConfiguration.loadConfiguration(file)
        val armor = arrayOfNulls<ItemStack>(4)
        for (i in 0 until 4) {
            armor[i] = cfg.getItemStack("armor.$i")
        }
        return armor
    }

    @Synchronized
    fun loadOffhand(uuid: UUID): ItemStack? {
        val file = File(inventoryDir, "$uuid.yml")
        if (!file.exists()) return null
        return YamlConfiguration.loadConfiguration(file).getItemStack("offhand")
    }

    @Synchronized
    fun hasInventoryData(uuid: UUID): Boolean {
        return File(inventoryDir, "$uuid.yml").exists()
    }

    @Synchronized
    fun saveEnderChest(uuid: UUID, contents: Array<ItemStack?>, pendingSync: Boolean = false) {
        val file = File(enderDir, "$uuid.yml")
        val cfg = YamlConfiguration()

        cfg.set("pending-sync", pendingSync)
        cfg.set("size", contents.size)
        for (i in contents.indices) {
            val item = contents[i]
            if (item != null && !item.type.isAir) {
                cfg.set("contents.$i", item)
            }
        }

        trySave(cfg, file, "ender chest", uuid)
    }

    @Synchronized
    fun consumePendingEnderSync(uuid: UUID): Boolean {
        val file = File(enderDir, "$uuid.yml")
        if (!file.exists()) return false
        val cfg = YamlConfiguration.loadConfiguration(file)
        if (!cfg.getBoolean("pending-sync", false)) return false
        cfg.set("pending-sync", false)
        trySave(cfg, file, "ender chest", uuid)
        return true
    }

    @Synchronized
    fun loadEnderChest(uuid: UUID): Array<ItemStack?>? {
        val file = File(enderDir, "$uuid.yml")
        if (!file.exists()) return null

        val cfg = YamlConfiguration.loadConfiguration(file)
        val size = cfg.getInt("size", 27)
        val contents = arrayOfNulls<ItemStack>(size)
        for (i in 0 until size) {
            contents[i] = cfg.getItemStack("contents.$i")
        }
        return contents
    }

    @Synchronized
    fun hasEnderData(uuid: UUID): Boolean {
        return File(enderDir, "$uuid.yml").exists()
    }

    private fun trySave(cfg: YamlConfiguration, file: File, label: String, uuid: UUID) {
        try {
            cfg.save(file)
        } catch (e: IOException) {
            plugin.logger.severe("[vcmc-openinv] $label 保存失敗 ($uuid): ${e.message}")
        }
    }
}
