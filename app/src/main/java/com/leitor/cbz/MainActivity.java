package com.leitor.cbz;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.MediaStore;
import android.content.res.Configuration;
import android.view.GestureDetector;

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
    private String currentFilePath = null;

    private Button openBtn;

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

    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private static final int BRIGHTNESS = 3;

    private int mode = NONE;
    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;
    private float[] matrixValues = new float[9];
    private float baseScale = 1f;
    private boolean isPanning = false;
    private long downTime = 0;
    private int startDimAlpha = 0;

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        sliceBitmapModeBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(MainActivity.this, "Slice Mode", Toast.LENGTH_SHORT).show();
                return true;
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
        readingModeBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(MainActivity.this, "Reading Direction", Toast.LENGTH_SHORT).show();
                return true;
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
        volKeysModeBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(MainActivity.this, "Volume Key Navigation", Toast.LENGTH_SHORT).show();
                return true;
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
        fullscreenModeBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(MainActivity.this, "Immersive Mode", Toast.LENGTH_SHORT).show();
                return true;
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
                SharedPreferences.Editor editor = getSharedPreferences("GlobalPrefs", MODE_PRIVATE).edit();
                editor.putBoolean("dim_mode", isDimMode);
                editor.apply();
                resetHideTimer();
            }
        });
        dimModeBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(MainActivity.this, "Screen Dimmer", Toast.LENGTH_SHORT).show();
                return true;
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
                if (fromUser && pageCounter != null) {
                    String text = "";
                    if (isRtlMode) {
                        text = "[ " + pages.size() + " / " + (progress + 1) + " ]";
                    } else {
                        text = "[ " + (progress + 1) + " / " + pages.size() + " ]";
                    }
                    pageCounter.setText(text);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hideHandler.removeCallbacks(hideRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                resetHideTimer();
                int progress = seekBar.getProgress();
                if (progress != currentPage) {
                    int direction = progress > currentPage ? 1 : -1;
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

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (bitmaps == null || bitmaps.length <= currentBitmapPart || bitmaps[currentBitmapPart] == null) {
                    return false;
                }

                matrix.getValues(matrixValues);
                float currentScale = matrixValues[Matrix.MSCALE_X];

                if (currentScale < baseScale * 1.1f) {
                    float targetScale = 2.5f;
                    matrix.postScale(targetScale, targetScale, e.getX(), e.getY());
                    limitZoom(matrix);
                    limitDrag(matrix, imageView);
                } else {
                    if (bitmaps != null && currentBitmapPart >= 0 && currentBitmapPart < bitmaps.length) {
                        initBaseMatrix(bitmaps[currentBitmapPart]);
                    }
                }
                imageView.setImageMatrix(matrix);
                return true;
            }
        });

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (gestureDetector.onTouchEvent(event)) {
                    return true;
                }

                if (bitmaps == null || bitmaps.length == 0 || bitmaps[currentBitmapPart] == null) {
                    return false;
                }

                ImageView view = (ImageView) v;

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        float screenWidthDown = v.getWidth();
                        float touchXDown = event.getX();

                        if (isDimMode && touchXDown <= screenWidthDown * 0.15f) {
                            mode = BRIGHTNESS;
                            start.set(event.getX(), event.getY());
                            startDimAlpha = dimAlpha;
                            isPanning = false;
                            downTime = System.currentTimeMillis();
                            break;
                        }

                        savedMatrix.set(matrix);
                        start.set(event.getX(), event.getY());
                        mode = DRAG;
                        isPanning = false;
                        downTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        oldDist = spacing(event);
                        if (oldDist > 10f) {
                            savedMatrix.set(matrix);
                            midPoint(mid, event);
                            mode = ZOOM;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        if (mode == BRIGHTNESS) {
                            long clickDuration = System.currentTimeMillis() - downTime;
                            float deltaX = Math.abs(event.getX() - start.x);
                            float deltaY = Math.abs(event.getY() - start.y);

                            if (!isPanning && clickDuration < 300 && deltaX < 20 && deltaY < 20) {
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
                            } else {
                                SharedPreferences.Editor editor = getSharedPreferences("GlobalPrefs", MODE_PRIVATE).edit();
                                editor.putInt("dim_alpha", dimAlpha);
                                editor.apply();
                            }
                        } else if (mode == DRAG) {
                            long clickDuration = System.currentTimeMillis() - downTime;
                            float deltaX = Math.abs(event.getX() - start.x);
                            float deltaY = Math.abs(event.getY() - start.y);

                            if (!isPanning && clickDuration < 300 && deltaX < 20 && deltaY < 20) {
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
                        }
                        mode = NONE;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (mode == BRIGHTNESS) {
                            float deltaY = event.getY() - start.y;
                            float screenHeight = v.getHeight();

                            if (Math.abs(deltaY) > 20) {
                                isPanning = true;
                            }

                            float alphaChange = (deltaY / screenHeight) * 255f;
                            int newAlpha = startDimAlpha + (int) alphaChange;

                            if (newAlpha < 0) newAlpha = 0;
                            if (newAlpha > 235) newAlpha = 235;

                            dimAlpha = newAlpha;

                            if (dimOverlay != null && isDimMode) {
                                dimOverlay.setBackgroundColor(Color.argb(dimAlpha, 0, 0, 0));
                            }
                            return true;

                        } else if (mode == DRAG) {
                            float deltaX = event.getX() - start.x;
                            float deltaY = event.getY() - start.y;
                            if (Math.abs(deltaX) > 20 || Math.abs(deltaY) > 20) {
                                isPanning = true;
                            }
                            matrix.set(savedMatrix);
                            matrix.postTranslate(deltaX, deltaY);
                            limitDrag(matrix, view);
                        } else if (mode == ZOOM) {
                            float newDist = spacing(event);
                            if (newDist > 10f) {
                                matrix.set(savedMatrix);
                                float scale = newDist / oldDist;
                                matrix.postScale(scale, scale, mid.x, mid.y);
                                limitZoom(matrix);
                                limitDrag(matrix, view);
                            }
                        }
                        break;
                }
                view.setImageMatrix(matrix);
                return true;
            }
        });
    }

    private void updateButtonColors() {
        int colorOff = isDimMode ? Color.LTGRAY : Color.GRAY;

        if (sliceBitmapModeBtn != null) {
            sliceBitmapModeBtn.setTextColor(isSliceBitmapMode ? Color.WHITE : colorOff);
        }
        if (volKeysModeBtn != null) {
            volKeysModeBtn.setTextColor(isVolKeysEnabled ? Color.WHITE : colorOff);
        }
        if (fullscreenModeBtn != null) {
            fullscreenModeBtn.setTextColor(isFullscreenMode ? Color.WHITE : colorOff);
        }
        if (dimModeBtn != null) {
            dimModeBtn.setTextColor(isDimMode ? Color.WHITE : colorOff);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isVolKeysEnabled && zipFile != null) {
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
        if (isVolKeysEnabled && zipFile != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
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
    public void onConfigurationChanged(final android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        toggleFrame(zipFile != null);

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
                            initBaseMatrix(bitmaps[currentBitmapPart]);
                        }
                    }
                }
            }
        });
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
            currentFilePath = path;
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
                SharedPreferences prefs = getSharedPreferences("CBZReaderPrefs", MODE_PRIVATE);
                int savedPage = prefs.getInt(currentFilePath + "_page", 0);
                int savedPart = prefs.getInt(currentFilePath + "_part", 0);
                isSliceBitmapMode = prefs.getBoolean(currentFilePath + "_slice", false);
                isRtlMode = prefs.getBoolean(currentFilePath + "_rtl", true);

                if (readingModeBtn != null) {
                    readingModeBtn.setText(isRtlMode ? "RTL" : "LTR");
                }

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

                if (savedPage < 0 || savedPage >= pages.size()) {
                    savedPage = 0;
                    savedPart = 0;
                }

                loadPage(savedPage, 1);

                if (savedPart > 0 && bitmaps != null && savedPart < bitmaps.length) {
                    currentBitmapPart = savedPart;
                    renderBitmap(bitmaps[currentBitmapPart]);
                }

                pageSlider.setMax(pages.size() - 1);
                String fileName = new java.io.File(path).getName();
                fileNameText.setText(fileName);
            } else {
                Toast.makeText(this, "No images found", Toast.LENGTH_LONG).show();
                openBtn.setVisibility(View.VISIBLE);
                currentFilePath = null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open test.cbz: " + e.getMessage(), Toast.LENGTH_LONG).show();
            openBtn.setVisibility(View.VISIBLE);
            currentFilePath = null;
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
        currentFilePath = null;
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

        updateButtonColors();

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
        final Bitmap bmp = bitmap;

        if (imageView.getWidth() > 0 && imageView.getHeight() > 0) {
            initBaseMatrix(bmp);
        } else {
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @SuppressWarnings("deprecation")
                @Override
                public void onGlobalLayout() {
                    if (imageView.getWidth() > 0 && imageView.getHeight() > 0) {
                        imageView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        initBaseMatrix(bmp);
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

    private void initBaseMatrix(Bitmap bitmap) {
        if (bitmap == null || imageView.getWidth() == 0 || imageView.getHeight() == 0) return;

        float vWidth = imageView.getWidth();
        float vHeight = imageView.getHeight();
        float dWidth = bitmap.getWidth();
        float dHeight = bitmap.getHeight();

        if (dWidth * vHeight > vWidth * dHeight) {
            baseScale = vWidth / dWidth;
        } else {
            baseScale = vHeight / dHeight;
        }

        float dx = (vWidth - dWidth * baseScale) / 2f;
        float dy = (vHeight - dHeight * baseScale) / 2f;

        matrix.setScale(baseScale, baseScale);
        matrix.postTranslate(dx, dy);
        imageView.setImageMatrix(matrix);
        imageView.invalidate();
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2f, y / 2f);
    }

    private void limitZoom(Matrix m) {
        m.getValues(matrixValues);
        float scaleX = matrixValues[Matrix.MSCALE_X];
        if (scaleX < baseScale) {
            float target = baseScale / scaleX;
            m.postScale(target, target, mid.x, mid.y);
        } else if (scaleX > baseScale * 4f) {
            float target = (baseScale * 4f) / scaleX;
            m.postScale(target, target, mid.x, mid.y);
        }
    }

    private void limitDrag(Matrix m, ImageView view) {
        if (bitmaps == null || bitmaps.length <= currentBitmapPart) return;
        Bitmap currentBmp = bitmaps[currentBitmapPart];
        if (currentBmp == null) return;

        m.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        float scaleX = matrixValues[Matrix.MSCALE_X];
        float scaleY = matrixValues[Matrix.MSCALE_Y];

        float imageWidth = currentBmp.getWidth() * scaleX;
        float imageHeight = currentBmp.getHeight() * scaleY;
        float viewWidth = view.getWidth();
        float viewHeight = view.getHeight();

        float minX, maxX, minY, maxY;

        if (imageWidth <= viewWidth) {
            minX = (viewWidth - imageWidth) / 2f;
            maxX = minX;
        } else {
            minX = viewWidth - imageWidth;
            maxX = 0;
        }

        if (imageHeight <= viewHeight) {
            minY = (viewHeight - imageHeight) / 2f;
            maxY = minY;
        } else {
            minY = viewHeight - imageHeight;
            maxY = 0;
        }

        if (transX < minX) transX = minX;
        if (transX > maxX) transX = maxX;
        if (transY < minY) transY = minY;
        if (transY > maxY) transY = maxY;

        matrixValues[Matrix.MTRANS_X] = transX;
        matrixValues[Matrix.MTRANS_Y] = transY;
        m.setValues(matrixValues);
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

        if (isFullscreenMode) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }

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
        saveProgress();
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

