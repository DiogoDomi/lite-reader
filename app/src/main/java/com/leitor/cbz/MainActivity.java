package com.leitor.cbz;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
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
    private Bitmap[] bitmaps = null;
    private int currentBitmapPart = 0;

    private Button openBtn;

    private FrameLayout topBar;
    private Button sliceBitmapModeBtn;
    private boolean isSliceBitmapMode = false;
    private TextView fileNameText;
    private Button readingModeBtn;
    private boolean isRtlMode = true;

    private LinearLayout bottomBar;
    private TextView pageCounter;
    private SeekBar pageSlider;

    private Handler hideHandler = new Handler();
    private Runnable hideRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout mainLayout = new FrameLayout(this);
        mainLayout.setBackgroundColor(Color.WHITE);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.GRAY);
        imageView.setAdjustViewBounds(true);
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

        sliceBitmapModeBtn = new Button(this);
        sliceBitmapModeBtn.setText(isSliceBitmapMode ? "Slice: ON" : "Slice: OFF");
        sliceBitmapModeBtn.setBackgroundColor(Color.TRANSPARENT);
        sliceBitmapModeBtn.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams sliceBitmapModeBtnParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        sliceBitmapModeBtnParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        sliceBitmapModeBtn.setLayoutParams(sliceBitmapModeBtnParams);

        sliceBitmapModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSlicePageMode();
                resetHideTimer();
            }
        });

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
                toggleReadingDirectionMode();
                resetHideTimer();
            }
        });

        topBar.addView(sliceBitmapModeBtn);
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
                    loadPage(progress, 1);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hideHandler.removeCallbacks(hideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                resetHideTimer();
            }
        });

        pageCounter = new TextView(this);
        pageCounter.setTextColor(Color.WHITE);
        pageCounter.setTextSize(14f);
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

        hideRunnable = new Runnable() {
            @Override
            public void run() {
                hideHUD();
            }
        };

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
                    } else {
                        if (zipFile != null) {
                            toggleHUD();
                        }
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
                    showHUD();
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

            closeFile();

            imageView.setBackgroundColor(Color.GRAY);

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
                loadPage(0, 1);
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
        recycleCurrentBitmaps();
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
        currentBitmapPart = 0;
        hideHUD();
    }

    private void changePage(int direction) {
        int pageIndex = currentPage + direction;
        if (pageIndex >= 0 && pageIndex < pages.size()) {
            loadPage(pageIndex, direction);
            showPageTextCounter();
        }
    }

    private void loadPage(int pageIndex, int direction) {
        try {
            recycleCurrentBitmaps();

            String entryName = pages.get(pageIndex);
            ZipEntry entry = zipFile.getEntry(entryName);
            InputStream imageStream = zipFile.getInputStream(entry);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap bitmap = BitmapFactory.decodeStream(imageStream, null, options);
            imageStream.close();

            processBitmap(bitmap);

            currentPage = pageIndex;

            if (direction < 0) {
                currentBitmapPart = bitmaps.length - 1;
            } else {
                currentBitmapPart = 0;
            }

            renderBitmap(bitmaps[currentBitmapPart]);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleSlicePageMode() {
        isSliceBitmapMode = !isSliceBitmapMode;

        if (sliceBitmapModeBtn != null) {
            sliceBitmapModeBtn.setText(isSliceBitmapMode ? "Slice: ON" : "Slice: OFF");
        }

        if (zipFile != null && !pages.isEmpty()) {
            loadPage(currentPage, 1);
        }
    }

    private void processBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (isSliceBitmapMode && width > height) {
            int mid = width / 2;
            int rightWidth = width - mid;

            bitmaps = new Bitmap[2];

            bitmaps[0] = Bitmap.createBitmap(bitmap, mid, 0, rightWidth, height);
            bitmaps[1] = Bitmap.createBitmap(bitmap, 0, 0, mid, height);

            safeRecycle(bitmap);
        } else {
            bitmaps = new Bitmap[1];
            bitmaps[0] = bitmap;
        }
    }

    private void renderBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        updatePageTextCounter();
    }

    private void updatePageTextCounter() {
        if (pageCounter != null) {
            String text = "";
            if (isRtlMode) {
                text = "[ " + pages.size() + " / " + (currentPage + 1) + " ]";
            } else {
                text = "[ " + (currentPage + 1) + " / " + pages.size() + " ]";
            }

            if (bitmaps != null && bitmaps.length > 1) {
                int currentDisplayPart = currentBitmapPart + 1;
                int totalParts = bitmaps.length;

                if (isRtlMode) {
                    text += " \n( " + totalParts + " / " + currentDisplayPart + " )";
                } else {
                    text += " \n( " + currentDisplayPart + " / " + totalParts + " )";
                }
            }

            pageCounter.setText(text);
        }
        if (pageSlider != null) {
            pageSlider.setProgress(currentPage);
        }
    }

    private void showHUD() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        bottomBar.setBackgroundColor(Color.argb(220, 20, 20, 20));
        pageCounter.setBackgroundColor(Color.TRANSPARENT);
        pageSlider.setVisibility(View.VISIBLE);
        pageCounter.setVisibility(View.VISIBLE);

        resetHideTimer();
    }

    private void hideHUD() {
        topBar.setVisibility(View.INVISIBLE);
        bottomBar.setVisibility(View.INVISIBLE);
        pageSlider.setVisibility(View.INVISIBLE);
        pageCounter.setVisibility(View.INVISIBLE);

        hideHandler.removeCallbacks(hideRunnable);
    }

    private void toggleHUD() {
        if (topBar.getVisibility() == View.VISIBLE) {
            hideHUD();
        } else {
            showHUD();
        }
    }

    private void showPageTextCounter() {
        if (topBar.getVisibility() == View.VISIBLE) {
            resetHideTimer();
            return;
        }

        topBar.setVisibility(View.INVISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        bottomBar.setBackgroundColor(Color.TRANSPARENT);
        pageCounter.setBackgroundColor(Color.argb(220, 20, 20, 20));
        pageSlider.setVisibility(View.INVISIBLE);
        pageCounter.setVisibility(View.VISIBLE);

        resetHideTimer();
    }

    private void resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, 5000);
    }

    private void toggleFrame(boolean activate) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) imageView.getLayoutParams();
        if (activate) {
            int verticalMarginPx = (int) (60 * getResources().getDisplayMetrics().density);
            int horizontalMarginPx = (int) (5 * getResources().getDisplayMetrics().density);
            params.setMargins(horizontalMarginPx, verticalMarginPx, horizontalMarginPx, verticalMarginPx);
        } else {
            params.setMargins(0, 0, 0, 0);
        }
        imageView.setLayoutParams(params);
    }

    private void toggleReadingDirectionMode() {
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

        if (direction > 0) {
            if (bitmaps != null && currentBitmapPart < bitmaps.length - 1) {
                currentBitmapPart++;
                renderBitmap(bitmaps[currentBitmapPart]);
                showPageTextCounter();
            } else {
                changePage(1);
            }
        } else if (direction < 0) {
            if (bitmaps != null && currentBitmapPart > 0) {
                currentBitmapPart--;
                renderBitmap(bitmaps[currentBitmapPart]);
                showPageTextCounter();
            } else {
                changePage(-1);
            }
        }
    }

    private void safeRecycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void recycleCurrentBitmaps() {
        if (bitmaps != null) {
            for (int i = 0; i < bitmaps.length; i++) {
                safeRecycle(bitmaps[i]);
                bitmaps[i] = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (zipFile != null) zipFile.close();
            recycleCurrentBitmaps();
        } catch (Exception e) {
            e.printStackTrace();
        }
        hideHandler.removeCallbacks(hideRunnable);
    }
}

