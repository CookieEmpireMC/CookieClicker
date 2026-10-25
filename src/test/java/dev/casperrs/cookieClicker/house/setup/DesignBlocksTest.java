package dev.casperrs.cookieClicker.house.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.junit.jupiter.api.Test;

/** {@link DesignBlocks} parses through {@link Bukkit#createBlockData}: a stand-in server answers for these ids. */
class DesignBlocksTest {

    static {
        try {
            Bukkit.setServer(fakeServer());
        } catch (UnsupportedOperationException alreadySet) {
            // an earlier test class set one; this stand-in only parses the ids below
        }
    }

    private static Server fakeServer() {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("createBlockData") && args != null && args[0] instanceof String s) {
                return blockData(s);
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (Server) Proxy.newProxyInstance(DesignBlocksTest.class.getClassLoader(),
                new Class<?>[] {Server.class}, handler);
    }

    /** A Leaves for {@code *_leaves} ids (persistent as the text says, false when absent), else a plain BlockData. */
    private static BlockData blockData(String text) {
        if (text.contains("[") != text.contains("]")) {
            throw new IllegalArgumentException("unbalanced block data " + text);
        }
        String id = text.contains("[") ? text.substring(0, text.indexOf('[')) : text;
        boolean leaves = id.endsWith("_leaves");
        boolean[] persistent = {text.contains("persistent=true")};
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getAsString" -> leaves ? id + "[persistent=" + persistent[0] + "]" : text;
            case "isPersistent" -> persistent[0];
            case "setPersistent" -> {
                persistent[0] = (Boolean) args[0];
                yield null;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
        };
        return (BlockData) Proxy.newProxyInstance(DesignBlocksTest.class.getClassLoader(),
                new Class<?>[] {leaves ? Leaves.class : BlockData.class}, handler);
    }

    @Test
    void leavesAreForcedPersistentWhateverTheDesignSays() {
        Leaves forced = (Leaves) DesignBlocks.create("minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]");
        assertTrue(forced.isPersistent());
        assertTrue(DesignBlocks.create("minecraft:spruce_leaves").getAsString().contains("persistent=true"));
    }

    @Test
    void otherBlocksKeepTheirDataAndBadDataIsRejected() {
        BlockData stone = DesignBlocks.create("minecraft:stone");
        assertEquals("minecraft:stone", stone.getAsString());
        assertFalse(stone instanceof Leaves);
        assertThrows(IllegalArgumentException.class, () -> DesignBlocks.create("minecraft:stone["));
    }
}
