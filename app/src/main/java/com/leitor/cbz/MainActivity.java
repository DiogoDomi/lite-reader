package com.leitor.cbz;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout layout = new FrameLayout(this);

        Button openBtn = new Button(this);
        openBtn.setText("Open File");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER;
        openBtn.setLayoutParams(params);

        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    startActivityForResult(intent, 1001);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "File explorer error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        layout.addView(openBtn);
        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            try {
                Uri uri = data.getData();
                String realPath = getPath(uri);

                if (realPath != null) {
                    if (realPath.toLowerCase().endsWith(".cbz")) {
                        Intent intent = new Intent(MainActivity.this, ReaderActivity.class);
                        intent.putExtra("FILE_PATH", realPath);
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "Unsupported format.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Error: Could not extract file path from URI.", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Intent Crash Prevented: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getPath(Uri uri) {
        if (uri == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme())) return uri.getPath();

        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int colIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(colIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}

