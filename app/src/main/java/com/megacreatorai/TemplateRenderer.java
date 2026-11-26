package com.megacreatorai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Рендер шаблонов на устройстве: фон рисуется Canvas, текст накладывается поверх.
 * Никаких внешних изображений и серверов не требуется.
 */
public final class TemplateRenderer {

    private TemplateRenderer() {
    }

    /** Стиль соответствует индексу шаблона в MainActivity.TEMPLATES. */
    public static Bitmap render(int style, String heading, String body, String cta) {
        int w;
        int h;
        if (style == 1) {
            w = 1280;
            h = 720;
        } else if (style == 2) {
            w = 1200;
            h = 628;
        } else if (style == 3) {
            w = 1080;
            h = 1080;
        } else if (style == 4) {
            w = 1080;
            h = 1350;
        } else if (style == 5) {
            w = 1200;
            h = 800;
        } else {
            w = 1080;
            h = 1920;
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawBackground(canvas, style, w, h);

        String title = heading == null || heading.trim().isEmpty() ? "MEGA CREATOR AI" : heading.trim();
        String text = body == null || body.trim().isEmpty() ? "Ваш текст" : body.trim();
        String action = cta == null || cta.trim().isEmpty() ? "Сохрани и попробуй!" : cta.trim();

        float panelX = w * 0.08f;
        float panelW = w * 0.84f;
        int innerW = (int) panelW - (int) (w * 0.05f);

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(w / 18f);
        StaticLayout titleLayout = new StaticLayout(title, titlePaint, innerW,
                Layout.Alignment.ALIGN_NORMAL, 1.05f, 0f, false);

        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(0xE6FFFFFF);
        bodyPaint.setTextSize(w / 34f);
        StaticLayout bodyLayout = new StaticLayout(text, bodyPaint, innerW,
                Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false);

        TextPaint ctaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        ctaPaint.setColor(0xFF22D3EE);
        ctaPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        ctaPaint.setTextSize(w / 30f);
        StaticLayout ctaLayout = new StaticLayout(action, ctaPaint, innerW,
                Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false);

        int gap = (int) (w * 0.025f);
        int textHeight = titleLayout.getHeight() + gap + bodyLayout.getHeight()
                + gap + ctaLayout.getHeight();
        float panelY;
        if (style == 2) {
            panelY = h * 0.18f;
        } else if (style == 0 || style == 4) {
            panelY = h * 0.52f;
        } else {
            panelY = (h - textHeight) / 2f;
        }
        panelY = Math.max((float) (h * 0.06f), Math.min(panelY, h - textHeight - (float) (h * 0.06f)));

        Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint.setColor(0xC40B0718);
        float pad = w * 0.035f;
        canvas.drawRoundRect(new RectF(panelX - pad, panelY - pad,
                panelX + panelW + pad, panelY + textHeight + pad),
                w * 0.035f, w * 0.035f, panelPaint);

        canvas.save();
        canvas.translate(panelX, panelY);
        titleLayout.draw(canvas);
        canvas.translate(0, titleLayout.getHeight() + gap);
        bodyLayout.draw(canvas);
        canvas.translate(0, bodyLayout.getHeight() + gap);
        ctaLayout.draw(canvas);
        canvas.restore();

        return bitmap;
    }

    private static void drawBackground(Canvas canvas, int style, int w, int h) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int start = 0xFF0A0716;
        int middle = 0xFF3B1D7A;
        int end = 0xFF12468F;
        if (style == 2) {
            start = 0xFF100A22;
            middle = 0xFF4C1D95;
            end = 0xFF1D4ED8;
        } else if (style == 3) {
            start = 0xFF120A2A;
            middle = 0xFF6D28D9;
            end = 0xFF0EA5E9;
        } else if (style == 4) {
            start = 0xFF07101F;
            middle = 0xFF312E81;
            end = 0xFF22D3EE;
        }

        float dx = style == 2 ? w : 0f;
        float dy = (style == 0 || style == 4) ? h : h * 0.35f;
        paint.setShader(new LinearGradient(0, 0, dx, dy, start, middle, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);

        paint.setShader(new RadialGradient(w * 0.82f, h * 0.12f, w * 0.62f,
                0x663B82F6, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(new RadialGradient(w * 0.16f, h * 0.88f, w * 0.55f,
                0x5522D3EE, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x22FFFFFF);
        paint.setStrokeWidth(Math.max(2f, w / 300f));
        if (style == 1 || style == 5) {
            for (int x = -h; x < w + h; x += w / 10) {
                canvas.drawLine(x, 0, x + h, h, paint);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                float cx = w * (0.12f + i * 0.25f);
                float cy = h * (i % 2 == 0 ? 0.18f : 0.82f);
                canvas.drawCircle(cx, cy, w * (0.06f + i * 0.015f), paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x22FFFFFF);
        canvas.drawCircle(w * 0.88f, h * 0.22f, w * 0.015f, paint);
    }

    /** Сохраняет PNG в приватную папку приложения — разрешение не нужно. */
    public static File save(Context context, Bitmap bitmap, String prefix) throws IOException {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) {
            throw new IOException("Папка приложения недоступна");
        }
        File file = new File(dir, prefix + "_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("Не удалось записать PNG");
            }
        }
        return file;
    }
}
