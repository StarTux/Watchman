package com.cavetale.watchman;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class EntityHider {
    public void hide(Player player, Entity entity) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntLists().write(0, Arrays.asList(entity.getEntityId()));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Cannot send ENTITY_DESTROY packet", e);
        }
    }

    public void show(Player player, Entity entity) {
        ProtocolLibrary.getProtocolManager().updateEntity(entity, Arrays.asList(player));
    }
}
