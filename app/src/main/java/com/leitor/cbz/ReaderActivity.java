package com.leitor.cbz;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.Gravity;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.content.res.Configuration;

import com.leitor.cbz.engine.BookEngine;
import com.leitor.cbz.engine.CbzEngine;
import com.leitor.cbz.ui.TouchManager;

public class ReaderActivity extends Activity {

    private ImageView imageView;
    private BookEngine bookEngine;
    private TouchManager touchManager;

    private int currentPage = 0;
    private Bitmap[] bitmaps = null;
    private int currentBitmapPart = 0;
    private String currentFilePath = null;

    private FrameLayout topBar;
    private LinearLayout settingsContainer;
    private Button sliceBitmapModeBtn;
    private Button readingModeBtn;
    private Button volKeysModeBtn;
    private Button fullscreenModeBtn;
    private Button dimModeBtn;

    private boolean isSliceBitmapMode = false;
    private boolean isRtlMode = true;
    private boolean isVolKeysEnabled = false;
    private boolean isFullscreenMode = true;
    private boolean isDimMode = false;
    private int dimAlpha = 130;

    private TextView fileNameText;
    private LinearLayout bottomBar;
    private TextView pageCounter;
    private SeekBar pageSlider;
    private View dimOverlay;

    private Handler hideHandler = new Handler();
    private Runnable hideRunnable;

    private long lastNavTime = 0;
    private static final int NAV_COOLDOWN_MS = 250;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            SharedPreferences globalPrefs = getSharedPreferences("GlobalPrefs", MODE_PRIVATE);
            isVolKeysEnabled = globalPrefs.getBoolean("vol_keys", false);
            isFullscreenMode = globalPrefs.getBoolean("fullscreen_mode", true);
            isDimMode = globalPrefs.getBoolean("dim_mode", false);
            dimAlpha = globalPrefs.getInt("dim_alpha", 130);

            FrameLayout mainLayout = new FrameLayout(this);
            mainLayout.setBackgroundColor(Color.WHITE);

