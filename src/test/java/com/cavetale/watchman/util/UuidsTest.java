package com.cavetale.watchman.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.Test;

public final class UuidsTest {
    @Test
    public void test() {
        for (int i = 0; i < 3; i += 1) {
            test(UUID.randomUUID());
        }
    }

    private void test(UUID uuid) {
        byte[] bytes = UUIDS.uuidToBytes(uuid);
        UUID uuid2 = UUIDS.uuidFromBytes(bytes);
        byte[] bytes2 = UUIDS.uuidToBytes(uuid2);
        System.out.println("a= " + uuid);
        System.out.println("b= " + uuid2);
        System.out.println("a=" + ls(bytes));
        System.out.println("b=" + ls(bytes2));
        assert uuid.equals(uuid2);
        assert Objects.deepEquals(bytes, bytes2);
    }

    private static List<Integer> ls(byte[] bytes) {
        List<Integer> list = new ArrayList<>();
        for (byte b : bytes) {
            list.add((int) b);
        }
        return list;
    }
}
