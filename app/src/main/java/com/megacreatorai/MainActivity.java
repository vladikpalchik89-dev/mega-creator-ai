package com.megacreatorai;

import android.graphics.Bitmap;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Главная активность: пять вкладок + подэкраны инструментов. */
public class MainActivity extends AppCompatActivity {

    private static final String[] STUDIO_TOOLS = {
            "Идеи", "Сценарий", "Реклама", "Переписать",
            "Хештеги", "Описание", "Поздравление", "План"
    };

    private static final String[][] TEMPLATES = {
            {"Story: новый старт", "Вертикальная история с заголовком и призывом", "✨"},
            {"Обложка видео", "Яркий заголовок для TikTok, Reels и Shorts", "🎬"},
            {"Рекламный баннер", "Оффер, преимущество и кнопка действия", "💰"},
            {"Поздравление", "Личная открытка с текстом и подписью", "🎉"},
            {"Мотивация", "Цитата и подпись для сторис", "🔥"},
            {"Пост для Telegram", "Заголовок, тезисы и вопрос аудитории", "📣"}
    };

    private ProjectStore store;
    private FrameLayout content;
    private BottomNavigationView nav;
    private TextView tariffBadge;
    private TextView avatar;

    private int currentTab = R.id.nav_home;
    private boolean subScreen = false;

    private String studioTool = "Идеи";
    private String studioTopic = "";
    private String studioExtra = "";
    private String studioBenefit = "";
    private String studioResult = "";
    private String projectFilter = "Все";

    private String authContact = "";
    private String authName = "";
    private String authCode = "";
    private Bitmap remoteBitmap;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final List<Voice> russianVoices = new ArrayList<>();
    private ArrayAdapter<String> voiceAdapter;
    private MediaPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new ProjectStore(this);
        AppCompatDelegate.setDefaultNightMode(store.isNight()
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        content = findViewById(R.id.content);
        nav = findViewById(R.id.bottomNav);
        tariffBadge = findViewById(R.id.tariffBadge);
        avatar = findViewById(R.id.avatar);

        nav.setOnItemSelectedListener(item -> {
            openTab(item.getItemId());
            return true;
        });
        avatar.setOnClickListener(v -> selectTab(R.id.nav_profile));
        tariffBadge.setOnClickListener(v -> selectTab(R.id.nav_profile));
        findViewById(R.id.bell).setOnClickListener(v -> Ui.info(this, "Уведомления",
                "Уведомления о генерациях и статусе проекта появятся после подключения сервера."));

        updateHeader();
        renderHome();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (subScreen) {
            back();
        } else if (currentTab != R.id.nav_home) {
            selectTab(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    // ---------- навигация и каркас экрана ----------

    private void selectTab(int id) {
        nav.setSelectedItemId(id);
    }

    private void openTab(int id) {
        currentTab = id;
        subScreen = false;
        updateHeader();
        if (id == R.id.nav_studio) {
            renderStudio();
        } else if (id == R.id.nav_templates) {
            renderTemplates();
        } else if (id == R.id.nav_projects) {
            renderProjects();
        } else if (id == R.id.nav_profile) {
            renderProfile();
        } else {
            renderHome();
        }
    }

    private void updateHeader() {
        tariffBadge.setText(store.getTariff().name());
        avatar.setText(Ui.initials(store.getName()));
    }

    private ScrollView scroller() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        return s;
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        col.setPadding(p, p, p, Ui.dp(this, 28));
        return col;
    }

    private void showTab(LinearLayout col) {
        ScrollView s = scroller();
        s.addView(col);
        content.removeAllViews();
        content.addView(s);
    }

    private void showSub(String title, LinearLayout body) {
        subScreen = true;
        LinearLayout outer = column();
        MaterialButton back = Ui.chipButton(this, "← Назад", v -> back());
        back.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        outer.addView(back);
        outer.addView(Ui.text(this, title, 20, R.color.text, true));
        outer.addView(Ui.spacer(this, 4));
        outer.addView(body);
        showTab(outer);
    }

    private void back() {
        openTab(currentTab);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private View toolCard(String icon, String title, String subtitle,
                          View.OnClickListener click, int startMargin, int endMargin) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        wp.setMargins(startMargin, 0, endMargin, Ui.dp(this, 10));
        wrap.setLayoutParams(wp);
        MaterialCardView cv = Ui.card(this, icon, title, subtitle, click);
        cv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrap.addView(cv);
        return wrap;
    }

    private MaterialCardView newCard() {
        MaterialCardView cv = new MaterialCardView(this);
        cv.setRadius(Ui.dp(this, 18));
        cv.setCardElevation(0);
        cv.setStrokeWidth(Ui.dp(this, 1));
        cv.setStrokeColor(ContextCompat.getColor(this, R.color.card_stroke));
        cv.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card));
        return cv;
    }

    private void limitsDialog(Limits.Feature feature) {
        int limit = Limits.dailyLimit(store.getTariff(), feature);
        Ui.dialog(this, "Лимит генераций",
                "На тарифе " + store.getTariff().name()
                        + " доступно " + limit + " генераций в день по этому инструменту.\n\n"
                        + "PRO — 299 ₽/мес, PREMIUM — 599 ₽/мес. "
                        + (loggedIn() ? "Оплата доступна в Профиле через сервер."
                        : "В Профиле можно войти в аккаунт или включить демо-режим."),
                "Открыть тарифы", () -> selectTab(R.id.nav_profile));
    }

    // ---------- главная ----------

