package com.leitor.cbz.engine;

import android.graphics.Bitmap;

public interface BookEngine {
    void openFile(String path) throws Exception;

    void closeFile();

    void destroy();

    int getPageCount();

    void setSliceMode(boolean isSliceMode);

    Bitmap[] loadPage(int pageIndex);

    void preloadNextPage(int currentPageIndex, int direction);
}

