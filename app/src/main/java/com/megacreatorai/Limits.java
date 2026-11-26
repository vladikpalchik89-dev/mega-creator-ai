package com.megacreatorai;

/**
 * Правила тарифов и лимитов. Чистый Java-класс: используется и приложением,
 * и юнит-тестами. Цены — предварительная гипотеза, не оферта.
 */
public final class Limits {

    private Limits() {
    }

    public enum Tariff {
        FREE, PRO, PREMIUM
    }

    public enum Feature {
        IDEAS, SCRIPT, AD, REWRITE, HASHTAGS, DESCRIPTION, GREETING, PLAN,
        TEMPLATES, VOICE, PHOTO, VIDEO
    }

    /** Сколько генераций в день доступно на тарифе. 0 — инструмент недоступен. */
    public static int dailyLimit(Tariff tariff, Feature feature) {
        if (tariff == null || feature == null) {
            return 0;
        }
        switch (feature) {
            case PHOTO:
                return tariff == Tariff.PREMIUM ? 20 : 0;
            case VIDEO:
                return tariff == Tariff.PREMIUM ? 8 : 0;
            case VOICE:
                return tariff == Tariff.FREE ? 2 : (tariff == Tariff.PRO ? 30 : 100);
            case TEMPLATES:
                return tariff == Tariff.FREE ? 3 : (tariff == Tariff.PRO ? 30 : 100);
            case SCRIPT:
                return tariff == Tariff.FREE ? 2 : (tariff == Tariff.PRO ? 25 : 80);
            case PLAN:
                return tariff == Tariff.FREE ? 1 : (tariff == Tariff.PRO ? 30 : 100);
            case AD:
            case REWRITE:
                return tariff == Tariff.FREE ? 3 : (tariff == Tariff.PRO ? 40 : 120);
            case GREETING:
                return tariff == Tariff.FREE ? 5 : (tariff == Tariff.PRO ? 50 : 150);
            case IDEAS:
            case HASHTAGS:
            case DESCRIPTION:
            default:
                return tariff == Tariff.FREE ? 5 : (tariff == Tariff.PRO ? 50 : 150);
        }
    }

    public static boolean isAvailable(Tariff tariff, Feature feature) {
        return dailyLimit(tariff, feature) > 0;
    }

    public static String price(Tariff tariff) {
        if (tariff == Tariff.PRO) {
            return "299 ₽ / месяц";
        }
        if (tariff == Tariff.PREMIUM) {
            return "599 ₽ / месяц";
        }
        return "0 ₽";
    }

    public static String summary(Tariff tariff) {
        if (tariff == Tariff.PRO) {
            return "Больше генераций, расширенная озвучка, шаблоны и история проектов";
        }
        if (tariff == Tariff.PREMIUM) {
            return "Максимальные лимиты, AI-фото и AI-видео через внешние сервисы";
        }
        return "Тексты, хештеги, реклама, пробные генерации и базовые шаблоны";
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
