package com.leitor.cbz;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileExplorerActivity extends Activity {

    private ListView listView;
    private TextView pathTextView;
    private List<File> fileList = new ArrayList<>();
    private File currentDirectory;
    private File rootDirectory;
    private SharedPreferences readerPrefs;
    private SharedPreferences explorerPrefs;

    private int sortColumn = 0;
    private boolean sortAscending = true;

    private TextView nameHeader;
    private TextView sizeHeader;
    private TextView dateHeader;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootDirectory = Environment.getExternalStorageDirectory();
        readerPrefs = getSharedPreferences("CBZReaderPrefs", MODE_PRIVATE);
        explorerPrefs = getSharedPreferences("ExplorerPrefs", MODE_PRIVATE);

        sortColumn = explorerPrefs.getInt("sort_col", 0);
        sortAscending = explorerPrefs.getBoolean("sort_asc", true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#121212"));

        FrameLayout topBar = new FrameLayout(this);
        topBar.setBackgroundColor(Color.parseColor("#1A1A1A"));
        int topBarHeight = (int) (64 * getResources().getDisplayMetrics().density);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                topBarHeight
        ));

        pathTextView = new TextView(this);
        pathTextView.setTextColor(Color.WHITE);
        pathTextView.setTextSize(20f);
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

        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setBackgroundColor(Color.parseColor("#222222"));
        headerBar.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        nameHeader = createHeaderTextView();
        sizeHeader = createHeaderTextView();
        sizeHeader.setGravity(Gravity.END);
        dateHeader = createHeaderTextView();
        dateHeader.setGravity(Gravity.END);

        nameHeader.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f));
        sizeHeader.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f));
        dateHeader.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f));

        nameHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleSortClick(0); }
        });
        sizeHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleSortClick(1); }
        });
        dateHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { handleSortClick(2); }
        });

        headerBar.addView(nameHeader);
        headerBar.addView(sizeHeader);
        headerBar.addView(dateHeader);
        mainLayout.addView(headerBar);

        updateHeaderUI();

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
                if (clickedFile.isDirectory()) {
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

    private TextView createHeaderTextView() {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.parseColor("#888888"));
        tv.setTextSize(18f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(8, 8, 8, 8);
        return tv;
    }

    private void handleSortClick(int column) {
        if (sortColumn == column) {
            sortAscending = !sortAscending;
        } else {
            sortColumn = column;
            if (column == 0) sortAscending = true;
            else sortAscending = false;
        }

        explorerPrefs.edit()
                .putInt("sort_col", sortColumn)
                .putBoolean("sort_asc", sortAscending)
                .apply();

        updateHeaderUI();
        loadFolder(currentDirectory);
    }

    private void updateHeaderUI() {
        nameHeader.setText("Name" + (sortColumn == 0 ? (sortAscending ? " ▲" : " ▼") : ""));
        sizeHeader.setText("Size" + (sortColumn == 1 ? (sortAscending ? " ▲" : " ▼") : ""));
        dateHeader.setText("Date" + (sortColumn == 2 ? (sortAscending ? " ▲" : " ▼") : ""));

        nameHeader.setTextColor(sortColumn == 0 ? Color.WHITE : Color.parseColor("#888888"));
        sizeHeader.setTextColor(sortColumn == 1 ? Color.WHITE : Color.parseColor("#888888"));
        dateHeader.setTextColor(sortColumn == 2 ? Color.WHITE : Color.parseColor("#888888"));
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

        Comparator<File> dirComparator = new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        };
        Collections.sort(directories, dirComparator);

        Comparator<File> fileComparator = new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                int result = 0;
                if (sortColumn == 0) {
                    result = f1.getName().compareToIgnoreCase(f2.getName());
                } else if (sortColumn == 1) {
                    result = Long.compare(f1.length(), f2.length());
                } else if (sortColumn == 2) {
                    result = Long.compare(f1.lastModified(), f2.lastModified());
                }
                return sortAscending ? result : -result;
            }
        };
        Collections.sort(books, fileComparator);

        fileList.addAll(directories);
        fileList.addAll(books);

        ArrayAdapter<File> adapter = new ArrayAdapter<File>(this, android.R.layout.simple_list_item_1, fileList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewHolder holder;

                if (convertView == null) {
                    LinearLayout row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    int pad = (int) (16 * getResources().getDisplayMetrics().density);
                    row.setPadding(pad, pad, pad, pad);

                    holder = new ViewHolder();

                    LinearLayout nameCol = new LinearLayout(getContext());
                    nameCol.setOrientation(LinearLayout.VERTICAL);

                    holder.nameView = new TextView(getContext());
                    holder.nameView.setTextSize(20f);
                    holder.nameView.setSingleLine(true);
                    holder.nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);

                    holder.progressContainer = new LinearLayout(getContext());
                    holder.progressContainer.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (int) (4 * getResources().getDisplayMetrics().density)
                    );
                    pbParams.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
                    holder.progressContainer.setLayoutParams(pbParams);
                    holder.progressContainer.setBackgroundColor(Color.parseColor("#333333"));

                    holder.progressFill = new View(getContext());
                    holder.progressEmpty = new View(getContext());

                    holder.progressContainer.addView(holder.progressFill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f));
                    holder.progressContainer.addView(holder.progressEmpty, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100f));

                    nameCol.addView(holder.nameView);
                    nameCol.addView(holder.progressContainer);

                    holder.sizeView = new TextView(getContext());
                    holder.sizeView.setTextSize(16f);
                    holder.sizeView.setSingleLine(true);
                    holder.sizeView.setGravity(Gravity.END);

                    holder.dateView = new TextView(getContext());
                    holder.dateView.setTextSize(16f);
                    holder.dateView.setSingleLine(true);
                    holder.dateView.setGravity(Gravity.END);

                    row.addView(nameCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f));
                    row.addView(holder.sizeView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f));
                    row.addView(holder.dateView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f));

                    convertView = row;
                    convertView.setTag(holder);
                } else {
                    holder = (ViewHolder) convertView.getTag();
                }

                File file = getItem(position);

                holder.sizeView.setText("");
                holder.dateView.setText("");

                if (file.isDirectory()) {
                    holder.nameView.setText("📁 " + file.getName());
                    holder.nameView.setTextColor(Color.parseColor("#FFD54F"));
                    holder.dateView.setText(dateFormat.format(new Date(file.lastModified())));
                    holder.dateView.setTextColor(Color.parseColor("#555555"));
                    holder.progressContainer.setVisibility(View.GONE);
                } else {
                    String path = file.getAbsolutePath();
                    int page = readerPrefs.getInt(path + "_page", 0);
                    int total = readerPrefs.getInt(path + "_total", 0);

                    holder.nameView.setText(file.getName());

                    if (total > 0 && page > 0) {
                        holder.progressContainer.setVisibility(View.VISIBLE);

                        int percent = (int) (((float) (page + 1) / total) * 100);
                        if (percent > 100) percent = 100;

                        LinearLayout.LayoutParams fillParams = (LinearLayout.LayoutParams) holder.progressFill.getLayoutParams();
                        fillParams.weight = percent;
                        holder.progressFill.setLayoutParams(fillParams);

                        LinearLayout.LayoutParams emptyParams = (LinearLayout.LayoutParams) holder.progressEmpty.getLayoutParams();
                        emptyParams.weight = 100 - percent;
                        holder.progressEmpty.setLayoutParams(emptyParams);

                        if (page >= total - 1) {
                            holder.nameView.setTextColor(Color.parseColor("#5A6B5D"));
                            holder.progressFill.setBackgroundColor(Color.parseColor("#5A6B5D"));
                        } else {
                            holder.nameView.setTextColor(Color.WHITE);
                            holder.progressFill.setBackgroundColor(Color.parseColor("#64B5F6"));
                        }
                    } else {
                        holder.progressContainer.setVisibility(View.GONE);
                        holder.nameView.setTextColor(Color.WHITE);
                    }

                    holder.sizeView.setText(formatSize(file.length()));
                    holder.sizeView.setTextColor(Color.parseColor("#888888"));

                    holder.dateView.setText(dateFormat.format(new Date(file.lastModified())));
                    holder.dateView.setTextColor(Color.parseColor("#888888"));
                }

                return convertView;
            }
        };

        listView.setAdapter(adapter);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int z = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format(Locale.US, "%.1f %sB", (double)bytes / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void openReader(String path) {
        Intent intent = new Intent(FileExplorerActivity.this, ReaderActivity.class);
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

    private static class ViewHolder {
        TextView nameView;
        LinearLayout progressContainer;
        View progressFill;
        View progressEmpty;
        TextView sizeView;
        TextView dateView;
    }
}