            imageView = new ImageView(this);
            imageView.setBackgroundColor(Color.GRAY);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.MATRIX);
            FrameLayout.LayoutParams ivParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
            imageView.setLayoutParams(ivParams);
            mainLayout.addView(imageView);

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
            fileNameTextParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            int fileNameTextPaddingPx = (int) (15 * getResources().getDisplayMetrics().density);
            int fileNameRightMarginPx = (int) (260 * getResources().getDisplayMetrics().density);
            fileNameTextParams.setMargins(fileNameTextPaddingPx, 0, fileNameRightMarginPx, 0);
            fileNameText.setLayoutParams(fileNameTextParams);

            settingsContainer = new LinearLayout(this);
            settingsContainer.setOrientation(LinearLayout.HORIZONTAL);
            FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            );
            settingsParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            settingsContainer.setLayoutParams(settingsParams);

            int btnMarginPx = (int) (6 * getResources().getDisplayMetrics().density);
            int btnPaddingPx = (int) (8 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams btnLayoutConfig = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnLayoutConfig.gravity = Gravity.CENTER_VERTICAL;
            btnLayoutConfig.setMargins(btnMarginPx, 0, btnMarginPx, 0);

            sliceBitmapModeBtn = new Button(this);
            sliceBitmapModeBtn.setLayoutParams(btnLayoutConfig);
            sliceBitmapModeBtn.setText("SLC");
            sliceBitmapModeBtn.setBackgroundColor(Color.TRANSPARENT);
            sliceBitmapModeBtn.setGravity(Gravity.CENTER);
            sliceBitmapModeBtn.setPadding(btnPaddingPx, btnPaddingPx, btnPaddingPx, btnPaddingPx);
            sliceBitmapModeBtn.setTextSize(14f);
            sliceBitmapModeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleSlicePageMode();
                    resetHideTimer();
                }
            });

            readingModeBtn = new Button(this);
            readingModeBtn.setLayoutParams(btnLayoutConfig);
            readingModeBtn.setText(isRtlMode ? "RTL" : "LTR");
            readingModeBtn.setBackgroundColor(Color.TRANSPARENT);
            readingModeBtn.setTextColor(Color.WHITE);
            readingModeBtn.setGravity(Gravity.CENTER);
            readingModeBtn.setPadding(btnPaddingPx, btnPaddingPx, btnPaddingPx, btnPaddingPx);
            readingModeBtn.setTextSize(14f);
            readingModeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleReadingDirectionMode();
                    resetHideTimer();
                }
            });

            volKeysModeBtn = new Button(this);
            volKeysModeBtn.setLayoutParams(btnLayoutConfig);
            volKeysModeBtn.setText("VOL");
            volKeysModeBtn.setBackgroundColor(Color.TRANSPARENT);
            volKeysModeBtn.setGravity(Gravity.CENTER);
            volKeysModeBtn.setPadding(btnPaddingPx, btnPaddingPx, btnPaddingPx, btnPaddingPx);
            volKeysModeBtn.setTextSize(14f);
            volKeysModeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isVolKeysEnabled = !isVolKeysEnabled;
                    updateButtonColors();
                    SharedPreferences.Editor editor = getSharedPreferences("GlobalPrefs", MODE_PRIVATE).edit();
                    editor.putBoolean("vol_keys", isVolKeysEnabled);
                    editor.apply();
                    resetHideTimer();
                }
            });

            fullscreenModeBtn = new Button(this);
            fullscreenModeBtn.setLayoutParams(btnLayoutConfig);
            fullscreenModeBtn.setText("FUL");
            fullscreenModeBtn.setBackgroundColor(Color.TRANSPARENT);
            fullscreenModeBtn.setGravity(Gravity.CENTER);
            fullscreenModeBtn.setPadding(btnPaddingPx, btnPaddingPx, btnPaddingPx, btnPaddingPx);
            fullscreenModeBtn.setTextSize(14f);
            fullscreenModeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isFullscreenMode = !isFullscreenMode;
                    updateButtonColors();
                    SharedPreferences.Editor editor = getSharedPreferences("GlobalPrefs", MODE_PRIVATE).edit();
                    editor.putBoolean("fullscreen_mode", isFullscreenMode);
                    editor.apply();

                    if (topBar.getVisibility() == View.INVISIBLE) {
                        hideHUD();
                    } else {
                        resetHideTimer();
                    }
                }
            });

            dimModeBtn = new Button(this);
            dimModeBtn.setLayoutParams(btnLayoutConfig);
            dimModeBtn.setText("DIM");
            dimModeBtn.setBackgroundColor(Color.TRANSPARENT);
            dimModeBtn.setGravity(Gravity.CENTER);
            dimModeBtn.setPadding(btnPaddingPx, btnPaddingPx, btnPaddingPx, btnPaddingPx);
            dimModeBtn.setTextSize(14f);
            dimModeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isDimMode = !isDimMode;
                    if (dimOverlay != null) {
                        dimOverlay.setBackgroundColor(isDimMode ? Color.argb(dimAlpha, 0, 0, 0) : Color.TRANSPARENT);
                    }
                    updateButtonColors();
                    if (touchManager != null) {
                        touchManager.setDimState(isDimMode, dimAlpha);
                    }
                    SharedPreferences.Editor editor = getSharedPreferences("GlobalPrefs", MODE_PRIVATE).edit();
                    editor.putBoolean("dim_mode", isDimMode);
                    editor.apply();
                    resetHideTimer();
                }
            });

            updateButtonColors();

            settingsContainer.addView(sliceBitmapModeBtn);
            settingsContainer.addView(readingModeBtn);
            settingsContainer.addView(volKeysModeBtn);
            settingsContainer.addView(fullscreenModeBtn);
            settingsContainer.addView(dimModeBtn);

            topBar.addView(fileNameText);
            topBar.addView(settingsContainer);
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
                    if (fromUser && pageCounter != null && bookEngine != null) {
                        pageCounter.setText(getBasePageText(progress));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    hideHandler.removeCallbacks(hideRunnable);
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    resetHideTimer();
                    if (bookEngine == null) return;

                    int progress = seekBar.getProgress();
                    if (progress != currentPage) {
                        int direction;
                        if (progress == 0) {
                            direction = 1;
                        } else if (progress == bookEngine.getPageCount() - 1) {
                            direction = -1;
                        } else {
                            direction = 1;
                        }
                        loadPage(progress, direction);
                    } else {
                        updatePageTextCounter();
                    }
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

            dimOverlay = new View(this);
            dimOverlay.setBackgroundColor(isDimMode ? Color.argb(dimAlpha, 0, 0, 0) : Color.TRANSPARENT);
            dimOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ));
            dimOverlay.setClickable(false);
            dimOverlay.setFocusable(false);

            mainLayout.addView(dimOverlay);

            setContentView(mainLayout);

            touchManager = new TouchManager(this, imageView, new TouchManager.TouchCallback() {
                @Override
                public void onNavigate(int direction) {
                    navigate(direction);
                }

                @Override
                public void onToggleHUD() {
                    if (bookEngine != null) {
                        toggleHUD();
                    }
                }

                @Override
                public void onBrightnessChange(int newAlpha) {
                    dimAlpha = newAlpha;
                    if (dimOverlay != null && isDimMode) {
                        dimOverlay.setBackgroundColor(Color.argb(dimAlpha, 0, 0, 0));
                    }
                }

                @Override
                public void onBrightnessSave(int finalAlpha) {
                    dimAlpha = finalAlpha;
                    SharedPreferences.Editor editor = getSharedPreferences("GlobalPrefs", MODE_PRIVATE).edit();
                    editor.putInt("dim_alpha", dimAlpha);
                    editor.apply();
                }
            });

            touchManager.setDimState(isDimMode, dimAlpha);
            imageView.setOnTouchListener(touchManager);

            String filePath = getIntent().getStringExtra("FILE_PATH");
            if (filePath != null) {
                imageView.setBackgroundColor(Color.WHITE);
                openFile(filePath);
                showHUD();
                toggleFrame(true);
            } else {
                Toast.makeText(this, "No file received!", Toast.LENGTH_SHORT).show();
                finish();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Reader Init Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void updateButtonColors() {
        int colorOff = isDimMode ? Color.LTGRAY : Color.GRAY;

        if (sliceBitmapModeBtn != null) sliceBitmapModeBtn.setTextColor(isSliceBitmapMode ? Color.WHITE : colorOff);
        if (volKeysModeBtn != null) volKeysModeBtn.setTextColor(isVolKeysEnabled ? Color.WHITE : colorOff);
        if (fullscreenModeBtn != null) fullscreenModeBtn.setTextColor(isFullscreenMode ? Color.WHITE : colorOff);
        if (dimModeBtn != null) dimModeBtn.setTextColor(isDimMode ? Color.WHITE : colorOff);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isVolKeysEnabled && bookEngine != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                int actionNext = KeyEvent.KEYCODE_VOLUME_UP;
                int actionPrev = KeyEvent.KEYCODE_VOLUME_DOWN;

                if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_180) {
                    actionNext = KeyEvent.KEYCODE_VOLUME_DOWN;
                    actionPrev = KeyEvent.KEYCODE_VOLUME_UP;
                }

                int forceNext = isRtlMode ? 1 : -1;
                int forcePrev = isRtlMode ? -1 : 1;

                if (keyCode == actionNext) {
                    navigate(forceNext);
                    return true;
                } else if (keyCode == actionPrev) {
                    navigate(forcePrev);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isVolKeysEnabled && bookEngine != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onConfigurationChanged(final android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        toggleFrame(bookEngine != null);
        imageView.requestLayout();
        final boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;

        imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                int w = imageView.getWidth();
                int h = imageView.getHeight();
                if (w > 0 && h > 0) {
                    boolean viewIsLandscape = w > h;
                    if (viewIsLandscape == isLandscape) {
                        imageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        if (bitmaps != null && currentBitmapPart >= 0 && currentBitmapPart < bitmaps.length) {
                            touchManager.applyBaseMatrix(bitmaps[currentBitmapPart]);
                        }
                    }
                }
            }
        });
    }

    private void openFile(String path) {
        try {
            currentFilePath = path;

            bookEngine = new CbzEngine();
            bookEngine.openFile(path);

            SharedPreferences prefs = getSharedPreferences("CBZReaderPrefs", MODE_PRIVATE);
            int savedPage = prefs.getInt(currentFilePath + "_page", 0);
            int savedPart = prefs.getInt(currentFilePath + "_part", 0);
            isSliceBitmapMode = prefs.getBoolean(currentFilePath + "_slice", false);
            isRtlMode = prefs.getBoolean(currentFilePath + "_rtl", true);

            bookEngine.setSliceMode(isSliceBitmapMode);

            if (readingModeBtn != null) readingModeBtn.setText(isRtlMode ? "RTL" : "LTR");

            isVolKeysEnabled = false;
            updateButtonColors();

            pageSlider.setScaleX(isRtlMode ? -1f : 1f);
            bottomBar.removeAllViews();
            if (isRtlMode) {
                bottomBar.addView(pageSlider);
                bottomBar.addView(pageCounter);
            } else {
                bottomBar.addView(pageCounter);
                bottomBar.addView(pageSlider);
            }

            if (savedPage < 0 || savedPage >= bookEngine.getPageCount()) {
                savedPage = 0;
                savedPart = 0;
            }

            loadPage(savedPage, 1);

            if (savedPart > 0 && bitmaps != null && savedPart < bitmaps.length) {
                currentBitmapPart = savedPart;
                renderBitmap(bitmaps[currentBitmapPart]);
            }

            pageSlider.setMax(bookEngine.getPageCount() - 1);
            String fileName = new java.io.File(path).getName();
            fileNameText.setText(fileName);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void closeFile() {
        if (bookEngine != null) {
            bookEngine.destroy();
            bookEngine = null;
        }
        if (bitmaps != null) {
            for (int i = 0; i < bitmaps.length; i++) {
                safeRecycle(bitmaps[i]);
                bitmaps[i] = null;
            }
        }
        currentPage = 0;
        currentBitmapPart = 0;
        currentFilePath = null;
        hideHUD();
    }

    private void changePage(int direction) {
        int pageIndex = currentPage + direction;
        if (bookEngine != null && pageIndex >= 0 && pageIndex < bookEngine.getPageCount()) {
            loadPage(pageIndex, direction);
            showPageTextCounter();
        }
    }

    private void loadPage(int pageIndex, int direction) {
        if (bookEngine == null) return;

        if (bitmaps != null) {
            for (int i = 0; i < bitmaps.length; i++) {
                safeRecycle(bitmaps[i]);
                bitmaps[i] = null;
            }
        }

        if (touchManager != null) {
            touchManager.applyBaseMatrix(null);
        }

        bitmaps = bookEngine.loadPage(pageIndex);
        currentPage = pageIndex;

        if (direction < 0 && bitmaps != null) {
            currentBitmapPart = bitmaps.length - 1;
        } else {
            currentBitmapPart = 0;
        }

        if (bitmaps != null && bitmaps.length > 0) {
            renderBitmap(bitmaps[currentBitmapPart]);
        }

        bookEngine.preloadNextPage(pageIndex, direction);
    }

    private void toggleSlicePageMode() {
        isSliceBitmapMode = !isSliceBitmapMode;
        if (bookEngine != null) bookEngine.setSliceMode(isSliceBitmapMode);
        updateButtonColors();
        if (bookEngine != null) loadPage(currentPage, 1);
    }

    private void renderBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
        final Bitmap bmp = bitmap;

        if (imageView.getWidth() > 0 && imageView.getHeight() > 0) {
            touchManager.applyBaseMatrix(bmp);
        } else {
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressWarnings("deprecation")
                @Override
                public void onGlobalLayout() {
                    if (imageView.getWidth() > 0 && imageView.getHeight() > 0) {
                        imageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        touchManager.applyBaseMatrix(bmp);
                    }
                }
            });
        }
        updatePageTextCounter();
        saveProgress();
    }

    private void saveProgress() {
        if (currentFilePath != null) {
            SharedPreferences prefs = getSharedPreferences("CBZReaderPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(currentFilePath + "_page", currentPage);
            editor.putInt(currentFilePath + "_part", currentBitmapPart);
            editor.putBoolean(currentFilePath + "_slice", isSliceBitmapMode);
            editor.putBoolean(currentFilePath + "_rtl", isRtlMode);
            editor.apply();
        }
    }

    private String getBasePageText(int targetPage) {
        if (isRtlMode) {
            return "[ " + bookEngine.getPageCount() + " / " + (targetPage + 1) + " ]";
        } else {
            return "[ " + (targetPage + 1) + " / " + bookEngine.getPageCount() + " ]";
        }
    }

    private void updatePageTextCounter() {
        if (pageCounter != null && bookEngine != null) {
            String text = getBasePageText(currentPage);

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
        if (pageSlider != null) pageSlider.setProgress(currentPage);
    }

    private void showHUD() {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        bottomBar.setBackgroundColor(Color.argb(220, 20, 20, 20));
        pageCounter.setBackgroundColor(Color.TRANSPARENT);
        pageSlider.setVisibility(View.VISIBLE);
        pageCounter.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        resetHideTimer();
    }

    private void hideHUD() {
        topBar.setVisibility(View.INVISIBLE);
        bottomBar.setVisibility(View.INVISIBLE);
        pageSlider.setVisibility(View.INVISIBLE);
        pageCounter.setVisibility(View.INVISIBLE);

        if (isFullscreenMode) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        hideHandler.removeCallbacks(hideRunnable);
    }

    private void toggleHUD() {
        if (topBar.getVisibility() == View.VISIBLE) hideHUD();
        else showHUD();
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

        if (isFullscreenMode) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
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

        if (readingModeBtn != null) readingModeBtn.setText(isRtlMode ? "RTL" : "LTR");
        updatePageTextCounter();
        saveProgress();
    }

    private void navigate(int touchSide) {
        long now = System.currentTimeMillis();
        if (now - lastNavTime < NAV_COOLDOWN_MS) return;
        lastNavTime = now;

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
        closeFile();
        hideHandler.removeCallbacks(hideRunnable);
    }
}

