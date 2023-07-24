package com.cavetale.watchman.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;

public final class Entities {
    public static Entity findSourceEntity(Entity in) {
        if (in == null) {
            return null;
        } else if (in instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        } else {
            return in;
        }
    }

    private Entities() { }
}
