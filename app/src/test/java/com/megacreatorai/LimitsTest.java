package com.megacreatorai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LimitsTest {

    @Test
    public void freeTariffBlocksPhotoAndVideo() {
        assertEquals(0, Limits.dailyLimit(Limits.Tariff.FREE, Limits.Feature.PHOTO));
        assertEquals(0, Limits.dailyLimit(Limits.Tariff.PRO, Limits.Feature.VIDEO));
        assertFalse(Limits.isAvailable(Limits.Tariff.FREE, Limits.Feature.PHOTO));
    }

    @Test
    public void premiumUnlocksImageGeneration() {
        assertTrue(Limits.dailyLimit(Limits.Tariff.PREMIUM, Limits.Feature.PHOTO) > 0);
        assertTrue(Limits.dailyLimit(Limits.Tariff.PREMIUM, Limits.Feature.VIDEO) > 0);
        assertTrue(Limits.isAvailable(Limits.Tariff.PREMIUM, Limits.Feature.PHOTO));
    }

    @Test
    public void limitsGrowWithTariff() {
        int free = Limits.dailyLimit(Limits.Tariff.FREE, Limits.Feature.IDEAS);
        int pro = Limits.dailyLimit(Limits.Tariff.PRO, Limits.Feature.IDEAS);
        int premium = Limits.dailyLimit(Limits.Tariff.PREMIUM, Limits.Feature.IDEAS);
        assertTrue(free < pro);
        assertTrue(pro < premium);
    }

    @Test
    public void pricesMatchConcept() {
        assertEquals("0 ₽", Limits.price(Limits.Tariff.FREE));
        assertEquals("299 ₽ / месяц", Limits.price(Limits.Tariff.PRO));
        assertEquals("599 ₽ / месяц", Limits.price(Limits.Tariff.PREMIUM));
    }

    @Test
    public void tariffParsingIsSafe() {
        assertEquals(Limits.Tariff.PRO, Limits.fromName(" pro ", Limits.Tariff.FREE));
        assertEquals(Limits.Tariff.FREE, Limits.fromName("unknown", Limits.Tariff.FREE));
        assertEquals(Limits.Tariff.FREE, Limits.fromName(null, Limits.Tariff.FREE));
    }
}
