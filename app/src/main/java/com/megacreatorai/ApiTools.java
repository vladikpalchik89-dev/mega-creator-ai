package com.megacreatorai;

/** Преобразование русских названий инструментов в контракт серверного API. */
public final class ApiTools {

    private ApiTools() {
    }

    /** Имя инструмента AI-Студии → значение параметра tool на сервере. */
    public static String serverTool(String title) {
        if (title == null) {
            return "idei";
        }
        switch (title.trim()) {
            case "Сценарий":
                return "script";
            case "Реклама":
                return "ad";
            case "Переписать":
                return "rewrite";
            case "Хештеги":
                return "hashtags";
            case "Описание":
                return "description";
            case "Поздравление":
                return "greeting";
            case "План":
                return "plan";
            default:
                return "idei";
        }
    }

    /** Контакт для входа: почта или телефон. */
    public static String contactField(String value) {
        return value != null && value.contains("@") ? "email" : "phone";
    }
}
