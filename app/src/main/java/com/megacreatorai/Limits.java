package com.megacreatorai;

/**
 * Правила тарифов и лимитов. Чистый Java-класс: используется и приложением,
 * и юнит-тестами.
 *
 * Режим «Всё бесплатно»: все инструменты открыты на любом тарифе, лимиты
 * практически неограничены, оплата и платежные шлюзы не нужны.
 */
public final class Limits {

    /** Условно-бесконечный дневной лимит: 100 000 генераций в день. */
    public static final int DAILY_LIMIT = 100000;

    private Limits() {
    }

    public enum Tariff {
        FREE, PRO, PREMIUM
    }

    public enum Feature {
        IDEAS, SCRIPT, AD, REWRITE, HASHTAGS, DESCRIPTION, GREETING, PLAN,
        TEMPLATES, VOICE, PHOTO, VIDEO
    }

    /** Все инструменты доступны на всех тарифах. */
    public static int dailyLimit(Tariff tariff, Feature feature) {
        if (tariff == null || feature == null) {
            return 0;
        }
        return DAILY_LIMIT;
    }

    public static boolean isAvailable(Tariff tariff, Feature feature) {
        return tariff != null && feature != null;
    }

    public static String price(Tariff tariff) {
        return "0 ₽ — бесплатно";
    }

    public static String summary(Tariff tariff) {
        return "Все инструменты ИИ: тексты, фото, видео, озвучка и шаблоны. "
                + "Без оплаты и подписки.";
    }

    public static Tariff fromName(String name, Tariff fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Tariff.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
