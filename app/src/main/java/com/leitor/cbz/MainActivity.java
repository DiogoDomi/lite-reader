package com.leitor.cbz;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.WHITE);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        setContentView(imageView);

        imageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float screenWidth = v.getWidth();
                    float touchXAxis = event.getX();
                    if (touchXAxis <= screenWidth * 0.25f) {
                        changePage(1);
                    } else if (touchXAxis >= screenWidth * 0.75f) {
                        changePage(-1);
                    }
                }
                return true;
            }
        });

        openFile();
    }

    private void openFile() {
        try {
            String path = Environment.getExternalStorageDirectory() + "/PDFs/test.cbz";
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
            } else {
                Toast.makeText(this, "No images found", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to open test.cbz: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

        } catch (Exception e) {
            e.printStackTrace();
        }
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

