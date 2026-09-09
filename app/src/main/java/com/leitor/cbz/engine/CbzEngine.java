package com.leitor.cbz.engine;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CbzEngine implements BookEngine {

    private ZipFile zipFile;
    private List<String> pages = new ArrayList<>();
    private boolean isSliceMode = false;

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> currentPreloadTask = null;
    private Bitmap preloadBitmap = null;
    private int preloadPageIndex = -1;
    private final Object decodeLock = new Object();

    @Override
    public void openFile(String path) throws Exception {
        closeFile();
        pages.clear();

        zipFile = new ZipFile(path);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                String name = entry.getName();
                if (!name.contains("__MACOSX") && !name.contains("/.") && !name.startsWith(".")) {
                    String lower = name.toLowerCase();
                    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".webp")) {
                        pages.add(name);
                    }
                }
            }
        }

        Collections.sort(pages);

        if (pages.isEmpty()) {
            throw new Exception("No images found in CBZ file.");
        }
    }

    @Override
    public void setSliceMode(boolean sliceMode) {
        this.isSliceMode = sliceMode;
    }

    @Override
    public int getPageCount() {
        return pages.size();
    }

    @Override
    public Bitmap[] loadPage(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) {
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

                try {
                    String entryName = pages.get(pageIndex);
                    ZipEntry entry = zipFile.getEntry(entryName);
                    InputStream imageStream = zipFile.getInputStream(entry);

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;

                    decodedBitmap = BitmapFactory.decodeStream(imageStream, null, options);
                    imageStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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

                    if (nextPageIndex < 0 || nextPageIndex >= pages.size()) return;
                    if (preloadBitmap != null && preloadPageIndex == nextPageIndex) return;

                    String entryName = pages.get(nextPageIndex);
                    ZipEntry entry = zipFile.getEntry(entryName);
                    InputStream imageStream = zipFile.getInputStream(entry);

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;

                    Bitmap tempBitmap = BitmapFactory.decodeStream(imageStream, null, options);
                    imageStream.close();

                    synchronized (decodeLock) {
                        if (Thread.currentThread().isInterrupted()) {
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
                if (zipFile != null) {
                    zipFile.close();
                    zipFile = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        pages.clear();
    }

    @Override
    public void destroy() {
        closeFile();
        if (executor != null) {
            executor.shutdownNow();
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

    private void safeRecycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}

