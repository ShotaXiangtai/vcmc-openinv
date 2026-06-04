package net.vcmc.openinv.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * エンダーチェスト閲覧用の GUI ホルダー（27スロット, 1:1 マッピング）。
 */
public class EnderViewHolder implements InventoryHolder {

    private final UUID targetUUID;
    private final String targetName;
    private final boolean offline;
    private Inventory inventory;

    public EnderViewHolder(UUID targetUUID, String targetName, boolean offline) {
        this.targetUUID = targetUUID;
        this.targetName = targetName;
        this.offline = offline;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public UUID getTargetUUID() { return targetUUID; }
    public String getTargetName() { return targetName; }
    public boolean isOffline() { return offline; }
}
