package com.leitor.cbz;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private ListView listView;
    private TextView pathTextView;
    private List<File> fileList = new ArrayList<>();
    private File currentDirectory;
    private File rootDirectory;
    private SharedPreferences readerPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootDirectory = Environment.getExternalStorageDirectory();
        readerPrefs = getSharedPreferences("CBZReaderPrefs", MODE_PRIVATE);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#121212"));

        FrameLayout topBar = new FrameLayout(this);
        topBar.setBackgroundColor(Color.parseColor("#1F1F1F"));
        int topBarHeight = (int) (56 * getResources().getDisplayMetrics().density);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                topBarHeight
        ));

        pathTextView = new TextView(this);
        pathTextView.setTextColor(Color.WHITE);
        pathTextView.setTextSize(18f);
        pathTextView.setSingleLine(true);
        pathTextView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        titleParams.setMargins(paddingPx, 0, paddingPx, 0);
        pathTextView.setLayoutParams(titleParams);

        topBar.addView(pathTextView);
        mainLayout.addView(topBar);

        listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        listView.setDividerHeight(1);
        mainLayout.addView(listView);

        setContentView(mainLayout);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File clickedFile = fileList.get(position);

                if (clickedFile.getName().equals("..")) {
                    loadFolder(currentDirectory.getParentFile());
                } else if (clickedFile.isDirectory()) {
                    loadFolder(clickedFile);
                } else {
                    openReader(clickedFile.getAbsolutePath());
                }
            }
        });

        if (savedInstanceState != null) {
            String savedPath = savedInstanceState.getString("current_path");
            if (savedPath != null) {
                File savedDir = new File(savedPath);
                if (savedDir.exists() && savedDir.isDirectory()) {
                    loadFolder(savedDir);
                    return;
                }
            }
        }

        loadFolder(rootDirectory);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listView != null && listView.getAdapter() != null) {
            ((ArrayAdapter) listView.getAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentDirectory != null) {
            outState.putString("current_path", currentDirectory.getAbsolutePath());
        }
    }

    private void loadFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            Toast.makeText(this, "Cannot access folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDirectory = folder;
        pathTextView.setText(folder.getName().equals("0") ? "Internal Storage" : folder.getName());
        fileList.clear();

        File[] files = folder.listFiles();
        List<File> directories = new ArrayList<>();
        List<File> books = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                if (file.isHidden()) continue;

                if (file.isDirectory()) {
                    directories.add(file);
                } else {
                    String lowerPath = file.getName().toLowerCase();
                    if (lowerPath.endsWith(".cbz") || lowerPath.endsWith(".zip") || lowerPath.endsWith(".pdf")) {
                        books.add(file);
                    }
                }
            }
        }

        Comparator<File> fileComparator = new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        };
        Collections.sort(directories, fileComparator);
        Collections.sort(books, fileComparator);

        if (!folder.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
            fileList.add(new File(folder, ".."));
        }

        fileList.addAll(directories);
        fileList.addAll(books);

        ArrayAdapter<File> adapter = new ArrayAdapter<File>(this, android.R.layout.simple_list_item_1, fileList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                File file = getItem(position);

                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                view.setPadding(pad, pad, pad, pad);

                if (file.getName().equals("..")) {
                    view.setText("⬅️ Go Back");
                    view.setTextColor(Color.parseColor("#4FC3F7"));
                } else if (file.isDirectory()) {
                    view.setText("📁 " + file.getName());
                    view.setTextColor(Color.parseColor("#FFD54F"));
                } else {
                    String path = file.getAbsolutePath();
                    int page = readerPrefs.getInt(path + "_page", 0);
                    int total = readerPrefs.getInt(path + "_total", 0);

                    String status = "";
                    int color = Color.WHITE;

                    if (total > 0) {
                        if (page >= total - 1) {
                            status = "  [100%]";
                            color = Color.parseColor("#5A6B5D");
                        } else if (page > 0) {
                            int percent = (int) (((float) (page + 1) / total) * 100);
                            status = "  [" + percent + "%]";
                            color = Color.parseColor("#64B5F6");
                        }
                    } else if (page > 0) {
                        status = "  [Reading]";
                        color = Color.parseColor("#64B5F6");
                    }

                    view.setText("📖 " + file.getName() + status);
                    view.setTextColor(color);
                }
                return view;
            }
        };

        listView.setAdapter(adapter);
    }

    private void openReader(String path) {
        Intent intent = new Intent(MainActivity.this, ReaderActivity.class);
        intent.putExtra("FILE_PATH", path);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (currentDirectory != null && !currentDirectory.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
            loadFolder(currentDirectory.getParentFile());
        } else {
            super.onBackPressed();
        }
    }
}

