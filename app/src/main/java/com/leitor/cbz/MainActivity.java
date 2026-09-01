package com.leitor.cbz;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.MediaStore;
import android.content.res.Configuration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MainActivity extends Activity {

    private ImageView imageView;

    private List<String> pages = new ArrayList<>();
    private ZipFile zipFile;
    private int currentPage = 0;
    private Bitmap currentBitmap = null;

    private Button openBtn;

    private FrameLayout topBar;
    private TextView fileNameText;
    private Button readingModeBtn;
    private boolean isRtlMode = true;

    private LinearLayout bottomBar;
    private TextView pageCounter;
    private SeekBar pageSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout mainLayout = new FrameLayout(this);
        mainLayout.setBackgroundColor(Color.WHITE);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.GRAY);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mainLayout.addView(imageView);

        openBtn = new Button(this);
        openBtn.setText("Open File (CBZ)");
        FrameLayout.LayoutParams openBtnParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        openBtnParams.gravity = Gravity.CENTER;
        openBtn.setLayoutParams(openBtnParams);
        mainLayout.addView(openBtn);

        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, 1001);
                openBtn.setVisibility(View.GONE);
            }
        });

        topBar = new FrameLayout(this);
        topBar.setBackgroundColor(Color.argb(220, 20, 20, 20));
        int topBarHeightPx = (int) (50 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            topBarHeightPx
        );
        topBarParams.gravity = Gravity.TOP;
        topBar.setLayoutParams(topBarParams);
        topBar.setVisibility(View.INVISIBLE);

        fileNameText = new TextView(this);
        fileNameText.setTextColor(Color.WHITE);
        fileNameText.setTextSize(16f);
        fileNameText.setSingleLine(true);
        fileNameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams fileNameTextParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        fileNameTextParams.gravity = Gravity.CENTER;
        int fileNameTextPaddingPx = (int) (80 * getResources().getDisplayMetrics().density);
        fileNameTextParams.setMargins(fileNameTextPaddingPx, 0, fileNameTextPaddingPx, 0);
        fileNameText.setLayoutParams(fileNameTextParams);

        readingModeBtn = new Button(this);
        readingModeBtn.setText("RTL");
        readingModeBtn.setBackgroundColor(Color.TRANSPARENT);
        readingModeBtn.setTextColor(Color.WHITE);

        FrameLayout.LayoutParams readingModeBtnParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        readingModeBtnParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        readingModeBtn.setLayoutParams(readingModeBtnParams);

        readingModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateReadingMode();
            }
        });

        topBar.addView(fileNameText);
        topBar.addView(readingModeBtn);
        mainLayout.addView(topBar);

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(Color.argb(220, 20, 20, 20));
        int bottomBarHeightPx = (int) (50 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams bottomBarParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            bottomBarHeightPx
        );
        bottomBarParams.gravity = Gravity.BOTTOM;
        bottomBar.setLayoutParams(bottomBarParams);
        bottomBar.setVisibility(View.INVISIBLE);

        pageSlider = new SeekBar(this);
        LinearLayout.LayoutParams pageSliderParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        pageSlider.setLayoutParams(pageSliderParams);
        int pageSliderPaddingPx = (int) (20 * getResources().getDisplayMetrics().density);
        pageSlider.setPadding(pageSliderPaddingPx, 0, pageSliderPaddingPx, 0);
        pageSlider.setScaleX(-1f);

        pageSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    loadPage(progress);
                    updatePageTextCounter();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        pageCounter = new TextView(this);
        pageCounter.setTextColor(Color.WHITE);
        pageCounter.setTextSize(16f);
        pageCounter.setGravity(Gravity.CENTER);
        int pageCounterWidthPx = (int) (120 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams pageCounterParams = new LinearLayout.LayoutParams(
            pageCounterWidthPx,
            LinearLayout.LayoutParams.MATCH_PARENT
        );
        int pageCounterPaddingPx = (int) (10 * getResources().getDisplayMetrics().density);
        pageCounter.setPadding(pageCounterPaddingPx, 0, pageCounterPaddingPx, 0);
        pageCounter.setLayoutParams(pageCounterParams);

        bottomBar.addView(pageSlider);
        bottomBar.addView(pageCounter);
        mainLayout.addView(bottomBar);

        setContentView(mainLayout);

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float screenWidth = v.getWidth();
                    float touchXAxis = event.getX();
                    if (touchXAxis <= screenWidth * 0.25f) {
                        navigate(1);
                    } else if (touchXAxis >= screenWidth * 0.75f) {
                        navigate(-1);
                    }
                }
                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            String realPath = getPath(uri);

            if (realPath != null) {
                if (realPath.toLowerCase().endsWith(".cbz")) {
                    openFile(realPath);
                    imageView.setBackgroundColor(Color.WHITE);
                    topBar.setVisibility(View.VISIBLE);
                    bottomBar.setVisibility(View.VISIBLE);
                    toggleFrame(true);
                } else {
                    Toast.makeText(this, "Invalid file extension", Toast.LENGTH_LONG).show();
                    openBtn.setVisibility(View.VISIBLE);
                }
            } else {
                Toast.makeText(this, "Real Path is wrong", Toast.LENGTH_LONG).show();
                openBtn.setVisibility(View.VISIBLE);
            }
        } else {
            openBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (openBtn.getVisibility() == View.GONE) {
            openBtn.setVisibility(View.VISIBLE);
            imageView.setImageBitmap(null);

            if (currentBitmap != null) currentBitmap.recycle();
            currentBitmap = null;

            closeFile();

            imageView.setBackgroundColor(Color.GRAY);
            topBar.setVisibility(View.INVISIBLE);
            bottomBar.setVisibility(View.INVISIBLE);

            toggleFrame(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (bottomBar.getVisibility() == View.VISIBLE) {
            toggleFrame(true);
            imageView.requestLayout();
        }
    }

    private String getPath(Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            int colIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(colIndex);
            cursor.close();
            return path;
        }
        return null;
    }

    private void openFile(String path) {
        try {
            zipFile = new ZipFile(path);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    String name = entry.getName();
                    if (!name.contains("__MACOSX") && !name.contains("/.") && !name.startsWith(".")) {
                        if (name.toLowerCase().endsWith(".jpg") ||
                            name.toLowerCase().endsWith(".jpeg") ||
                            name.toLowerCase().endsWith(".png") ||
                            name.toLowerCase().endsWith(".webp")) {
                            pages.add(name);
                        }
                    }
                }
            }

            Collections.sort(pages);

            if (!pages.isEmpty()) {
                loadPage(0);
                pageSlider.setMax(pages.size() - 1);
                String fileName = new java.io.File(path).getName();
                fileNameText.setText(fileName);
            } else {
                Toast.makeText(this, "No images found", Toast.LENGTH_LONG).show();
                openBtn.setVisibility(View.VISIBLE);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open test.cbz: " + e.getMessage(), Toast.LENGTH_LONG).show();
            openBtn.setVisibility(View.VISIBLE);
        }
    }

    private void closeFile() {
        try {
            if (zipFile != null) {
                zipFile.close();
                zipFile = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        pages.clear();
        currentPage = 0;
    }

    private void changePage(int direction) {
        int pageIndex = currentPage + direction;
        if (pageIndex >= 0 && pageIndex < pages.size()) {
            loadPage(pageIndex);
        }
    }

    private void loadPage(int pageIndex) {
        try {
            String entryName = pages.get(pageIndex);
            ZipEntry entry = zipFile.getEntry(entryName);
            InputStream imageStream = zipFile.getInputStream(entry);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
            imageStream.close();

            imageView.setImageBitmap(bitmap);

            if (currentBitmap != null) currentBitmap.recycle();

            currentBitmap = bitmap;
            currentPage = pageIndex;

            updatePageTextCounter();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePageTextCounter() {
        if (pageCounter != null) {
            String text = "";
            if (isRtlMode) {
                text = "[ " + pages.size() + " / " + (currentPage + 1) + " ]";
            } else {
                text = "[ " + (currentPage + 1) + " / " + pages.size() + " ]";
            }
            pageCounter.setText(text);
        }
        if (pageSlider != null) {
            pageSlider.setProgress(currentPage);
        }
    }

    private void toggleFrame(boolean activate) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) imageView.getLayoutParams();
        if (activate) {
            int barMarginPx = (int) (50 * getResources().getDisplayMetrics().density);
            params.setMargins(0, barMarginPx, 0, barMarginPx);
        } else {
            params.setMargins(0, 0, 0, 0);
        }
        imageView.setLayoutParams(params);
    }

    private void updateReadingMode() {
        isRtlMode = !isRtlMode;

        pageSlider.setScaleX(isRtlMode ? -1f : 1f);

        bottomBar.removeAllViews();
        if (isRtlMode) {
            bottomBar.addView(pageSlider);
            bottomBar.addView(pageCounter);
        } else {
            bottomBar.addView(pageCounter);
            bottomBar.addView(pageSlider);
        }

        if (readingModeBtn != null) {
            readingModeBtn.setText(isRtlMode ? "RTL" : "LTR");
        }

        updatePageTextCounter();
    }

    private void navigate(int touchSide) {
        int direction = isRtlMode ? touchSide : -touchSide;
        changePage(direction);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (zipFile != null) zipFile.close();
            if (currentBitmap != null) currentBitmap.recycle();
            currentBitmap = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