    private void renderHome() {
        LinearLayout col = column();
        String name = store.getName();
        col.addView(Ui.text(this, name.isEmpty()
                ? "Добро пожаловать!" : "Привет, " + name + "!", 22, R.color.text, true));
        col.addView(Ui.muted(this, getString(R.string.tagline)));
        col.addView(Ui.button(this, "✨ Создать с ИИ", true,
                v -> selectTab(R.id.nav_studio)));

        col.addView(Ui.section(this, "Инструменты"));
        int half = Ui.dp(this, 6);
        LinearLayout r1 = row();
        r1.addView(toolCard("🖼", "AI-ФОТО", "Создание и улучшение", v -> showPhoto(), 0, half));
        r1.addView(toolCard("🎬", "AI-ВИДЕО", "Сценарии и ролики", v -> showVideo(), half, 0));
        col.addView(r1);

        LinearLayout r2 = row();
        r2.addView(toolCard("💰", "РЕКЛАМА AI", "Офферы и объявления", v -> {
            studioTool = "Реклама";
            studioResult = "";
            selectTab(R.id.nav_studio);
        }, 0, half));
        r2.addView(toolCard("🎙", "ОЗВУЧКА AI", "Голос на устройстве", v -> showVoice(), half, 0));
        col.addView(r2);

        LinearLayout r3 = row();
        r3.addView(toolCard("#", "ХЕШТЕГИ", "Подбор по теме", v -> {
            studioTool = "Хештеги";
            studioResult = "";
            selectTab(R.id.nav_studio);
        }, 0, half));
        r3.addView(toolCard("🧩", "ШАБЛОНЫ", "Сторис, обложки, баннеры",
                v -> selectTab(R.id.nav_templates), half, 0));
        col.addView(r3);

        LinearLayout r4 = row();
        r4.addView(toolCard("🤖", "AI-СТУДИЯ", "Тексты, идеи, сценарии",
                v -> selectTab(R.id.nav_studio), 0, half));
        r4.addView(toolCard("📁", "ПРОЕКТЫ", "Всё созданное вами",
                v -> selectTab(R.id.nav_projects), half, 0));
        col.addView(r4);

        col.addView(Ui.section(this, "Лимиты сегодня"));
        MaterialCardView limits = newCard();
        LinearLayout limitsBox = new LinearLayout(this);
        limitsBox.setOrientation(LinearLayout.VERTICAL);
        limitsBox.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        limitsBox.addView(Ui.text(this,
                "Тексты: " + store.remaining(Limits.Feature.IDEAS) + "   •   "
                        + "Реклама: " + store.remaining(Limits.Feature.AD) + "   •   "
                        + "Озвучка: " + store.remaining(Limits.Feature.VOICE),
                13, R.color.text, false));
        limitsBox.addView(Ui.muted(this, "Тариф " + store.getTariff().name()
                + " — параметры и цены в Профиле"));
        limits.addView(limitsBox);
        col.addView(limits);

        col.addView(Ui.section(this, "Последние проекты"));
        JSONArray projects = store.listProjects();
        if (projects.length() == 0) {
            col.addView(Ui.card(this, "📁", "Пока пусто",
                    "Создайте первый материал в AI-Студии",
                    v -> selectTab(R.id.nav_studio)));
        } else {
            for (int i = projects.length() - 1; i >= 0 && i >= projects.length() - 3; i--) {
                col.addView(projectRow(projects.optJSONObject(i), false));
            }
        }

        col.addView(Ui.spacer(this, 10));
        col.addView(Ui.card(this, "🚀", "Больше возможностей",
                "PRO и PREMIUM: больше генераций, фото и видео",
                v -> selectTab(R.id.nav_profile)));
        showTab(col);
    }

    // ---------- AI-Студия ----------

    private void renderStudio() {
        LinearLayout col = column();
        col.addView(Ui.text(this, "AI-Студия", 20, R.color.text, true));
        col.addView(Ui.muted(this, loggedIn()
                ? "Подключён сервер: генерация выполняется настоящей языковой моделью."
                : "Локальный режим: шаблонный генератор без нейросети. "
                        + "Для настоящего ИИ войдите в аккаунт на сервере в Профиле."));

        EditText topic = Ui.edit(this, studioTool.equals("Переписать")
                ? "Текст для переписывания" : "Тема, товар или идея", studioTopic, true);
        EditText extra = Ui.edit(this, "Аудитория / стиль / площадка", studioExtra, false);
        EditText benefit = Ui.edit(this, "Преимущество (для рекламы)", studioBenefit, false);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = row();
        for (String tool : STUDIO_TOOLS) {
            MaterialButton chip = Ui.chipButton(this, tool, v -> {
                studioTopic = topic.getText().toString();
                studioExtra = extra.getText().toString();
                studioBenefit = benefit.getText().toString();
                studioTool = tool;
                studioResult = "";
                renderStudio();
            });
            if (tool.equals(studioTool)) {
                chip.setBackgroundResource(R.drawable.bg_gradient);
                chip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, Ui.dp(this, 6), 0);
            chip.setLayoutParams(cp);
            chips.addView(chip);
        }
        hs.addView(chips);
        col.addView(hs);
        col.addView(Ui.spacer(this, 10));

        col.addView(topic);
        col.addView(Ui.spacer(this, 8));
        col.addView(extra);
        col.addView(Ui.spacer(this, 8));
        col.addView(benefit);
        col.addView(Ui.spacer(this, 12));

        col.addView(Ui.button(this, loggedIn() ? "Создать с ИИ" : "Создать (локально)", true, v -> {
            studioTopic = topic.getText().toString();
            studioExtra = extra.getText().toString();
            studioBenefit = benefit.getText().toString();
            Limits.Feature feature = featureForTool(studioTool);
            if (!store.canGenerate(feature)) {
                limitsDialog(feature);
                return;
            }
            if (loggedIn()) {
                generateOnServer(feature);
            } else {
                generateLocally(feature);
            }
        }));

        if (!studioResult.isEmpty()) {
            col.addView(Ui.spacer(this, 14));
            MaterialCardView card = newCard();
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(this, 14);
            box.setPadding(pad, pad, pad, pad);
            box.addView(Ui.text(this, studioTool + " — результат", 16, R.color.text, true));
            TextView output = Ui.text(this, studioResult, 14, R.color.text, false);
            output.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            box.addView(output);
            LinearLayout actions = row();
            MaterialButton copy = Ui.chipButton(this, "Копировать",
                    v -> Ui.copy(this, studioResult));
            MaterialButton save = Ui.chipButton(this, "В проект", v -> {
                store.addProject(studioTool + ": " + trim(studioTopic, 28), studioTool, studioResult);
                Ui.toast(this, "Сохранено в Проекты");
            });
            actions.addView(copy);
            actions.addView(save);
            box.addView(actions);
            card.addView(box);
            col.addView(card);
        }

        col.addView(Ui.spacer(this, 12));
        col.addView(Ui.muted(this, "Остаток по инструменту: "
                + store.remaining(featureForTool(studioTool)) + " генераций сегодня."));
        showTab(col);
    }

    private Limits.Feature featureForTool(String tool) {
        if ("Сценарий".equals(tool)) {
            return Limits.Feature.SCRIPT;
        }
        if ("Реклама".equals(tool)) {
            return Limits.Feature.AD;
        }
        if ("Переписать".equals(tool)) {
            return Limits.Feature.REWRITE;
        }
        if ("Хештеги".equals(tool)) {
            return Limits.Feature.HASHTAGS;
        }
        if ("Описание".equals(tool)) {
            return Limits.Feature.DESCRIPTION;
        }
        if ("Поздравление".equals(tool)) {
            return Limits.Feature.GREETING;
        }
        if ("План".equals(tool)) {
            return Limits.Feature.PLAN;
        }
        return Limits.Feature.IDEAS;
    }

