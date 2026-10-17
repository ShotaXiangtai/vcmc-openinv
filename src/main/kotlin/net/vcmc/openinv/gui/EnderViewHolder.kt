package net.vcmc.openinv.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

/**
 * エンダーチェスト操作用 GUI ホルダー (27スロット, 1:1 マッピング)
 */
class EnderViewHolder(
    val targetUUID: UUID,
    val targetName: String,
    val isOffline: Boolean
) : InventoryHolder {

    private var inv: Inventory? = null

    override fun getInventory(): Inventory {
        return inv ?: error("Inventory not initialized")
    }

    fun setInventory(inventory: Inventory) {
        this.inv = inventory
    }
}
