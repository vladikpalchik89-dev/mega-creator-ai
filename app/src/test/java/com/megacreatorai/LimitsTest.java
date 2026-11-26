package com.megacreatorai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LimitsTest {

    @Test
    public void allFeaturesAreFreeForEveryTariff() {
        Limits.Tariff[] tariffs = Limits.Tariff.values();
        Limits.Feature[] features = Limits.Feature.values();
        for (Limits.Tariff tariff : tariffs) {
            for (Limits.Feature feature : features) {
                assertTrue(feature.name(), Limits.isAvailable(tariff, feature));
                assertEquals(Limits.DAILY_LIMIT, Limits.dailyLimit(tariff, feature));
            }
        }
    }

    @Test
    public void photoAndVideoAreUnlockedOnFree() {
        assertTrue(Limits.isAvailable(Limits.Tariff.FREE, Limits.Feature.PHOTO));
        assertTrue(Limits.isAvailable(Limits.Tariff.FREE, Limits.Feature.VIDEO));
    }

    @Test
    public void pricesAreZero() {
        assertEquals("0 ₽ — бесплатно", Limits.price(Limits.Tariff.FREE));
        assertEquals("0 ₽ — бесплатно", Limits.price(Limits.Tariff.PRO));
        assertEquals("0 ₽ — бесплатно", Limits.price(Limits.Tariff.PREMIUM));
    }

    @Test
    public void tariffParsingIsSafe() {
        assertEquals(Limits.Tariff.PRO, Limits.fromName(" pro ", Limits.Tariff.FREE));
        assertEquals(Limits.Tariff.FREE, Limits.fromName("unknown", Limits.Tariff.FREE));
        assertEquals(Limits.Tariff.FREE, Limits.fromName(null, Limits.Tariff.FREE));
    }
}