    private String generate(String tool, String topic, String extra, String benefit) {
        if ("Сценарий".equals(tool)) {
            return LocalAi.script(topic, 60);
        }
        if ("Реклама".equals(tool)) {
            return LocalAi.ad(topic, extra, benefit, extra);
        }
        if ("Переписать".equals(tool)) {
            return LocalAi.rewrite(topic, extra.isEmpty() ? "Профессионально" : extra);
        }
        if ("Хештеги".equals(tool)) {
            return LocalAi.hashtags(topic, 12);
        }
        if ("Описание".equals(tool)) {
            return LocalAi.description(topic, extra.isEmpty() ? "TikTok" : extra);
        }
        if ("Поздравление".equals(tool)) {
            return LocalAi.greeting(topic, extra);
        }
        if ("План".equals(tool)) {
            return LocalAi.plan(topic, 7);
        }
        return LocalAi.ideas(topic, extra, 8);
    }

    private void generateLocally(Limits.Feature feature) {
        studioResult = generate(studioTool, studioTopic, studioExtra, studioBenefit);
        if (!store.consume(feature)) {
            limitsDialog(feature);
            return;
        }
        store.addHistory(studioTool, "Тексты");
        renderStudio();
        Ui.toast(this, "Готово (локальный шаблон). Осталось: " + store.remaining(feature));
    }

