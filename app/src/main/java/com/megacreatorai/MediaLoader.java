package com.megacreatorai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Загрузка результата генерации: data URL или обычная ссылка. Выполняется в фоне. */
public final class MediaLoader {

    private MediaLoader() {
    }

    public static Bitmap load(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Сервер вернул пустой результат");
        }
        String source = value.trim();
        if (source.startsWith("data:")) {
            int comma = source.indexOf(',');
            if (comma < 0) {
                throw new IOException("Некорректный data URL");
            }
            String meta = source.substring(0, comma);
            if (!meta.contains("image")) {
                throw new IOException("Сервер вернул не изображение");
            }
            byte[] bytes;
            try {
                bytes = Base64.decode(source.substring(comma + 1), Base64.DEFAULT);
            } catch (IllegalArgumentException e) {
                throw new IOException("Не удалось декодировать изображение");
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                throw new IOException("Не удалось прочитать изображение");
            }
            return bitmap;
        }
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            throw new IOException("Сервер вернул неизвестный формат результата");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Не удалось скачать файл: код " + code);
            }
            InputStream in = connection.getInputStream();
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) {
                    throw new IOException("Не удалось прочитать изображение");
                }
                return bitmap;
            } finally {
                in.close();
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Для отладки: компактный вид результата без большого base64. */
    public static String shortResult(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 120 ? value.substring(0, 120) + "…" : value;
    }
}
