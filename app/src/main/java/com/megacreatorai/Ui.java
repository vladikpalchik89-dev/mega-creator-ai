package com.megacreatorai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Общие UI-помощники: единый стиль карточек, кнопок, полей и диалогов. */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context c, String value, int sizeSp, int colorRes, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value == null ? "" : value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(ContextCompat.getColor(c, colorRes));
        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }
        tv.setTextIsSelectable(true);
        return tv;
    }

    public static TextView section(Context c, String title) {
        TextView tv = text(c, title, 17, R.color.text, true);
        tv.setPadding(0, dp(c, 16), 0, dp(c, 8));
        return tv;
    }

    public static TextView muted(Context c, String value) {
        TextView tv = text(c, value, 12, R.color.text_muted, false);
        tv.setPadding(0, dp(c, 2), 0, dp(c, 10));
        return tv;
    }

    public static View spacer(Context c, int sizeDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, sizeDp)));
        return v;
    }

    /** Карточка инструмента: иконка-эмодзи, заголовок, подпись. */
    public static MaterialCardView card(Context c, String icon, String title,
                                        String subtitle, View.OnClickListener click) {
        MaterialCardView cv = new MaterialCardView(c);
        cv.setRadius(dp(c, 18));
        cv.setCardElevation(0);
        cv.setStrokeWidth(dp(c, 1));
        cv.setStrokeColor(ContextCompat.getColor(c, R.color.card_stroke));
        cv.setCardBackgroundColor(ContextCompat.getColor(c, R.color.card));
        cv.setClickable(click != null);
        cv.setFocusable(click != null);
        if (click != null) {
            cv.setOnClickListener(click);
        }

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));

        TextView ico = text(c, icon, 22, R.color.primary, true);
        ico.setWidth(dp(c, 34));
        ico.setGravity(Gravity.CENTER);
        row.addView(ico);

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cp.setMargins(dp(c, 8), 0, dp(c, 4), 0);
        column.setLayoutParams(cp);
        column.addView(text(c, title, 15, R.color.text, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            column.addView(text(c, subtitle, 11, R.color.text_muted, false));
        }
        row.addView(column);
        cv.addView(row);
        return cv;
    }

    /** Основная кнопка с градиентом или спокойная кнопка-фишка. */
    public static MaterialButton button(Context c, String label, boolean gradient,
                                        View.OnClickListener click) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setBackgroundTintList(null);
        b.setBackgroundResource(gradient ? R.drawable.bg_gradient : R.drawable.bg_chip);
        b.setTextColor(ContextCompat.getColor(c, gradient ? android.R.color.white : R.color.text));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 48)));
        b.setOnClickListener(click);
        return b;
    }

    public static MaterialButton chipButton(Context c, String label, View.OnClickListener click) {
        MaterialButton b = new MaterialButton(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setBackgroundTintList(null);
        b.setBackgroundResource(R.drawable.bg_chip);
        b.setTextColor(ContextCompat.getColor(c, R.color.text));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setMinimumWidth(dp(c, 80));
        b.setOnClickListener(click);
        return b;
    }

    public static EditText edit(Context c, String hint, String value, boolean multiline) {
        EditText et = new EditText(c);
        et.setHint(hint);
        et.setText(value == null ? "" : value);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setTextColor(ContextCompat.getColor(c, R.color.text));
        et.setHintTextColor(ContextCompat.getColor(c, R.color.text_muted));
        int padding = dp(c, 12);
        et.setPadding(padding, padding, padding, padding);
        et.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) {
            et.setMinLines(4);
            et.setGravity(Gravity.TOP);
        }
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    public static void dialog(Context c, String title, String message, String ok, Runnable action) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(ok, (d, w) -> {
                    if (action != null) {
                        action.run();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public static void info(Context c, String title, String message) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Ок", null)
                .show();
    }

    public static void toast(Context c, String message) {
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
    }

    public static void copy(Context c, String value) {
        if (value == null || value.isEmpty()) {
            toast(c, "Нечего копировать");
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("MEGA CREATOR AI", value));
            toast(c, "Скопировано в буфер обмена");
        }
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "MC";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            out.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return out.toString();
    }

    public static void open(Context c, Class<?> activity) {
        c.startActivity(new Intent(c, activity));
    }
}
