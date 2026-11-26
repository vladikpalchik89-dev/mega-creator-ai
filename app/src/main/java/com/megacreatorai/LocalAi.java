package com.megacreatorai;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Локальный генератор контента на шаблонах. Работает без интернета и сервера.
 * Это НЕ большая языковая модель: честно помечаем результат как шаблонную
 * заготовку. Реальный ИИ подключается позже через сервер.
 */
public final class LocalAi {

    private LocalAi() {
    }

    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public static String topic(String value) {
        String s = clean(value);
        return s.isEmpty() ? "ваша тема" : s;
    }

    public static String audience(String value) {
        String s = clean(value);
        return s.isEmpty() ? "ваша аудитория" : s;
    }

    /** Идеи для постов и коротких видео. */
    public static String ideas(String rawTopic, String rawAudience, int count) {
        String t = topic(rawTopic);
        String a = audience(rawAudience);
        String[] patterns = {
                "«%s» за 60 секунд: 3 совета для %s",
                "5 заблуждений о «%s», которые стоит развеять",
                "Как «%s» изменит привычный день: короткий разбор",
                "До / после: «%s» глазами новичка",
                "Вопрос аудитории: что вы думаете про «%s»?",
                "Мини-кейс: как я попробовал «%s» и что получилось",
                "Тренд недели: «%s» в формате сторис",
                "Чек-лист: 4 шага, чтобы начать с «%s»",
                "Ошибка, которую почти все допускают в «%s»",
                "«%s» без сложных слов: объяснение за 30 секунд"
        };
        int n = Math.max(1, Math.min(count, patterns.length));
        StringBuilder out = new StringBuilder("Идеи для контента на тему «" + t + "»\n\n");
        for (int i = 0; i < n; i++) {
            out.append(i + 1).append(". ")
                    .append(String.format(Locale.getDefault(), patterns[i], t, a))
                    .append('\n');
        }
        out.append("\nПодсказка: возьмите одну идею и снимите её вертикально 9:16.");
        return out.toString();
    }

    /** Сценарий короткого ролика с разбивкой по времени. */
    public static String script(String rawTopic, int seconds) {
        String t = topic(rawTopic);
        int total = Math.max(15, Math.min(seconds, 180));
        int hook = Math.max(3, Math.round(total * 0.15f));
        int problem = Math.max(3, Math.round(total * 0.25f));
        int solution = Math.max(5, Math.round(total * 0.40f));
        int cta = Math.max(2, total - hook - problem - solution);
        return "Сценарий ролика «" + t + "» (" + total + " сек.)\n\n"
                + "0–" + hook + " сек. Хук: «Стоп! Сейчас покажу, как работает " + t + "» — крупный план, короткая фраза.\n"
                + hook + "–" + (hook + problem) + " сек. Проблема: покажите, что мешает аудитории, одним предложением.\n"
                + (hook + problem) + "–" + (hook + problem + solution) + " сек. Решение: 3 шага, каждый — отдельный кадр и надпись.\n"
                + (hook + problem + solution) + "–" + total + " сек. Финальный кадр: итог и призыв «Сохрани, чтобы не потерять».\n\n"
                + "Совет: первые 2 секунды решают — начинайте сразу с действия.";
    }

    /** Рекламный текст под площадку и аудиторию. */
    public static String ad(String rawProduct, String rawAudience, String rawBenefit, String tone) {
        String p = topic(rawProduct);
        String a = audience(rawAudience);
        String b = rawBenefit == null || clean(rawBenefit).isEmpty() ? "экономия времени" : clean(rawBenefit);
        String toneText = tone == null ? "" : clean(tone).toLowerCase();
        String opener;
        if (toneText.contains("друж")) {
            opener = "Привет! Знаешь " + a + "?";
        } else if (toneText.contains("строг") || toneText.contains("профессион")) {
            opener = "Для " + a + ".";
        } else {
            opener = "Внимание, " + a + "!";
        }
        return opener + "\n\n"
                + "Знакомо:/" + p + " отнимает время и силы?\n\n"
                + "«" + p + "» — " + b + ". Попробуйте сегодня и оцените разницу.\n\n"
                + "👉 Узнать подробнее — напишите в директ.\n\n"
                + "Заголовок для объявления: " + p + " — " + b + ".";
    }

    /** Переписывание в заданном стиле (локальные шаблоны, не нейросеть). */
    public static String rewrite(String rawText, String style) {
        String text = clean(rawText);
        if (text.isEmpty()) {
            return "Введите текст, который нужно переписать.";
        }
        String s = style == null ? "" : style;
        if (s.contains("Коротко")) {
            String[] parts = text.split("(?<=[.!?])\\s+");
            StringBuilder out = new StringBuilder("Коротко: ");
            for (int i = 0; i < Math.min(2, parts.length); i++) {
                if (i > 0) {
                    out.append(' ');
                }
                out.append(parts[i]);
            }
            return out.toString();
        }
        if (s.contains("Дружелюбно")) {
            return "Привет! Давай по-простому:\n\n" + text + "\n\nЕсли откликнулось — ставь лайк 🙂";
        }
        if (s.contains("Вдохновляюще")) {
            return "Каждый шаг имеет значение.\n\n" + text + "\n\nНачни с маленького действия сегодня.";
        }
        if (s.contains("TikTok")) {
            return "Стоп, смотри 👇\n\n" + text + "\n\nСохрани, чтобы вернуться к этому позже.";
        }
        return "Кратко о главном:\n\n" + text + "\n\nВывод: используйте это как основу для своей публикации.";
    }

