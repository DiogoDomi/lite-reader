package com.leitor.cbz.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfEngine implements BookEngine {

    private PdfiumCore pdfiumCore;
    private PdfDocument pdfDocument;
    private ParcelFileDescriptor fileDescriptor;

    private int pageCount = 0;
    private boolean isSliceMode = false;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> currentPreloadTask = null;
    private Bitmap preloadBitmap = null;
    private int preloadPageIndex = -1;
    private final Object decodeLock = new Object();

    public PdfEngine(Context context) {
        pdfiumCore = new PdfiumCore(context);
    }

    @Override
    public void openFile(String path) throws Exception {
        closeFile();

        File f = new File(path);
        fileDescriptor = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        pdfDocument = pdfiumCore.newDocument(fileDescriptor);
        pageCount = pdfiumCore.getPageCount(pdfDocument);

        if (pageCount == 0) {
            throw new Exception("No pages found in PDF file.");
        }
    }

    @Override
    public void setSliceMode(boolean sliceMode) {
        this.isSliceMode = sliceMode;
    }

    @Override
    public int getPageCount() {
        return pageCount;
    }

    @Override
    public Bitmap[] loadPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pageCount) {
            return null;
        }

        Bitmap decodedBitmap = null;

        synchronized (decodeLock) {
            if (preloadBitmap != null && preloadPageIndex == pageIndex) {
                decodedBitmap = preloadBitmap;
                preloadBitmap = null;
                preloadPageIndex = -1;
            } else {
                safeRecycle(preloadBitmap);
                preloadBitmap = null;
                preloadPageIndex = -1;
                decodedBitmap = renderPdfPageToBitmap(pageIndex);
            }
        }

        return processBitmap(decodedBitmap);
    }

    @Override
    public void preloadNextPage(final int currentPage, final int direction) {
        if (currentPreloadTask != null && !currentPreloadTask.isDone()) {
            currentPreloadTask.cancel(true);
        }

        currentPreloadTask = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    int nextPageIndex = currentPage + direction;

                    if (nextPageIndex < 0 || nextPageIndex >= pageCount) return;
                    if (preloadBitmap != null && preloadPageIndex == nextPageIndex) return;

                    Bitmap tempBitmap = renderPdfPageToBitmap(nextPageIndex);

                    synchronized (decodeLock) {
                        if (Thread.currentThread().isInterrupted() || tempBitmap == null) {
                            safeRecycle(tempBitmap);
                            return;
                        }

                        safeRecycle(preloadBitmap);
                        preloadBitmap = tempBitmap;
                        preloadPageIndex = nextPageIndex;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private Bitmap renderPdfPageToBitmap(int pageIndex) {
        try {
            pdfiumCore.openPage(pdfDocument, pageIndex);

            int pw = pdfiumCore.getPageWidthPoint(pdfDocument, pageIndex);
            int ph = pdfiumCore.getPageHeightPoint(pdfDocument, pageIndex);

            int maxDim = Math.max(pw, ph);
            float scale = 800f / maxDim;

            if (scale > 1.0f) {
                scale = 1.0f;
            }

            int width = (int) (pw * scale);
            int height = (int) (ph * scale);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            bitmap.eraseColor(android.graphics.Color.WHITE);

            pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageIndex, 0, 0, width, height);

            return bitmap;
        } catch (OutOfMemoryError e) {
            try {
                int pw = pdfiumCore.getPageWidthPoint(pdfDocument, pageIndex);
                int ph = pdfiumCore.getPageHeightPoint(pdfDocument, pageIndex);

                int maxDim = Math.max(pw, ph);
                float scale = 600f / maxDim;

                if (scale > 1.0f) {
                    scale = 1.0f;
                }

                int fallbackWidth = (int) (pw * scale);
                int fallbackHeight = (int) (ph * scale);

                Bitmap bitmap = Bitmap.createBitmap(fallbackWidth, fallbackHeight, Bitmap.Config.RGB_565);
                bitmap.eraseColor(android.graphics.Color.WHITE);
                pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageIndex, 0, 0, fallbackWidth, fallbackHeight);
                return bitmap;
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap[] processBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (isSliceMode && width > height) {
            int mid = width / 2;
            int rightWidth = width - mid;

            Bitmap[] bitmapsArray = new Bitmap[2];
            bitmapsArray[0] = Bitmap.createBitmap(bitmap, mid, 0, rightWidth, height);
            bitmapsArray[1] = Bitmap.createBitmap(bitmap, 0, 0, mid, height);

            safeRecycle(bitmap);
            return bitmapsArray;
        } else {
            return new Bitmap[]{bitmap};
        }
    }

    @Override
    public void closeFile() {
        if (currentPreloadTask != null && !currentPreloadTask.isDone()) {
            currentPreloadTask.cancel(true);
        }

        synchronized (decodeLock) {
            safeRecycle(preloadBitmap);
            preloadBitmap = null;
            preloadPageIndex = -1;

            try {
                if (pdfDocument != null) {
                    pdfiumCore.closeDocument(pdfDocument);
                    pdfDocument = null;
                }
                if (fileDescriptor != null) {
                    fileDescriptor.close();
                    fileDescriptor = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void destroy() {
        closeFile();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void safeRecycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}

