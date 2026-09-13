package com.leitor.cbz;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsActivity extends Activity {

    private SharedPreferences globalPrefs;
    private TextView defaultDirTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        globalPrefs = getSharedPreferences("GlobalPrefs", MODE_PRIVATE);
        float density = getResources().getDisplayMetrics().density;

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#121212"));

        FrameLayout topBar = new FrameLayout(this);
        topBar.setBackgroundColor(Color.parseColor("#1A1A1A"));
        int topBarHeight = (int) (64 * density);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, topBarHeight
        ));

        TextView title = new TextView(this);
        title.setText("Global Settings");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20f);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        int paddingPx = (int) (16 * density);
        titleParams.setMargins(paddingPx, 0, paddingPx, 0);
        title.setLayoutParams(titleParams);
        topBar.addView(title);
        mainLayout.addView(topBar);

        LinearLayout optionsContainer = new LinearLayout(this);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        optionsContainer.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        TextView dirLabel = new TextView(this);
        dirLabel.setText("Default Directory");
        dirLabel.setTextColor(Color.parseColor("#AAAAAA"));
        dirLabel.setTextSize(14f);
        optionsContainer.addView(dirLabel);

        LinearLayout dirRow = new LinearLayout(this);
        dirRow.setOrientation(LinearLayout.HORIZONTAL);
        dirRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, (int)(8 * density), 0, (int)(32 * density));
        dirRow.setLayoutParams(rowParams);

        defaultDirTextView = new TextView(this);
        defaultDirTextView.setTextColor(Color.WHITE);
        defaultDirTextView.setTextSize(16f);
        defaultDirTextView.setText(globalPrefs.getString("default_dir", Environment.getExternalStorageDirectory().getAbsolutePath()));
        defaultDirTextView.setSingleLine(true);
        defaultDirTextView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        defaultDirTextView.setBackgroundColor(Color.parseColor("#222222"));
        defaultDirTextView.setPadding((int)(12 * density), (int)(12 * density), (int)(12 * density), (int)(12 * density));

        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        pathParams.rightMargin = (int)(8 * density);
        defaultDirTextView.setLayoutParams(pathParams);

        Button btnPick = new Button(this);
        btnPick.setText("📂");
        btnPick.setTextSize(18f);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SettingsActivity.this, FileExplorerActivity.class);
                intent.putExtra("PICK_MODE", true);
                startActivityForResult(intent, 1001);
            }
        });

        dirRow.addView(defaultDirTextView);
        dirRow.addView(btnPick);
        optionsContainer.addView(dirRow);

        optionsContainer.addView(createToggleSetting("Default Reading Mode", "LTR", "RTL", "default_rtl", true, density));
        optionsContainer.addView(createToggleSetting("Default Page Slicing", "OFF", "ON", "default_slice", false, density));
        optionsContainer.addView(createTripleToggleSetting("Default Color Filter", "OFF", "DIM", "SEP", "filter_mode", 0, density));
        optionsContainer.addView(createToggleSetting("Volume Keys Navigation", "OFF", "ON", "vol_keys", false, density));
        optionsContainer.addView(createToggleSetting("Fullscreen on Startup", "OFF", "ON", "fullscreen_mode", true, density));

        mainLayout.addView(optionsContainer);
        setContentView(mainLayout);
    }

    private View createToggleSetting(String labelText, final String leftText, final String rightText, final String prefKey, boolean defaultValue, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, (int)(24 * density));
        row.setLayoutParams(rowParams);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        label.setLayoutParams(labelParams);

        LinearLayout toggleContainer = new LinearLayout(this);
        toggleContainer.setOrientation(LinearLayout.HORIZONTAL);
        toggleContainer.setBackgroundColor(Color.parseColor("#333333"));
        toggleContainer.setPadding(2, 2, 2, 2);

        final TextView leftBtn = new TextView(this);
        leftBtn.setText(leftText);
        leftBtn.setTextSize(14f);
        leftBtn.setPadding((int)(16 * density), (int)(8 * density), (int)(16 * density), (int)(8 * density));
        leftBtn.setGravity(Gravity.CENTER);

        final TextView rightBtn = new TextView(this);
        rightBtn.setText(rightText);
        rightBtn.setTextSize(14f);
        rightBtn.setPadding((int)(16 * density), (int)(8 * density), (int)(16 * density), (int)(8 * density));
        rightBtn.setGravity(Gravity.CENTER);

        toggleContainer.addView(leftBtn);
        toggleContainer.addView(rightBtn);

        row.addView(label);
        row.addView(toggleContainer);

        final boolean[] currentState = {globalPrefs.getBoolean(prefKey, defaultValue)};

        final Runnable updateUI = new Runnable() {
            @Override
            public void run() {
                if (currentState[0]) {
                    rightBtn.setBackgroundColor(Color.parseColor("#64B5F6"));
                    rightBtn.setTextColor(Color.BLACK);
                    leftBtn.setBackgroundColor(Color.TRANSPARENT);
                    leftBtn.setTextColor(Color.WHITE);
                } else {
                    leftBtn.setBackgroundColor(Color.parseColor("#64B5F6"));
                    leftBtn.setTextColor(Color.BLACK);
                    rightBtn.setBackgroundColor(Color.TRANSPARENT);
                    rightBtn.setTextColor(Color.WHITE);
                }
            }
        };

        updateUI.run();

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentState[0] = (v == rightBtn);
                globalPrefs.edit().putBoolean(prefKey, currentState[0]).apply();
                updateUI.run();
            }
        };

        leftBtn.setOnClickListener(toggleListener);
        rightBtn.setOnClickListener(toggleListener);

        return row;
    }

    private View createTripleToggleSetting(String labelText, final String opt0, final String opt1, final String opt2, final String prefKey, int defaultValue, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, (int)(24 * density));
        row.setLayoutParams(rowParams);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
        );
        label.setLayoutParams(labelParams);

        LinearLayout toggleContainer = new LinearLayout(this);
        toggleContainer.setOrientation(LinearLayout.HORIZONTAL);
        toggleContainer.setBackgroundColor(Color.parseColor("#333333"));
        toggleContainer.setPadding(2, 2, 2, 2);

        final TextView btn0 = new TextView(this);
        btn0.setText(opt0);
        btn0.setTextSize(14f);
        btn0.setPadding((int)(12 * density), (int)(8 * density), (int)(12 * density), (int)(8 * density));
        btn0.setGravity(Gravity.CENTER);

        final TextView btn1 = new TextView(this);
        btn1.setText(opt1);
        btn1.setTextSize(14f);
        btn1.setPadding((int)(12 * density), (int)(8 * density), (int)(12 * density), (int)(8 * density));
        btn1.setGravity(Gravity.CENTER);

        final TextView btn2 = new TextView(this);
        btn2.setText(opt2);
        btn2.setTextSize(14f);
        btn2.setPadding((int)(12 * density), (int)(8 * density), (int)(12 * density), (int)(8 * density));
        btn2.setGravity(Gravity.CENTER);

        toggleContainer.addView(btn0);
        toggleContainer.addView(btn1);
        toggleContainer.addView(btn2);

        row.addView(label);
        row.addView(toggleContainer);

        final int[] currentState = {globalPrefs.getInt(prefKey, defaultValue)};

        final Runnable updateUI = new Runnable() {
            @Override
            public void run() {
                btn0.setBackgroundColor(Color.TRANSPARENT);
                btn0.setTextColor(Color.WHITE);
                btn1.setBackgroundColor(Color.TRANSPARENT);
                btn1.setTextColor(Color.WHITE);
                btn2.setBackgroundColor(Color.TRANSPARENT);
                btn2.setTextColor(Color.WHITE);

                if (currentState[0] == 0) {
                    btn0.setBackgroundColor(Color.parseColor("#64B5F6"));
                    btn0.setTextColor(Color.BLACK);
                } else if (currentState[0] == 1) {
                    btn1.setBackgroundColor(Color.parseColor("#64B5F6"));
                    btn1.setTextColor(Color.BLACK);
                } else if (currentState[0] == 2) {
                    btn2.setBackgroundColor(Color.parseColor("#64B5F6"));
                    btn2.setTextColor(Color.BLACK);
                }
            }
        };

        updateUI.run();

        View.OnClickListener toggleListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v == btn0) currentState[0] = 0;
                else if (v == btn1) currentState[0] = 1;
                else if (v == btn2) currentState[0] = 2;

                globalPrefs.edit().putInt(prefKey, currentState[0]).apply();
                updateUI.run();
            }
        };

        btn0.setOnClickListener(toggleListener);
        btn1.setOnClickListener(toggleListener);
        btn2.setOnClickListener(toggleListener);

        return row;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String pickedPath = data.getStringExtra("PICKED_DIR");
            if (pickedPath != null) {
                globalPrefs.edit().putString("default_dir", pickedPath).apply();
                defaultDirTextView.setText(pickedPath);
            }
        }
    }
}