    private void generateOnServer(Limits.Feature feature) {
        Ui.toast(this, "Генерация на сервере…");
        String body = "{\"tool\":\"" + jsonEscape(ApiTools.serverTool(studioTool))
                + "\",\"prompt\":" + quote(studioTopic)
                + ",\"audience\":" + quote(studioExtra)
                + ",\"benefit\":" + quote(studioBenefit) + "}";
        apiPost("/api/generate", body, true, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                String result = parseTextResult(response);
                if (result.isEmpty()) {
                    Ui.toast(MainActivity.this, "Сервер вернул пустой результат");
                    return;
                }
                studioResult = result;
                applyServerRemaining(response, feature);
                store.addHistory(studioTool, "Тексты (ИИ)");
                renderStudio();
                Ui.toast(MainActivity.this, "Готово! Осталось сегодня: " + store.remaining(feature));
            }

            @Override
            public void onError(String message) {
                Ui.dialog(MainActivity.this, "Сервер недоступен", message
                        + "\n\nМожно использовать локальный шаблонный генератор.",
                        "Локально", () -> generateLocally(feature));
            }
        });
    }

    private String quote(String value) {
        return "\"" + jsonEscape(value) + "\"";
    }

    private String parseTextResult(String response) {
        try {
            return new JSONObject(response).optString("result", "").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void applyServerRemaining(String response, Limits.Feature feature) {
        try {
            JSONObject json = new JSONObject(response);
            if (json.has("remaining")) {
                store.syncRemaining(feature, json.optInt("remaining"));
            }
        } catch (Exception ignored) {
            // сервер не вернул счётчик — оставляем локальный
        }
    }

    private String trim(String value, int max) {
        String s = LocalAi.clean(value);
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ---------- шаблоны ----------

    private void renderTemplates() {
        LinearLayout col = column();
        col.addView(Ui.text(this, "Шаблоны", 20, R.color.text, true));
        col.addView(Ui.muted(this, "Текстовые заготовки для сторис, обложек и рекламы. "
                + "Редактор изображений появится на следующем этапе."));
        for (int i = 0; i < TEMPLATES.length; i++) {
            final int index = i;
            col.addView(Ui.card(this, TEMPLATES[i][2], TEMPLATES[i][0], TEMPLATES[i][1],
                    v -> renderTemplateEditor(index)));
        }
        showTab(col);
    }

    private void renderTemplateEditor(int index) {
        LinearLayout body = column();
        String[] t = TEMPLATES[index];
        EditText heading = Ui.edit(this, "Заголовок", t[0], false);
        EditText main = Ui.edit(this, "Основной текст", t[1], true);
        EditText cta = Ui.edit(this, "Призыв / подпись", "Сохрани и попробуй!", false);
        body.addView(heading);
        body.addView(Ui.spacer(this, 8));
        body.addView(main);
        body.addView(Ui.spacer(this, 8));
        body.addView(cta);
        body.addView(Ui.spacer(this, 10));
        body.addView(Ui.chipButton(this, "Предпросмотр", v ->
                showTemplatePreview(index, heading, main, cta, null)));
        body.addView(Ui.spacer(this, 10));
        body.addView(Ui.button(this, "Сохранить PNG и в проект", true, v -> {
            if (!store.canGenerate(Limits.Feature.TEMPLATES)) {
                limitsDialog(Limits.Feature.TEMPLATES);
                return;
            }
            try {
                Bitmap bitmap = templateBitmap(index, heading, main, cta);
                File file = TemplateRenderer.save(this, bitmap, "mega_template");
                store.consume(Limits.Feature.TEMPLATES);
                String content = heading.getText().toString().trim() + "\n\n"
                        + main.getText().toString().trim() + "\n\n"
                        + cta.getText().toString().trim()
                        + "\n\nPNG: " + file.getAbsolutePath();
                store.addProject(trim(heading.getText().toString(), 30), "Шаблоны", content);
                store.addHistory(t[0], "Шаблоны");
                showTemplatePreview(index, heading, main, cta, bitmap);
                Ui.toast(this, "PNG сохранён: " + file.getName());
            } catch (IOException e) {
                Ui.toast(this, "Не удалось сохранить PNG");
            }
        }));
        showSub(t[0], body);
    }

    private Bitmap templateBitmap(int index, EditText heading, EditText main, EditText cta) {
        return TemplateRenderer.render(index,
                heading.getText().toString(),
                main.getText().toString(),
                cta.getText().toString());
    }

    /** Добавляет превью под кнопками, не создавая дубликаты при повторных нажатиях. */
    private void showTemplatePreview(int index, EditText heading, EditText main,
                                     EditText cta, Bitmap prepared) {
        LinearLayout body = (LinearLayout) ((ScrollView) content.getChildAt(0)).getChildAt(0);
        ImageView existing = body.findViewWithTag("template_preview");
        if (existing == null) {
            existing = new ImageView(this);
            existing.setTag("template_preview");
            existing.setAdjustViewBounds(true);
            existing.setMaxHeight(Ui.dp(this, 260));
            existing.setScaleType(ImageView.ScaleType.FIT_CENTER);
            existing.setBackgroundResource(R.drawable.bg_input);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 10);
            existing.setLayoutParams(lp);
            body.addView(existing);
        }
        existing.setImageBitmap(prepared != null ? prepared : templateBitmap(index, heading, main, cta));
    }

    // ---------- проекты ----------

    private void renderProjects() {
        LinearLayout col = column();
        col.addView(Ui.text(this, "Мои проекты", 20, R.color.text, true));
        col.addView(Ui.muted(this, "Тексты, реклама, шаблоны и озвучки в одном месте."));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = row();
        String[] filters = {"Все", "Тексты", "Реклама", "Шаблоны", "Озвучка", "Избранное"};
        for (String filter : filters) {
            MaterialButton chip = Ui.chipButton(this, filter, v -> {
                projectFilter = filter;
                renderProjects();
            });
            if (filter.equals(projectFilter)) {
                chip.setBackgroundResource(R.drawable.bg_gradient);
                chip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            }
            chips.addView(chip);
        }
        hs.addView(chips);
        col.addView(hs);
        col.addView(Ui.spacer(this, 10));

        JSONArray items = store.listProjects();
        int shown = 0;
        for (int i = items.length() - 1; i >= 0; i--) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !matchesFilter(item, projectFilter)) {
                continue;
            }
            col.addView(projectRow(item, true));
            shown++;
        }
        if (shown == 0) {
            col.addView(Ui.card(this, "📁", "Проектов нет",
                    "Создайте материал в AI-Студии или выберите другой фильтр",
                    v -> selectTab(R.id.nav_studio)));
        }
        showTab(col);
    }

    private boolean matchesFilter(JSONObject item, String filter) {
        if ("Все".equals(filter)) {
            return true;
        }
        if ("Избранное".equals(filter)) {
            return item.optBoolean("favorite");
        }
        return filter.equals(item.optString("category"));
    }

    private MaterialCardView projectRow(JSONObject item, boolean withActions) {
        String id = item.optString("id");
        String title = item.optString("title");
        String category = item.optString("category");
        String date = ProjectStore.formatDate(item.optLong("created"));
        MaterialCardView cv = newCard();
        cv.setClickable(true);
        cv.setOnClickListener(v -> showProject(item));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 14);
        box.setPadding(pad, pad, pad, pad);
        box.addView(Ui.text(this, (item.optBoolean("favorite") ? "★ " : "") + title,
                15, R.color.text, true));
        box.addView(Ui.muted(this, category + " · " + date));
        if (withActions) {
            LinearLayout actions = row();
            MaterialButton fav = Ui.chipButton(this,
                    item.optBoolean("favorite") ? "★ В избранном" : "☆ В избранное", v -> {
                        store.toggleFavorite(id);
                        renderProjects();
                    });
            MaterialButton del = Ui.chipButton(this, "Удалить", v ->
                    Ui.dialog(this, "Удалить проект?", "Действие нельзя отменить.", "Удалить", () -> {
                        store.deleteProject(id);
                        renderProjects();
                    }));
            actions.addView(fav);
            actions.addView(del);
            box.addView(actions);
        }
        cv.addView(box);
        return cv;
    }

    private void showProject(JSONObject item) {
        LinearLayout body = column();
        body.addView(Ui.muted(this, item.optString("category") + " · "
                + ProjectStore.formatDate(item.optLong("created"))));
        body.addView(Ui.text(this, item.optString("content"), 14, R.color.text, false));
        body.addView(Ui.spacer(this, 12));
        LinearLayout actions = row();
        actions.addView(Ui.chipButton(this, "Копировать",
                v -> Ui.copy(this, item.optString("content"))));
        if (loggedIn()) {
            actions.addView(Ui.chipButton(this, "На сервер", v -> syncProject(item)));
            if (!item.optString("serverId").isEmpty()) {
                actions.addView(Ui.chipButton(this, "Удалить с сервера",
                        v -> deleteRemoteProject(item)));
            }
        }
        actions.addView(Ui.chipButton(this, "Удалить", v ->
                Ui.dialog(this, "Удалить проект?", "Действие нельзя отменить.", "Удалить", () -> {
                    store.deleteProject(item.optString("id"));
                    back();
                })));
        body.addView(actions);
        showSub(item.optString("title"), body);
    }

    private void syncProject(JSONObject item) {
        String body = "{\"title\":" + quote(item.optString("title"))
                + ",\"category\":" + quote(item.optString("category"))
                + ",\"content\":" + quote(item.optString("content"))
                + ",\"fileUrl\":" + quote(item.optString("fileUrl"))
                + ",\"favorite\":" + item.optBoolean("favorite") + "}";
        apiPost("/api/projects", body, true, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    JSONObject created = json.optJSONObject("project");
                    String serverId = created == null ? json.optString("id", "")
                            : created.optString("id", "");
                    if (!serverId.isEmpty()) {
                        item.put("serverId", serverId);
                        store.updateProject(item.optString("id"), item);
                    }
                    Ui.toast(MainActivity.this, "Проект синхронизирован с сервером");
                } catch (Exception e) {
                    Ui.toast(MainActivity.this, "Не удалось прочитать ответ сервера");
                }
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Синхронизация недоступна", message);
            }
        });
    }

    private void deleteRemoteProject(JSONObject item) {
        String serverId = item.optString("serverId");
        if (serverId.isEmpty()) {
            return;
        }
        apiDelete("/api/projects/" + serverId, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    item.remove("serverId");
                    store.updateProject(item.optString("id"), item);
                } catch (Exception ignored) {
                    // локальная копия останется прежней
                }
                Ui.toast(MainActivity.this, "Удалено с сервера");
                back();
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Не удалось удалить", message);
            }
        });
    }

    // ---------- профиль ----------

    private void renderProfile() {
        LinearLayout col = column();
        col.addView(Ui.text(this, "Личный кабинет", 20, R.color.text, true));
        col.addView(Ui.muted(this, loggedIn()
                ? "Вы вошли в серверный аккаунт: тариф и лимиты проверяются сервером."
                : "Локальный режим: данные хранятся только на этом устройстве."));

        col.addView(Ui.section(this, "Вход по коду"));
        col.addView(Ui.muted(this, "Укажите телефон или email — сервер пришлёт код. "
                + "В демо-режиме сервер может показать код прямо в приложении."));
        EditText contact = Ui.edit(this, "Телефон или email", authContact, false);
        EditText userName = Ui.edit(this, "Имя (необязательно)", authName, false);
        EditText code = Ui.edit(this, "Код из SMS/email", authCode, false);
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        col.addView(contact);
        col.addView(Ui.spacer(this, 8));
        col.addView(userName);
        col.addView(Ui.spacer(this, 8));
        col.addView(code);
        col.addView(Ui.spacer(this, 8));
        LinearLayout authButtons = row();
        authButtons.addView(Ui.chipButton(this, "Получить код", v -> {
            authContact = contact.getText().toString().trim();
            authName = userName.getText().toString().trim();
            if (authContact.isEmpty()) {
                Ui.toast(this, "Введите телефон или email");
                return;
            }
            requestCode(authContact, authName);
        }));
        authButtons.addView(Ui.chipButton(this, "Войти", v -> {
            authContact = contact.getText().toString().trim();
            authCode = code.getText().toString().trim();
            if (authContact.isEmpty() || authCode.isEmpty()) {
                Ui.toast(this, "Введите контакт и код");
                return;
            }
            verifyCode(authContact, authCode);
        }));
        col.addView(authButtons);
        if (loggedIn()) {
            col.addView(Ui.spacer(this, 8));
            LinearLayout sessionButtons = row();
            sessionButtons.addView(Ui.chipButton(this, "Обновить тариф", v -> refreshMe()));
            sessionButtons.addView(Ui.chipButton(this, "Выйти", v -> {
                store.setToken("");
                store.setTariff(Limits.Tariff.FREE);
                updateHeader();
                renderProfile();
                Ui.toast(this, "Вы вышли из серверного аккаунта");
            }));
            col.addView(sessionButtons);
        }

        col.addView(Ui.section(this, "Профиль на устройстве"));
        EditText name = Ui.edit(this, "Имя", store.getName(), false);
        EditText email = Ui.edit(this, "Email", store.getEmail(), false);
        EditText phone = Ui.edit(this, "Телефон", store.getPhone(), false);
        col.addView(name);
        col.addView(Ui.spacer(this, 8));
        col.addView(email);
        col.addView(Ui.spacer(this, 8));
        col.addView(phone);
        col.addView(Ui.spacer(this, 10));
        col.addView(Ui.button(this, "Сохранить профиль", false, v -> {
            String n = name.getText().toString().trim();
            String e = email.getText().toString().trim();
            String p = phone.getText().toString().trim();
            if (!e.isEmpty() && !e.contains("@")) {
                Ui.toast(this, "Проверьте email");
                return;
            }
            if (!p.isEmpty() && p.replaceAll("\\D", "").length() < 10) {
                Ui.toast(this, "Проверьте номер телефона");
                return;
            }
            store.saveProfile(n, e, p);
            updateHeader();
            Ui.toast(this, "Профиль сохранён");
        }));

        col.addView(Ui.section(this, "Тариф"));
        Limits.Tariff[] tariffs = Limits.Tariff.values();
        for (Limits.Tariff tariff : tariffs) {
            MaterialCardView card = newCard();
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = Ui.dp(this, 14);
            box.setPadding(pad, pad, pad, pad);
            box.addView(Ui.text(this, tariff.name() + " · " + Limits.price(tariff),
                    15, R.color.primary, true));
            box.addView(Ui.muted(this, Limits.summary(tariff)));
            Limits.Tariff current = store.getTariff();
            String action = tariff == current ? "Текущий тариф" : "Выбрать";
            box.addView(Ui.button(this, action, tariff == Limits.Tariff.PREMIUM,
                    v -> chooseTariff(tariff)));
            card.addView(box);
            col.addView(card);
            col.addView(Ui.spacer(this, 8));
        }
        col.addView(Ui.muted(this, loggedIn()
                ? "Оплата через сервер: ссылку на оплату откроет ЮKassa. "
                        + "Тариф обновится после подтверждения платежа."
                : store.isDemoUnlocked()
                        ? "Демо-режим включён: платные тарифы доступны без оплаты."
                        : "Для реальной оплаты войдите в серверный аккаунт выше."));

        col.addView(Ui.section(this, "Лимиты сегодня"));
        MaterialCardView limits = newCard();
        LinearLayout lb = new LinearLayout(this);
        lb.setOrientation(LinearLayout.VERTICAL);
        lb.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        Limits.Feature[] features = {
                Limits.Feature.IDEAS, Limits.Feature.SCRIPT, Limits.Feature.AD,
                Limits.Feature.REWRITE, Limits.Feature.HASHTAGS, Limits.Feature.TEMPLATES,
                Limits.Feature.VOICE, Limits.Feature.PHOTO, Limits.Feature.VIDEO
        };
        for (Limits.Feature f : features) {
            lb.addView(Ui.text(this, featureName(f) + ": "
                    + store.used(f) + " / " + Limits.dailyLimit(store.getTariff(), f),
                    13, R.color.text, false));
        }
        limits.addView(lb);
        col.addView(limits);

        col.addView(Ui.section(this, "Сервер"));
        EditText server = Ui.edit(this, "https://api.example.com", store.getServerUrl(), false);
        col.addView(server);
        col.addView(Ui.spacer(this, 8));
        LinearLayout serverActions = row();
        serverActions.addView(Ui.chipButton(this, "Сохранить адрес", v -> {
            store.setServerUrl(server.getText().toString());
            Ui.toast(this, "Адрес сервера сохранён");
        }));
        serverActions.addView(Ui.chipButton(this, "Проверить связь", v -> checkServer()));
        col.addView(serverActions);
        col.addView(Ui.muted(this, new ServerGateway(store.getServerUrl()).isConfigured()
                ? "Адрес задан. Секреты хранятся на сервере, не в APK."
                : "Сервер не подключён: работают только локальные инструменты."));

        col.addView(Ui.section(this, "Оформление"));
        LinearLayout themeBox = new LinearLayout(this);
        themeBox.setOrientation(LinearLayout.HORIZONTAL);
        themeBox.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView themeLabel = Ui.text(this, "Тёмная тема", 14, R.color.text, false);
        themeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch themeSwitch = new Switch(this);
        themeSwitch.setChecked(store.isNight());
        themeSwitch.setOnCheckedChangeListener((button, checked) -> {
            store.setNight(checked);
            AppCompatDelegate.setDefaultNightMode(checked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
        });
        themeBox.addView(themeLabel);
        themeBox.addView(themeSwitch);
        col.addView(themeBox);

        col.addView(Ui.section(this, "Помощь"));
        LinearLayout help = row();
        help.addView(Ui.chipButton(this, "Как это работает", v -> Ui.info(this, "MEGA CREATOR AI",
                "Тексты, хештеги, реклама, шаблоны и озвучка работают локально. "
                        + "AI-фото, AI-видео и реальные модели требуют сервера и внешних API.")));
        help.addView(Ui.chipButton(this, "Конфиденциальность", v -> Ui.info(this, "Данные",
                "Профиль и проекты хранятся на устройстве. Секреты не встраиваются в APK. "
                        + "Удалить данные можно кнопкой ниже.")));
        col.addView(help);
        col.addView(Ui.spacer(this, 10));
        col.addView(Ui.button(this, "Удалить мои данные", false, v ->
                Ui.dialog(this, "Удалить данные?", "Профиль, проекты и лимиты будут очищены.",
                        "Удалить", () -> {
                            store.clearAll();
                            updateHeader();
                            selectTab(R.id.nav_home);
                        })));

        showTab(col);
    }

    private void chooseTariff(Limits.Tariff tariff) {
        if (tariff == store.getTariff()) {
            Ui.toast(this, "Этот тариф уже выбран");
            return;
        }
        if (tariff == Limits.Tariff.FREE) {
            store.setTariff(tariff);
            updateHeader();
            renderProfile();
            Ui.toast(this, "Выбран FREE");
            return;
        }
        if (loggedIn()) {
            buyTariff(tariff.name());
            return;
        }
        if (!store.isDemoUnlocked()) {
            Ui.dialog(this, "Оплата не подключена",
                    "Это демонстрация тарифа " + tariff.name() + " без реального платежа. "
                            + "После подключения платёжного провайдера оплату проверяет сервер.",
                    "Включить демо", () -> {
                        store.setDemoUnlocked(true);
                        store.setTariff(tariff);
                        updateHeader();
                        renderProfile();
                    });
        } else {
            store.setTariff(tariff);
            updateHeader();
            renderProfile();
            Ui.toast(this, "Выбран " + tariff.name() + " (демо)");
        }
    }

    private void checkServer() {
        String url = store.getServerUrl();
        ServerGateway gateway = new ServerGateway(url);
        if (!gateway.isConfigured()) {
            Ui.toast(this, "Сначала укажите адрес https://…");
            return;
        }
        Ui.toast(this, "Проверяем соединение…");
        new Thread(() -> {
            String message;
            try {
                String response = gateway.health(5000);
                message = "Сервер ответил: " + trim(response, 160);
            } catch (IOException e) {
                message = "Нет соединения: " + e.getMessage();
            }
            final String result = message;
            runOnUiThread(() -> Ui.info(this, "Статус сервера", result));
        }, "server-health").start();
    }

    // ---------- вызовы серверного API ----------

    private ServerGateway gateway() {
        return new ServerGateway(store.getServerUrl(), store.getToken());
    }

    private boolean serverReady() {
        return new ServerGateway(store.getServerUrl()).isConfigured();
    }

    private boolean loggedIn() {
        return serverReady() && !store.getToken().isEmpty();
    }

    private void apiPost(String path, String body, boolean auth, ApiCallback callback) {
        if (!serverReady()) {
            callback.onError("Сервер не подключён");
            return;
        }
        final ServerGateway gw = auth ? gateway() : new ServerGateway(store.getServerUrl());
        new Thread(() -> {
            try {
                final String response = gw.postJson(path, body);
                runOnUiThread(() -> callback.onSuccess(response));
            } catch (Exception e) {
                final String message = e.getMessage() == null ? "Ошибка сети" : e.getMessage();
                runOnUiThread(() -> callback.onError(message));
            }
        }, "api-post").start();
    }

    private void apiGet(String path, boolean auth, ApiCallback callback) {
        if (!serverReady()) {
            callback.onError("Сервер не подключён");
            return;
        }
        final ServerGateway gw = auth ? gateway() : new ServerGateway(store.getServerUrl());
        new Thread(() -> {
            try {
                final String response = gw.getJson(path);
                runOnUiThread(() -> callback.onSuccess(response));
            } catch (Exception e) {
                final String message = e.getMessage() == null ? "Ошибка сети" : e.getMessage();
                runOnUiThread(() -> callback.onError(message));
            }
        }, "api-get").start();
    }

    private void apiDelete(String path, ApiCallback callback) {
        if (!serverReady()) {
            callback.onError("Сервер не подключён");
            return;
        }
        final ServerGateway gw = gateway();
        new Thread(() -> {
            try {
                final String response = gw.deleteJson(path);
                runOnUiThread(() -> callback.onSuccess(response));
            } catch (Exception e) {
                final String message = e.getMessage() == null ? "Ошибка сети" : e.getMessage();
                runOnUiThread(() -> callback.onError(message));
            }
        }, "api-delete").start();
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void requestCode(String contact, String name) {
        String field = ApiTools.contactField(contact);
        String body = "{\"" + field + "\":" + quote(contact)
                + ",\"name\":" + quote(name) + "}";
        apiPost("/api/auth/register", body, false, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                String devCode = "";
                try {
                    devCode = new JSONObject(response).optString("devCode", "");
                } catch (Exception ignored) {
                    // сервер отправил код на почту/телефон
                }
                if (!devCode.isEmpty()) {
                    authCode = devCode;
                    renderProfile();
                    Ui.info(MainActivity.this, "Код подтверждения",
                            "Демо-режим сервера: код " + devCode
                                    + ". Он уже подставлен в поле ввода.");
                } else {
                    Ui.toast(MainActivity.this, "Код отправлен. Введите его в поле выше");
                }
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Не удалось получить код", message);
            }
        });
    }

    private void verifyCode(String contact, String code) {
        String field = ApiTools.contactField(contact);
        String body = "{\"" + field + "\":" + quote(contact)
                + ",\"code\":" + quote(code) + "}";
        apiPost("/api/auth/verify", body, false, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    String token = json.optString("token", "");
                    if (token.isEmpty()) {
                        Ui.toast(MainActivity.this, "Сервер не вернул токен");
                        return;
                    }
                    store.setToken(token);
                    JSONObject user = json.optJSONObject("user");
                    if (user != null) {
                        String name = user.optString("name", "");
                        String email = user.optString("email", "");
                        String phone = user.optString("phone", "");
                        if (!name.isEmpty() || !email.isEmpty() || !phone.isEmpty()) {
                            store.saveProfile(name, email, phone);
                        }
                        store.applyServerTariff(user.optString("tariff", "FREE"));
                    }
                    authCode = "";
                    updateHeader();
                    renderProfile();
                    Ui.toast(MainActivity.this, "Вы вошли. Тариф синхронизирован");
                } catch (Exception e) {
                    Ui.toast(MainActivity.this, "Не удалось прочитать ответ сервера");
                }
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Вход не удался", message);
            }
        });
    }

    private void refreshMe() {
        if (!loggedIn()) {
            Ui.toast(this, "Сначала войдите в серверный аккаунт");
            return;
        }
        apiGet("/api/me", true, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject user = new JSONObject(response).optJSONObject("user");
                    if (user != null) {
                        String tariff = user.optString("tariff", "FREE");
                        store.applyServerTariff(tariff);
                        updateHeader();
                        renderProfile();
                        Ui.toast(MainActivity.this, "Тариф синхронизирован: " + tariff);
                    }
                } catch (Exception e) {
                    Ui.toast(MainActivity.this, "Не удалось прочитать ответ сервера");
                }
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Ошибка", message);
            }
        });
    }

    private void buyTariff(String tariff) {
        if (!loggedIn()) {
            Ui.toast(this, "Войдите в серверный аккаунт перед оплатой");
            return;
        }
        apiPost("/api/payments/create", "{\"tariff\":\"" + tariff + "\"}", true, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                try {
                    JSONObject json = new JSONObject(response);
                    String url = json.optString("confirmationUrl", "");
                    if (url.isEmpty()) {
                        Ui.toast(MainActivity.this, "Сервер не вернул ссылку на оплату");
                        return;
                    }
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    Ui.info(MainActivity.this, "Оплата",
                            "После оплаты сервер подтвердит платёж. Нажмите «Обновить тариф».");
                } catch (Exception e) {
                    Ui.toast(MainActivity.this, "Не удалось открыть оплату");
                }
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Оплата недоступна", message);
            }
        });
    }

    private String featureName(Limits.Feature feature) {
        switch (feature) {
            case IDEAS: return "Идеи";
            case SCRIPT: return "Сценарии";
            case AD: return "Реклама";
            case REWRITE: return "Переписывание";
            case HASHTAGS: return "Хештеги";
            case DESCRIPTION: return "Описания";
            case GREETING: return "Поздравления";
            case PLAN: return "Контент-планы";
            case TEMPLATES: return "Шаблоны";
            case VOICE: return "Озвучка";
            case PHOTO: return "AI-фото";
            case VIDEO: return "AI-видео";
            default: return feature.name();
        }
    }

    // ---------- фото и видео (требуется сервер) ----------

    private void showPhoto() {
        showRemoteScreen("AI-ФОТО", Limits.Feature.PHOTO,
                "Генерация изображений по описанию, портреты, аватарки, замена фона. "
                        + "Нужны внешний API и интернет.");
    }

    private void showVideo() {
        showRemoteScreen("AI-ВИДЕО", Limits.Feature.VIDEO,
                "Сценарии, ролики из фото и текста, субтитры и экспорт для TikTok, Reels и Shorts. "
                        + "Нужен внешний видеосервис.");
    }

    private void showRemoteScreen(String title, Limits.Feature feature, String description) {
        LinearLayout body = column();
        body.addView(Ui.text(this, description, 14, R.color.text, false));
        ServerGateway gateway = new ServerGateway(store.getServerUrl());
        MaterialCardView status = newCard();
        LinearLayout sb = new LinearLayout(this);
        sb.setOrientation(LinearLayout.VERTICAL);
        sb.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        sb.addView(Ui.text(this, gateway.isConfigured()
                ? "Сервер: адрес указан" : "Сервер: не подключён", 14, R.color.text, true));
        sb.addView(Ui.text(this, loggedIn()
                ? "Аккаунт: вы вошли" : "Аккаунт: нужен вход в Профиле",
                13, R.color.text_muted, false));
        sb.addView(Ui.text(this, Limits.isAvailable(store.getTariff(), feature)
                ? "Доступ: тариф " + store.getTariff().name()
                : "Доступ: нужен PREMIUM", 13, R.color.text_muted, false));
        sb.addView(Ui.muted(this, "Остаток сегодня: "
                + store.remaining(feature) + " генераций"));
        status.addView(sb);
        body.addView(status);
        body.addView(Ui.spacer(this, 12));
        body.addView(Ui.muted(this, "Опишите задачу — подготовим готовый запрос для внешнего сервиса."));
        EditText promptInput = Ui.edit(this, "Например: аватарка для Telegram", "", true);
        body.addView(promptInput);
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.button(this, "Подготовить запрос и скопировать", false, v -> {
            String value = promptInput.getText().toString().trim();
            if (value.isEmpty()) {
                Ui.toast(this, "Опишите, что хотите получить");
                return;
            }
            String prompt = feature == Limits.Feature.PHOTO
                    ? LocalAi.imagePrompt(value) : LocalAi.videoPrompt(value);
            store.addProject("Запрос: " + trim(value, 26), title, prompt);
            Ui.copy(this, prompt);
            Ui.toast(this, "Запрос скопирован и сохранён в Проекты");
        }));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.button(this, loggedIn()
                ? "Сгенерировать на сервере" : "Войти для генерации", true, v -> {
            String value = promptInput.getText().toString().trim();
            if (value.isEmpty()) {
                Ui.toast(this, "Опишите, что хотите получить");
                return;
            }
            if (!loggedIn()) {
                Ui.dialog(this, "Нужен вход", "Войдите в серверный аккаунт в Профиле.",
                        "В профиль", () -> selectTab(R.id.nav_profile));
                return;
            }
            if (!Limits.isAvailable(store.getTariff(), feature)) {
                limitsDialog(feature);
                return;
            }
            if (!store.canGenerate(feature)) {
                limitsDialog(feature);
                return;
            }
            String prompt = feature == Limits.Feature.PHOTO
                    ? LocalAi.imagePrompt(value) : LocalAi.videoPrompt(value);
            generateRemote(feature, prompt);
        }));

        ImageView preview = new ImageView(this);
        preview.setTag("remote_preview");
        preview.setVisibility(View.GONE);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight(Ui.dp(this, 320));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundResource(R.drawable.bg_input);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        body.addView(preview);

        MaterialButton save = Ui.chipButton(this, "Сохранить PNG", v -> {
            if (remoteBitmap == null) {
                Ui.toast(this, "Сначала сгенерируйте изображение");
                return;
            }
            try {
                File file = TemplateRenderer.save(this, remoteBitmap, "mega_ai");
                store.addProject("AI-фото", "AI-ФОТО", "Файл: " + file.getAbsolutePath());
                Ui.toast(this, "PNG сохранён: " + file.getName());
            } catch (IOException e) {
                Ui.toast(this, "Не удалось сохранить PNG");
            }
        });
        save.setTag("remote_save");
        save.setVisibility(View.GONE);
        body.addView(save);

        TextView link = Ui.text(this, "", 13, R.color.text, false);
        link.setTag("remote_link");
        link.setVisibility(View.GONE);
        body.addView(link);
        MaterialButton open = Ui.chipButton(this, "Открыть видео", v -> {
            String url = link.getText().toString();
            if (!url.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        open.setTag("remote_open");
        open.setVisibility(View.GONE);
        body.addView(open);

        body.addView(Ui.spacer(this, 10));
        body.addView(Ui.button(this, "Открыть тарифы", false,
                v -> selectTab(R.id.nav_profile)));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.button(this, "Настроить сервер", false,
                v -> selectTab(R.id.nav_profile)));
        body.addView(Ui.spacer(this, 10));
        body.addView(Ui.muted(this, "Генерация выполняется внешним сервисом через сервер. "
                + "Стоимость API и условия коммерческого использования нужно проверять "
                + "до включения генерации."));
        showSub(title, body);
    }

    private View findTagged(String tag) {
        return content.findViewWithTag(tag);
    }

    private void generateRemote(Limits.Feature feature, String prompt) {
        Ui.toast(this, "Запрос отправлен на сервер…");
        String path = feature == Limits.Feature.PHOTO
                ? "/api/images/generate" : "/api/videos/generate";
        apiPost(path, "{\"prompt\":" + quote(prompt) + "}", true, new ApiCallback() {
            @Override
            public void onSuccess(String response) {
                String result = parseTextResult(response);
                applyServerRemaining(response, feature);
                store.addHistory(feature == Limits.Feature.PHOTO ? "AI-фото" : "AI-видео",
                        feature == Limits.Feature.PHOTO ? "AI-ФОТО" : "AI-ВИДЕО");
                if (feature == Limits.Feature.PHOTO) {
                    showRemoteImage(result);
                } else {
                    showRemoteVideo(result);
                }
            }

            @Override
            public void onError(String message) {
                Ui.info(MainActivity.this, "Генерация недоступна", message);
            }
        });
    }

    private void showRemoteImage(String result) {
        new Thread(() -> {
            try {
                final Bitmap bitmap = MediaLoader.load(result);
                runOnUiThread(() -> {
                    remoteBitmap = bitmap;
                    ImageView preview = (ImageView) findTagged("remote_preview");
                    if (preview != null) {
                        preview.setImageBitmap(bitmap);
                        preview.setVisibility(View.VISIBLE);
                    }
                    View save = findTagged("remote_save");
                    if (save != null) {
                        save.setVisibility(View.VISIBLE);
                    }
                    Ui.toast(MainActivity.this, "Изображение готово");
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Ошибка загрузки" : e.getMessage();
                runOnUiThread(() -> Ui.info(MainActivity.this, "Ошибка", message));
            }
        }, "load-image").start();
    }

    private void showRemoteVideo(String result) {
        TextView link = (TextView) findTagged("remote_link");
        if (link != null) {
            link.setText("Ссылка на видео: " + MediaLoader.shortResult(result));
            link.setVisibility(View.VISIBLE);
        }
        View open = findTagged("remote_open");
        if (open != null) {
            open.setVisibility(View.VISIBLE);
        }
        store.addProject("AI-видео", "AI-ВИДЕО", result);
        Ui.toast(this, "Видео готово");
    }

    // ---------- озвучка через системный TTS ----------

    private void showVoice() {
        initTts();
        LinearLayout body = column();
        body.addView(Ui.muted(this, "Озвучка работает через системный движок TTS на устройстве. "
                + "Доступ к голосам зависит от установленного движка."));
        EditText text = Ui.edit(this, "Введите текст для озвучки", "", true);
        body.addView(text);
        body.addView(Ui.spacer(this, 10));

        voiceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new ArrayList<>(Arrays.asList("По умолчанию (русский)")));
        voiceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner voices = new Spinner(this);
        voices.setAdapter(voiceAdapter);
        body.addView(voices);
        body.addView(Ui.spacer(this, 10));

        body.addView(Ui.button(this, "Озвучить", true, v -> {
            String value = text.getText().toString().trim();
            if (value.isEmpty()) {
                Ui.toast(this, "Введите текст");
                return;
            }
            if (!store.canGenerate(Limits.Feature.VOICE)) {
                limitsDialog(Limits.Feature.VOICE);
                return;
            }
            if (!ttsReady || tts == null) {
                Ui.toast(this, "TTS-движок недоступен");
                return;
            }
            applySelectedVoice(voices);
            tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "voice_" + System.currentTimeMillis());
            store.consume(Limits.Feature.VOICE);
            store.addHistory("Озвучка", "Озвучка");
        }));
        body.addView(Ui.spacer(this, 8));
        body.addView(Ui.button(this, "Сохранить WAV", false, v -> {
            String value = text.getText().toString().trim();
            if (value.isEmpty()) {
                Ui.toast(this, "Введите текст");
                return;
            }
            if (!store.canGenerate(Limits.Feature.VOICE)) {
                limitsDialog(Limits.Feature.VOICE);
                return;
            }
            if (!ttsReady || tts == null) {
                Ui.toast(this, "TTS-движок недоступен");
                return;
            }
            applySelectedVoice(voices);
            File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (dir == null) {
                Ui.toast(this, "Не удалось получить папку приложения");
                return;
            }
            File file = new File(dir, "voice_" + System.currentTimeMillis() + ".wav");
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "save");
            int result = tts.synthesizeToFile(value, params, file, "save_" + System.currentTimeMillis());
            if (result == TextToSpeech.SUCCESS) {
                store.consume(Limits.Feature.VOICE);
                store.addHistory("Озвучка (файл)", "Озвучка");
                Ui.toast(this, "Файл сохранён: " + file.getName());
            } else {
                Ui.toast(this, "Не удалось сохранить аудио");
            }
        }));

        body.addView(Ui.section(this, "Сохранённые записи"));
        File dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File[] files = dir == null ? null : dir.listFiles((d, name) -> name.endsWith(".wav"));
        if (files == null || files.length == 0) {
            body.addView(Ui.muted(this, "Здесь появятся сохранённые озвучки."));
        } else {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < Math.min(10, files.length); i++) {
                final File file = files[i];
                body.addView(Ui.chipButton(this, "▶ " + file.getName(), v -> playFile(file)));
            }
        }
        body.addView(Ui.spacer(this, 10));
        body.addView(Ui.muted(this, "Остаток озвучек сегодня: "
                + store.remaining(Limits.Feature.VOICE)));
        showSub("Озвучка AI", body);
    }

    private void initTts() {
        if (tts != null) {
            return;
        }
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) {
                return;
            }
            int result = tts.setLanguage(new Locale("ru"));
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = false;
                return;
            }
            Set<Voice> voices = tts.getVoices();
            russianVoices.clear();
            List<String> names = new ArrayList<>();
            names.add("По умолчанию (русский)");
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice.getLocale() != null
                            && "ru".equals(voice.getLocale().getLanguage())) {
                        russianVoices.add(voice);
                        names.add(voice.getName());
                    }
                }
            }
            if (voiceAdapter != null) {
                voiceAdapter.clear();
                voiceAdapter.addAll(names);
                voiceAdapter.notifyDataSetChanged();
            }
        });
    }

    private void applySelectedVoice(Spinner spinner) {
        if (tts == null || russianVoices.isEmpty()) {
            return;
        }
        int position = spinner.getSelectedItemPosition();
        if (position > 0 && position <= russianVoices.size()) {
            tts.setVoice(russianVoices.get(position - 1));
        }
    }

    private void playFile(File file) {
        if (player != null) {
            player.release();
        }
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.start();
        } catch (IOException e) {
            Ui.toast(this, "Не удалось воспроизвести файл");
        }
    }
}
