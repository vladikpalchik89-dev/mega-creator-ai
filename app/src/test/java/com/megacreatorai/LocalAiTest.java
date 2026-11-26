package com.megacreatorai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalAiTest {

    @Test
    public void cleanTrimsAndCollapsesSpaces() {
        assertEquals("кофе с молоком", LocalAi.clean("  кофе   с\tмолоком  "));
        assertEquals("", LocalAi.clean(null));
    }

    @Test
    public void ideasContainTopicAndAudience() {
        String result = LocalAi.ideas("кофе", "новички", 5);
        assertTrue(result.contains("кофе"));
        assertTrue(result.contains("новички"));
        assertEquals(5, countLinesStartingWith(result, "1.") + countLinesStartingWith(result, "2.")
                + countLinesStartingWith(result, "3.") + countLinesStartingWith(result, "4.")
                + countLinesStartingWith(result, "5."));
    }

    @Test
    public void scriptHasFourBlocks() {
        String result = LocalAi.script("запуск канала", 60);
        assertTrue(result.contains("Хук"));
        assertTrue(result.contains("Проблема"));
        assertTrue(result.contains("Решение"));
        assertTrue(result.contains("Финальный кадр"));
        assertTrue(result.contains("60 сек."));
    }

    @Test
    public void adUsesProductAndBenefit() {
        String result = LocalAi.ad("курс", "блогеры", "экономия времени", "дружелюбно");
        assertTrue(result.contains("курс"));
        assertTrue(result.contains("экономия времени"));
        assertTrue(result.contains("Привет"));
    }

    @Test
    public void rewriteStylesWork() {
        assertTrue(LocalAi.rewrite("Первое предложение. Второе предложение.", "Коротко")
                .startsWith("Коротко:"));
        assertTrue(LocalAi.rewrite("Текст", "TikTok").contains("Стоп"));
        assertTrue(LocalAi.rewrite("Текст", "Дружелюбно").contains("Привет"));
    }

    @Test
    public void hashtagsAreUniqueAndLimited() {
        String result = LocalAi.hashtags("кофе с молоком", 10);
        String[] tags = result.split(" ");
        assertEquals(10, tags.length);
        for (String tag : tags) {
            assertTrue(tag.startsWith("#"));
        }
        boolean seenCoffee = false;
        for (String tag : tags) {
            if (tag.equals("#кофе")) {
                assertFalse("Дубли хештегов недопустимы", seenCoffee);
                seenCoffee = true;
            }
        }
        assertTrue(seenCoffee);
    }

    @Test
    public void descriptionDependsOnPlatform() {
        assertTrue(LocalAi.description("тема", "Telegram").contains("канале"));
        assertTrue(LocalAi.description("тема", "YouTube").contains("Таймкоды"));
        assertTrue(LocalAi.description("тема", "Instagram").contains("✨"));
    }

    @Test
    public void promptsIncludeDescriptionAndFormat() {
        String image = LocalAi.imagePrompt("портрет в неоне");
        String video = LocalAi.videoPrompt("унбоксинг");
        assertTrue(image.contains("портрет в неоне"));
        assertTrue(image.contains("9:16"));
        assertTrue(video.contains("унбоксинг"));
        assertTrue(video.contains("9:16"));
    }

    @Test
    public void greetingAndPlanUseInput() {
        assertTrue(LocalAi.greeting("Аня", "днём рождения").contains("Аня"));
        String plan = LocalAi.plan("кулинария", 7);
        assertTrue(plan.contains("кулинария"));
        assertTrue(plan.contains("День 7"));
    }

    private int countLinesStartingWith(String text, String prefix) {
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }
}
