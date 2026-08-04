package com.guildcore.gui.holders;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TradeGUIHolder implements InventoryHolder {
    private final UUID player1Uuid;
    private final UUID player2Uuid;
    private boolean p1Ready = false;
    private boolean p2Ready = false;
    private int countdownSeconds = -1;
    private Object countdownTask = null;
    private boolean closed = false;
    private boolean completed = false;

    public TradeGUIHolder(UUID player1Uuid, UUID player2Uuid) {
        this.player1Uuid = player1Uuid;
        this.player2Uuid = player2Uuid;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public UUID getPlayer1Uuid() {
        return player1Uuid;
    }

    public UUID getPlayer2Uuid() {
        return player2Uuid;
    }

    public boolean isP1Ready() {
        return p1Ready;
    }

    public void setP1Ready(boolean p1Ready) {
        this.p1Ready = p1Ready;
    }

    public boolean isP2Ready() {
        return p2Ready;
    }

    public void setP2Ready(boolean p2Ready) {
        this.p2Ready = p2Ready;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int countdownSeconds) {
        this.countdownSeconds = countdownSeconds;
    }

    public Object getCountdownTask() {
        return countdownTask;
    }

    public void setCountdownTask(Object countdownTask) {
        this.countdownTask = countdownTask;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
