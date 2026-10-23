package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.plot.PedestalPrivacy.Pair;
import dev.casperrs.cookieClicker.plot.PedestalPrivacy.Pos;
import dev.casperrs.cookieClicker.station.Station;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PedestalPrivacyPairsTest {

    private static final String W = "minecraft:overworld";
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final Station PED = new Station("main", W, 0, 64, 0, 0);

    private static Pos at(UUID id, double x, double z) {
        return new Pos(id, W, x, 64, z);
    }

    @Test
    void twoPlayersNearTheSamePedestalHideEachOtherBothWays() {
        Set<Pair> pairs = PedestalPrivacy.pairs(List.of(at(A, 1, 1), at(B, -2, 0)), List.of(PED), 6);
        assertEquals(Set.of(new Pair(A, B), new Pair(B, A)), pairs);
    }

    @Test
    void aLonePlayerHidesNobodyAndNobodyIsNeverAPlayerWithThemselves() {
        assertTrue(PedestalPrivacy.pairs(List.of(at(A, 0, 0)), List.of(PED), 6).isEmpty());
        assertTrue(PedestalPrivacy.pairs(List.of(), List.of(PED), 6).isEmpty());
        assertTrue(PedestalPrivacy.pairs(List.of(at(A, 0, 0), at(B, 0, 0)), List.of(), 6).isEmpty());
    }

    @Test
    void differentWorldsNeverPair() {
        Pos nether = new Pos(B, "minecraft:the_nether", 0, 64, 0);
        assertTrue(PedestalPrivacy.pairs(List.of(at(A, 0, 0), nether), List.of(PED), 6).isEmpty());
    }

    @Test
    void separatePedestalsKeepTheirCrowdsApartButSharedPlayersLink() {
        Station other = new Station("two", W, 100, 64, 0, 0);
        Set<Pair> pairs = PedestalPrivacy.pairs(
                List.of(at(A, 0, 0), at(B, 1, 0), at(C, 100, 0)), List.of(PED, other), 6);
        assertEquals(Set.of(new Pair(A, B), new Pair(B, A)), pairs);
    }

}
