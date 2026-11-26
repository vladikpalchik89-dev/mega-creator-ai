package com.megacreatorai;

/** Простой колбэк для асинхронных запросов к серверу (без java.util.function). */
public interface ApiCallback {
    void onSuccess(String response);

    void onError(String message);
}
