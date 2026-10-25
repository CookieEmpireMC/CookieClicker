package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.farm.Farm;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Plugin chunk tickets: every ticket added is removed again, and a new hold replaces the old one. */
class ChunkHoldTest {

    private static World world(List<String> log) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addPluginChunkTicket" -> {
                        log.add("add");
                        yield true;
                    }
                    case "removePluginChunkTicket" -> {
                        log.add("remove");
                        yield true;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static long count(List<String> log, String what) {
        return log.stream().filter(what::equals).count();
    }

    @Test
    void everyTicketIsRemovedOnceAndANewHoldReplacesTheOldOne() {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class},
                (proxy, method, args) -> method.getName().equals("hashCode") ? 1 : null);
        List<String> log = new ArrayList<>();
        World world = world(log);
        ChunkHold hold = new ChunkHold(plugin);
        Farm.Box box = new Farm.Box(-1, 60, 15, 16, 80, 16); // chunks x -1..1 and z 0..1: six tickets
        hold.hold(world, box);
        assertEquals(6, count(log, "add"));
        hold.hold(world, box); // releases the first six, then holds six again
        assertEquals(6, count(log, "remove"));
        assertEquals(12, count(log, "add"));
        hold.release();
        hold.release(); // nothing left to release
        assertEquals(12, count(log, "remove"));
    }
}