    /** Подбор хештегов: тема + базовые теги, без повторов. */
    public static String hashtags(String rawTopic, int count) {
        Set<String> tags = new LinkedHashSet<>();
        String[] base = {"контент", "идеи", "блогер", "создательконтента", "ai", "мегакреатор"};
        for (String tag : base) {
            tags.add(tag);
        }
        String cleaned = clean(rawTopic).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zа-яё0-9\\s]", " ");
        for (String word : cleaned.split("\\s+")) {
            if (word.length() >= 3 && !isStopWord(word)) {
                tags.add(word);
            }
        }
        String[] extra = {"тренды", "вдохновение", "творчество", "соцсети", "полезно"};
        for (String tag : extra) {
            if (tags.size() >= count) {
                break;
            }
            tags.add(tag);
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        for (String tag : tags) {
            if (i >= count) {
                break;
            }
            if (i > 0) {
                out.append(' ');
            }
            out.append('#').append(tag);
            i++;
        }
        return out.toString();
    }

    private static boolean isStopWord(String word) {
        return word.equals("для") || word.equals("как") || word.equals("про")
                || word.equals("это") || word.equals("что") || word.equals("идея")
                || word.equals("тема") || word.equals("ваша");
    }

    /** Описание публикации под выбранную площадку. */
    public static String description(String rawTopic, String platform) {
        String t = topic(rawTopic);
        String p = platform == null ? "" : platform;
        if (p.contains("Telegram")) {
            return "«" + t + "» — короткий разбор в канале.\n\n"
                    + "Что внутри: 3 тезиса, один пример и вопрос для обсуждения.\n\n"
                    + "Пишите в комментариях, как это работает у вас.";
        }
        if (p.contains("YouTube")) {
            return t + " — за 60 секунд без воды.\n\n"
                    + "Таймкоды:\n0:00 суть\n0:15 пример\n0:35 итог\n\n"
                    + "Подписывайтесь, если полезно.";
        }
        if (p.contains("Instagram")) {
            return t + " ✨\n\nСохраните пост, чтобы вернуться к нему позже.\n\n"
                    + "Какая часть откликнулась сильнее? Напишите в комментариях.";
        }
        return t + " 👇\n\n3 вещи, которые стоит запомнить:\n1. Начните с малого.\n2. Делайте каждый день.\n3. Смотрите на результат, не на идеал.\n\n"
                + "Сохраните и возвращайтесь.";
    }

    /** Поздравление. */
    public static String greeting(String rawName, String occasion) {
        String n = rawName == null || clean(rawName).isEmpty() ? "друг" : clean(rawName);
        String o = occasion == null || clean(occasion).isEmpty() ? "праздник" : clean(occasion);
        return "«" + n + "», с " + o + "!\n\n"
                + "Пусть каждый день приносит новые идеи, а всё задуманное превращается в реальность.\n\n"
                + "Твоя студия творчества — MEGA CREATOR AI ✨";
    }

    /** Готовый запрос для внешнего сервиса генерации изображений. */
    public static String imagePrompt(String description) {
        return topic(description)
                + ". Современный визуальный стиль, чистый фон, мягкий студийный свет, "
                + "насыщенные фиолетовые и неоново-синие акценты, вертикальный формат 9:16, "
                + "высокое качество, без текста и логотипов.";
    }

    /** Готовый запрос для внешнего видеосервиса. */
    public static String videoPrompt(String description) {
        return topic(description)
                + ". Вертикальное короткое видео 9:16, первые 2 секунды — динамичный хук, "
                + "плавная камера, фиолетово-синяя цветокоррекция, субтитры по тексту, "
                + "финальный кадр с призывом сохранить публикацию.";
    }

    /** Контент-план на N дней. */
    public static String plan(String rawNiche, int days) {
        String n = topic(rawNiche);
        String[] themes = {"Идея", "Обзор", "Совет", "Вовлечение", "Кейс", "Вопрос", "Итоги недели"};
        int count = Math.max(1, Math.min(days, 30));
        StringBuilder out = new StringBuilder("Контент-план: " + n + "\n\n");
        for (int i = 0; i < count; i++) {
            out.append("День ").append(i + 1).append(": ")
                    .append(themes[i % themes.length])
                    .append(" — «").append(n).append("»: короткий пост, 1 пример и 3 хештега.\n");
        }
        return out.toString();
    }
}
