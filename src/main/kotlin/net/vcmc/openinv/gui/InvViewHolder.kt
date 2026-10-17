package net.vcmc.openinv.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * プレイヤーインベントリ操作用 GUI ホルダー
 *
 * GUI レイアウト (45スロット = 5行 x 9列):
 *   GUI  0-26   プレイヤーインベントリ contents[9-35] (メインインベントリ 3行)
 *   GUI 27-35   プレイヤーインベントリ contents[0-8]  (ホットバー 1行)
 *   GUI 36      armor[3] ヘルメット
 *   GUI 37      armor[2] チェストプレート
 *   GUI 38      armor[1] レギンス
 *   GUI 39      armor[0] ブーツ
 *   GUI 40      オフハンド
 *   GUI 41-44   未使用 (フィラー)
 */
class InvViewHolder(
    val targetUUID: UUID,
    val targetName: String,
    val isOffline: Boolean
) : InventoryHolder {

    companion object {
        const val GUI_SIZE = 45

        val GUI_TO_CONTENTS = IntArray(GUI_SIZE) { -1 }
        val GUI_TO_ARMOR = IntArray(GUI_SIZE) { -1 }
        val GUI_IS_OFFHAND = BooleanArray(GUI_SIZE)
        val GUI_IS_UNUSED = BooleanArray(GUI_SIZE)

        val FILLER: ItemStack = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            editMeta { it.displayName(Component.empty()) }
        }

        init {
            for (i in 0 until 27) {
                GUI_TO_CONTENTS[i] = i + 9
            }
            for (i in 0 until 9) {
                GUI_TO_CONTENTS[27 + i] = i
            }
            GUI_TO_ARMOR[36] = 3 // ヘルメット
            GUI_TO_ARMOR[37] = 2 // チェストプレート
            GUI_TO_ARMOR[38] = 1 // レギンス
            GUI_TO_ARMOR[39] = 0 // ブーツ
            GUI_IS_OFFHAND[40] = true
            for (i in 41..44) {
                GUI_IS_UNUSED[i] = true
            }
        }
    }

    private var inv: Inventory? = null

    override fun getInventory(): Inventory {
        return inv ?: error("Inventory not initialized")
    }

    fun setInventory(inventory: Inventory) {
        this.inv = inventory
    }

    fun populate(contents: Array<ItemStack?>, armor: Array<ItemStack?>?, offhand: ItemStack?) {
        val inventory = this.inv ?: return
        for (gui in 0 until GUI_SIZE) {
            val contSlot = GUI_TO_CONTENTS[gui]
            val armorSlot = GUI_TO_ARMOR[gui]

            when {
                contSlot in contents.indices -> {
                    inventory.setItem(gui, contents[contSlot])
                }
                armor != null && armorSlot in armor.indices -> {
                    inventory.setItem(gui, armor[armorSlot])
                }
                GUI_IS_OFFHAND[gui] -> {
                    inventory.setItem(gui, offhand)
                }
                GUI_IS_UNUSED[gui] -> {
                    inventory.setItem(gui, FILLER)
                }
            }
        }
    }

    fun extract(): ExtractedInventory {
        val contents = arrayOfNulls<ItemStack>(36)
        val armor = arrayOfNulls<ItemStack>(4)
        var offhand: ItemStack? = null
        val inventory = this.inv ?: return ExtractedInventory(contents, armor, offhand)

        for (gui in 0 until GUI_SIZE) {
            val item = inventory.getItem(gui)
            val contSlot = GUI_TO_CONTENTS[gui]
            val armorSlot = GUI_TO_ARMOR[gui]

            if (contSlot >= 0 && contSlot < contents.size) {
                contents[contSlot] = item
            } else if (armorSlot >= 0 && armorSlot < armor.size) {
                armor[armorSlot] = item
            } else if (GUI_IS_OFFHAND[gui]) {
                offhand = item
            }
        }

        return ExtractedInventory(contents, armor, offhand)
    }

    class ExtractedInventory(
        val contents: Array<ItemStack?>,
        val armor: Array<ItemStack?>,
        val offhand: ItemStack?
    )
}
