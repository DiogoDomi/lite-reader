package com.leitor.cbz.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

public class TouchManager implements View.OnTouchListener {

    public interface TouchCallback {
        void onNavigate(int direction);
        void onToggleHUD();
        void onBrightnessChange(int newAlpha);
        void onBrightnessSave(int finalAlpha);
    }

    private TouchCallback callback;
    private ImageView imageView;
    private GestureDetector gestureDetector;
    private Bitmap currentBitmap = null;

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

    private int filterMode = 0;
    private int dimAlpha = 130;
    private int startDimAlpha = 0;

    public TouchManager(Context context, ImageView imageView, TouchCallback callback) {
        this.imageView = imageView;
        this.callback = callback;

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (currentBitmap == null || currentBitmap.isRecycled()) return false;

                matrix.getValues(matrixValues);
                float currentScale = matrixValues[Matrix.MSCALE_X];

                if (currentScale < baseScale * 1.1f) {
                    float targetScale = 2.5f;
                    matrix.postScale(targetScale, targetScale, e.getX(), e.getY());
                    limitZoom();
                    limitDrag();
                } else {
                    applyBaseMatrix(currentBitmap);
                }
                imageView.setImageMatrix(matrix);
                return true;
            }
        });
    }

    public void setFilterState(int filterMode, int dimAlpha) {
        this.filterMode = filterMode;

        int maxAlpha = (filterMode == 1) ? 130 : ((filterMode == 2) ? 90 : 255);
        this.dimAlpha = Math.min(dimAlpha, maxAlpha);
    }

    public void applyBaseMatrix(Bitmap bitmap) {
        this.currentBitmap = bitmap;
        if (bitmap == null || bitmap.isRecycled() || imageView.getWidth() == 0 || imageView.getHeight() == 0) return;

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

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        }

        if (currentBitmap == null || currentBitmap.isRecycled()) {
            return false;
        }

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                float screenWidthDown = v.getWidth();
                float touchXDown = event.getX();

                if (filterMode != 0 && touchXDown <= screenWidthDown * 0.15f) {
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
                        handleTap(v, event.getX());
                    } else {
                        if (callback != null) callback.onBrightnessSave(dimAlpha);
                    }
                } else if (mode == DRAG) {
                    long clickDuration = System.currentTimeMillis() - downTime;
                    float deltaX = Math.abs(event.getX() - start.x);
                    float deltaY = Math.abs(event.getY() - start.y);

                    if (!isPanning && clickDuration < 300 && deltaX < 20 && deltaY < 20) {
                        handleTap(v, event.getX());
                    }
                }
                mode = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == BRIGHTNESS) {
                    float deltaY = event.getY() - start.y;
                    float screenHeight = v.getHeight();

                    if (Math.abs(deltaY) > 20) isPanning = true;

                    float alphaChange = (deltaY / screenHeight) * 255f;
                    int newAlpha = startDimAlpha + (int) alphaChange;

                    int maxAlpha = (filterMode == 1) ? 130 : 90;

                    if (newAlpha < 0) newAlpha = 0;
                    if (newAlpha > maxAlpha) newAlpha = maxAlpha;

                    dimAlpha = newAlpha;

                    if (callback != null) callback.onBrightnessChange(dimAlpha);
                    return true;

                } else if (mode == DRAG) {
                    float deltaX = event.getX() - start.x;
                    float deltaY = event.getY() - start.y;
                    if (Math.abs(deltaX) > 20 || Math.abs(deltaY) > 20) isPanning = true;

                    matrix.set(savedMatrix);
                    matrix.postTranslate(deltaX, deltaY);
                    limitDrag();
                } else if (mode == ZOOM) {
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        matrix.set(savedMatrix);
                        float scale = newDist / oldDist;
                        matrix.postScale(scale, scale, mid.x, mid.y);
                        limitZoom();
                        limitDrag();
                    }
                }
                break;
        }
        imageView.setImageMatrix(matrix);
        return true;
    }

    private void handleTap(View v, float touchXAxis) {
        float screenWidth = v.getWidth();
        if (touchXAxis <= screenWidth * 0.25f) {
            if (callback != null) callback.onNavigate(1);
        } else if (touchXAxis >= screenWidth * 0.75f) {
            if (callback != null) callback.onNavigate(-1);
        } else {
            if (callback != null) callback.onToggleHUD();
        }
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

    private void limitZoom() {
        matrix.getValues(matrixValues);
        float scaleX = matrixValues[Matrix.MSCALE_X];
        if (scaleX < baseScale) {
            float target = baseScale / scaleX;
            matrix.postScale(target, target, mid.x, mid.y);
        } else if (scaleX > baseScale * 4f) {
            float target = (baseScale * 4f) / scaleX;
            matrix.postScale(target, target, mid.x, mid.y);
        }
    }

    private void limitDrag() {
        if (currentBitmap == null || currentBitmap.isRecycled()) return;

        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        float scaleX = matrixValues[Matrix.MSCALE_X];
        float scaleY = matrixValues[Matrix.MSCALE_Y];

        float imageWidth = currentBitmap.getWidth() * scaleX;
        float imageHeight = currentBitmap.getHeight() * scaleY;
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();

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
        matrix.setValues(matrixValues);
    }
}

