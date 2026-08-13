/*
 * Music - pemutar musik offline tanpa iklan
 * Copyright (C) 2026 GenzPx
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.genzpx.music.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.genzpx.music.R;
import com.genzpx.music.util.DeviceGuard;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/**
 * Panduan melonggarkan pembatasan latar belakang bawaan ROM.
 * Seluruhnya opsional; aplikasi tetap berfungsi tanpa perubahan apa pun.
 */
public class GuardSheet extends BottomSheetDialog {

    public GuardSheet(Context context) {
        super(context, R.style.SheetTheme);
        setContentView(R.layout.sheet_guard);

        TextView brand = findViewById(R.id.guard_brand);
        LinearLayout stepsBox = findViewById(R.id.guard_steps);
        MaterialButton btnBattery = findViewById(R.id.guard_btn_battery);
        MaterialButton btnAutostart = findViewById(R.id.guard_btn_autostart);
        TextView status = findViewById(R.id.guard_status);

        brand.setText(context.getString(R.string.guard_brand_fmt, DeviceGuard.brandLabel()));

        // Langkah manual, dinomori
        String[] steps = DeviceGuard.steps();
        for (int i = 0; i < steps.length; i++) {
            TextView t = new TextView(context);
            t.setText(context.getString(R.string.guard_step_fmt, i + 1, steps[i]));
            t.setTextSize(13f);
            t.setPadding(0, i == 0 ? 0 : dp(context, 10), 0, 0);
            t.setTextColor(context.getResources().getColor(R.color.text_secondary));
            stepsBox.addView(t);
        }

        if (DeviceGuard.isBatteryUnrestricted(context)) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.guard_battery_ok);
        } else {
            status.setVisibility(View.GONE);
        }

        btnBattery.setOnClickListener(v -> {
            if (!DeviceGuard.openBatterySettings(context)) {
                Toast.makeText(context, R.string.guard_open_failed, Toast.LENGTH_LONG).show();
            }
            dismiss();
        });

        // Layar autostart tidak ada di semua ROM
        if (DeviceGuard.hasAutostart(context)) {
            btnAutostart.setVisibility(View.VISIBLE);
            btnAutostart.setOnClickListener(v -> {
                if (!DeviceGuard.openAutostart(context)) {
                    Toast.makeText(context, R.string.guard_open_failed, Toast.LENGTH_LONG).show();
                }
                dismiss();
            });
        } else {
            btnAutostart.setVisibility(View.GONE);
        }

        findViewById(R.id.guard_btn_close).setOnClickListener(v -> dismiss());
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }
}
