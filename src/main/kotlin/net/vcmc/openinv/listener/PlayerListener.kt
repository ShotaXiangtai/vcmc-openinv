package net.vcmc.openinv.listener

import net.vcmc.openinv.OpenInvPlugin
import net.vcmc.openinv.gui.EnderViewHolder
import net.vcmc.openinv.gui.InvViewHolder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.Collections
import java.util.HashSet
import java.util.UUID

class PlayerListener(private val plugin: OpenInvPlugin) : Listener {

    /**
     * オフライン中に編集されたプレイヤーの UUID セット
     * ログイン時に YAML から最新データを反映する
     */
    private val pendingInvSync: MutableSet<UUID> = Collections.synchronizedSet(HashSet())
    private val pendingEnderSync: MutableSet<UUID> = Collections.synchronizedSet(HashSet())

    // プレイヤー退出時: YAML へスナップショット保存
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val inv = player.inventory

        val contents = inv.storageContents.map { it?.clone() }.toTypedArray()
        val armor = inv.armorContents.map { it?.clone() }.toTypedArray()
        val offhand = inv.itemInOffHand.clone()
        val ender = player.enderChest.contents.map { it?.clone() }.toTypedArray()
        val uuid = player.uniqueId

        plugin.server.asyncScheduler.runNow(plugin) {
            plugin.playerDataStorage.saveInventory(uuid, contents, armor, offhand, pendingSync = false)
            plugin.playerDataStorage.saveEnderChest(uuid, ender, pendingSync = false)
        }
    }

    // プレイヤー参加時: オフライン中に編集されたデータを適用
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val storage = plugin.playerDataStorage

        plugin.server.asyncScheduler.runNow(plugin) {
            val needsInv = pendingInvSync.remove(uuid) || storage.consumePendingInvSync(uuid)
            val needsEnder = pendingEnderSync.remove(uuid) || storage.consumePendingEnderSync(uuid)
            if (!needsInv && !needsEnder) return@runNow

            val contents = if (needsInv) storage.loadContents(uuid) else null
            val armor = if (needsInv) storage.loadArmor(uuid) else null
            val offhand = if (needsInv) storage.loadOffhand(uuid) else null
            val ender = if (needsEnder) storage.loadEnderChest(uuid) else null

            player.scheduler.run(plugin, {
                if (needsInv && contents != null) {
                    val inv = player.inventory
                    inv.storageContents = contents
                    inv.armorContents = armor ?: arrayOfNulls(4)
                    inv.setItemInOffHand(offhand ?: ItemStack(Material.AIR))
                    player.updateInventory()
                }
                if (needsEnder && ender != null) {
                    player.enderChest.contents = ender
                }
            }, null)
        }
    }

    // 死亡時: 直前インベントリ保存 (LOWEST でアイテムドロップ前に取得)
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val inv = player.inventory

        val contents = inv.storageContents.map { it?.clone() }.toTypedArray()
        val armor = inv.armorContents.map { it?.clone() }.toTypedArray()
        val offhand = inv.itemInOffHand.clone()
        val uuid = player.uniqueId

        plugin.server.asyncScheduler.runNow(plugin) {
            plugin.deathStorage.saveDeathInventory(uuid, contents, armor, offhand)
        }
    }

    // GUI クリック: 未使用スロットの操作およびダブルクリック回収をキャンセル
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.inventory.holder !is InvViewHolder) return
        val slot = event.rawSlot
        if (slot in 0 until InvViewHolder.GUI_SIZE && InvViewHolder.GUI_IS_UNUSED[slot]) {
            event.isCancelled = true
            return
        }
        if (event.action == InventoryAction.COLLECT_TO_CURSOR) {
            event.isCancelled = true
            return
        }
    }

    // GUI ドラッグ: 未使用スロットへのドラッグをキャンセル
    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.inventory.holder !is InvViewHolder) return
        for (slot in event.rawSlots) {
            if (slot in 0 until InvViewHolder.GUI_SIZE && InvViewHolder.GUI_IS_UNUSED[slot]) {
                event.isCancelled = true
                return
            }
        }
    }

    // GUI クローズ時: 変更をターゲットプレイヤーまたは YAML に反映
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val gui = event.inventory
        when (val holder = gui.holder) {
            is InvViewHolder -> applyInvChanges(holder, gui)
            is EnderViewHolder -> applyEnderChanges(holder, gui)
        }
    }

    private fun applyInvChanges(holder: InvViewHolder, gui: Inventory) {
        val extracted = holder.extract()
        val targetUUID = holder.targetUUID
        val target = plugin.server.getPlayer(targetUUID)

        if (target != null && target.isOnline) {
            target.scheduler.run(plugin, {
                val inv = target.inventory
                inv.storageContents = extracted.contents
                inv.armorContents = extracted.armor
                inv.setItemInOffHand(extracted.offhand ?: ItemStack(Material.AIR))
                target.updateInventory()
            }, null)
        } else {
            pendingInvSync.add(targetUUID)
            val contents = extracted.contents.map { it?.clone() }.toTypedArray()
            val armor = extracted.armor.map { it?.clone() }.toTypedArray()
            val offhand = extracted.offhand?.clone()
            plugin.server.asyncScheduler.runNow(plugin) {
                plugin.playerDataStorage.saveInventory(
                    targetUUID,
                    contents,
                    armor,
                    offhand,
                    pendingSync = true
                )
            }
        }
    }

    private fun applyEnderChanges(holder: EnderViewHolder, gui: Inventory) {
        val contents = gui.contents
        val targetUUID = holder.targetUUID
        val target = plugin.server.getPlayer(targetUUID)

        if (target != null && target.isOnline) {
            target.scheduler.run(plugin, {
                target.enderChest.contents = contents
            }, null)
        } else {
            pendingEnderSync.add(targetUUID)
            val clonedContents = contents.map { it?.clone() }.toTypedArray()
            plugin.server.asyncScheduler.runNow(plugin) {
                plugin.playerDataStorage.saveEnderChest(targetUUID, clonedContents, pendingSync = true)
            }
        }
    }
}
