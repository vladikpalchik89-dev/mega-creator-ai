package com.megacreatorai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApiToolsTest {

    @Test
    public void mapsEveryStudioTool() {
        assertEquals("idei", ApiTools.serverTool("Идеи"));
        assertEquals("script", ApiTools.serverTool("Сценарий"));
        assertEquals("ad", ApiTools.serverTool("Реклама"));
        assertEquals("rewrite", ApiTools.serverTool("Переписать"));
        assertEquals("hashtags", ApiTools.serverTool("Хештеги"));
        assertEquals("description", ApiTools.serverTool("Описание"));
        assertEquals("greeting", ApiTools.serverTool("Поздравление"));
        assertEquals("plan", ApiTools.serverTool("План"));
        assertEquals("idei", ApiTools.serverTool(null));
    }

    @Test
    public void detectsContactType() {
        assertEquals("email", ApiTools.contactField("user@example.com"));
        assertEquals("phone", ApiTools.contactField("+7 999 123-45-67"));
    }
}
