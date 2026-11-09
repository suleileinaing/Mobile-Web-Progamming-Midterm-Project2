package com.cookandroid.photoviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.view.View;
import androidx.appcompat.app.AlertDialog;
import android.net.Uri;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import android.widget.EditText;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import androidx.recyclerview.widget.GridLayoutManager;

public class MainActivity extends AppCompatActivity {

    private final String siteUrl = "https://sulei2707.pythonanywhere.com";;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private TextView textView;
    private Button loadButton;
    private RecyclerView recyclerView;

    private Uri selectedImageUri;
    private ImageView imagePreview;
    private Button removeImageButton;
    private TextView selectedImageText;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null && imagePreview != null) {
                            selectedImageUri = uri;
                            imagePreview.setImageURI(uri);
                            imagePreview.setVisibility(View.VISIBLE);
                            removeImageButton.setVisibility(View.VISIBLE);
                            selectedImageText.setText(
                                    "사진이 선택되었습니다."
                            );
                        }
                    }
            );
    private final List<PhotoPost> allPosts = new ArrayList<>();
    private boolean showSavedOnly = false;
    private boolean latestFirst = true;

    private long currentUserId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    v.setPadding(
                            bars.left, bars.top,
                            bars.right, bars.bottom
                    );
                    return insets;
                }
        );

        textView = findViewById(R.id.textView);
        loadButton = findViewById(R.id.btn_load);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(
                new GridLayoutManager(this, 2)
        );

        findViewById(R.id.btnAllPosts).setOnClickListener(v -> {
            showSavedOnly = false;
            displayPosts();
        });

        findViewById(R.id.btnSavedPosts).setOnClickListener(v -> {
            showSavedOnly = true;
            displayPosts();
        });

        loadButton.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= 37
                    && checkSelfPermission(
                    "android.permission.ACCESS_LOCAL_NETWORK"
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                "android.permission.ACCESS_LOCAL_NETWORK"
                        },
                        100
                );
                return;
            }

            downloadImages();
        });

        findViewById(R.id.btn_save).setOnClickListener(
                v -> showUploadDialog()
        );

        Spinner sortSpinner = findViewById(R.id.spinnerSort);

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                this,
                R.layout.item_sort_selected,
                new String[]{"최신순", "오래된순"}
        );

        sortAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        sortSpinner.setAdapter(sortAdapter);

        sortSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        latestFirst = position == 0;
                        displayPosts();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );
    }
    private void writeTextField(
            DataOutputStream output,
            String boundary,
            String name,
            String value
    ) throws IOException {

        String field = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\""
                + name + "\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + value + "\r\n";

        output.write(field.getBytes(StandardCharsets.UTF_8));
    }

    private void uploadPost(String title, String text, Uri imageUri) {
        String token = BuildConfig.PHOTO_API_TOKEN.trim();

        if (token.isEmpty()) {
            Toast.makeText(
                    this,
                    "Input token in local.properties",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        textView.setText("업로드 중...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String boundary = "PhotoViewer-" + UUID.randomUUID();

                connection = (HttpURLConnection) new URL(
                        siteUrl + "/api_root/Post/"
                ).openConnection();

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);

                connection.setRequestProperty(
                        "Authorization", "Token " + token
                );
                connection.setRequestProperty(
                        "Accept", "application/json"
                );
                connection.setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary=" + boundary
                );

                String mimeType =
                        getContentResolver().getType(imageUri);

                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }

                try (InputStream imageStream =
                             getContentResolver().openInputStream(imageUri)) {

                    if (imageStream == null) {
                        throw new IOException("Cannot open selected image");
                    }

                    try (DataOutputStream output =
                                 new DataOutputStream(
                                         connection.getOutputStream()
                                 )) {

                        writeTextField(output, boundary, "title", title);
                        writeTextField(output, boundary, "text", text);

                        String extension = android.webkit.MimeTypeMap
                                .getSingleton()
                                .getExtensionFromMimeType(mimeType);

                        if (extension == null) {
                            throw new IOException(
                                    "Cannot identify image format. Please choose JPG or PNG."
                            );
                        }

                        String fileName = "upload." + extension;

                        String imageHeader = "--" + boundary + "\r\n"
                                + "Content-Disposition: form-data; "
                                + "name=\"image\"; filename=\"" + fileName + "\"\r\n"
                                + "Content-Type: " + mimeType + "\r\n\r\n";
                        output.write(
                                imageHeader.getBytes(StandardCharsets.UTF_8)
                        );

                        byte[] buffer = new byte[8192];
                        int count;

                        while ((count = imageStream.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }

                        output.writeBytes(
                                "\r\n--" + boundary + "--\r\n"
                        );
                        output.flush();
                    }
                }

                int responseCode = connection.getResponseCode();

                if (responseCode != 201) {
                    StringBuilder details = new StringBuilder();

                    InputStream errorStream = connection.getErrorStream();

                    if (errorStream != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(errorStream, "UTF-8")
                        )) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                details.append(line);
                            }
                        }
                    }

                    throw new IOException(
                            "Upload HTTP " + responseCode + ": " + details
                    );
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            "게시 완료!",
                            Toast.LENGTH_SHORT
                    ).show();

                    downloadImages();
                });

            } catch (Exception error) {
                android.util.Log.e(
                        "PhotoViewerNetwork", "Upload failed", error
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    textView.setText(
                            "업로드 실패: " + error.getMessage()
                    );
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
    private void showUploadDialog() {
        selectedImageUri = null;

        View form = getLayoutInflater().inflate(
                R.layout.dialog_upload,
                null
        );

        EditText titleInput = form.findViewById(R.id.editTitle);
        EditText textInput = form.findViewById(R.id.editText);

        imagePreview = form.findViewById(R.id.imagePreview);
        selectedImageText = form.findViewById(
                R.id.textSelectedImage
        );

        removeImageButton = form.findViewById(
                R.id.btnRemoveImage
        );

        removeImageButton.setOnClickListener(v -> {
            selectedImageUri = null;

            imagePreview.setImageDrawable(null);
            imagePreview.setVisibility(View.GONE);

            selectedImageText.setText(
                    "선택한 사진이 없습니다."
            );

            removeImageButton.setVisibility(View.GONE);
        });

        form.findViewById(R.id.btnChooseImage)
                .setOnClickListener(v ->
                        imagePicker.launch("image/*")
                );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("새로운 이미지 게시")
                .setView(form)
                .setNegativeButton("취소", null)
                .setPositiveButton("게시", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String title =
                                    titleInput.getText().toString().trim();
                            String text =
                                    textInput.getText().toString().trim();

                            if (title.isEmpty()) {
                                titleInput.setError("제목을 입력해 주세요.");
                                return;
                            }

                            if (text.isEmpty()) {
                                textInput.setError("내용을 입력해 주세요.");
                                return;
                            }

                            if (selectedImageUri == null) {
                                Toast.makeText(
                                        this,
                                        "사진을 선택해 주세요.",
                                        Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }

                            uploadPost(title, text, selectedImageUri);
                            dialog.dismiss();
                        })
        );

        dialog.show();
        int buttonColor = android.graphics.Color.parseColor("#000000");

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(buttonColor);

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(buttonColor);
    }

    private HttpURLConnection connect(String address)
            throws IOException {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        return connection;
    }

    private void downloadImages() {
        loadButton.setEnabled(false);
        textView.setText("동기화 중...");

        executor.execute(() -> {
            List<PhotoPost> images = new ArrayList<>();

            try {
                final long loadedUserId = fetchCurrentUserId();
                StringBuilder json = new StringBuilder();

                HttpURLConnection api =
                        connect(siteUrl + "/api_root/Post/");

                api.setRequestProperty("Accept", "application/json");

                try {
                    if (api.getResponseCode() != 200) {
                        throw new IOException(
                                "API HTTP " + api.getResponseCode()
                        );
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    api.getInputStream(), "UTF-8"
                            ))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            json.append(line);
                        }
                    }
                } finally {
                    api.disconnect();
                }

                JSONArray posts = new JSONArray(json.toString());

                for (int i = 0; i < posts.length(); i++) {
                    JSONObject post = posts.getJSONObject(i);
                    String imageUrl = post.optString("image", "");

                    if (imageUrl.isEmpty()
                            || imageUrl.equals("null")) {
                        continue;
                    }

                    HttpURLConnection imageConnection =
                            connect(imageUrl);

                    try {
                        if (imageConnection.getResponseCode() != 200) {
                            throw new IOException(
                                    "Image HTTP "
                                            + imageConnection.getResponseCode()
                            );
                        }

                        try (InputStream stream =
                                     imageConnection.getInputStream()) {

                            Bitmap bitmap =
                                    BitmapFactory.decodeStream(stream);

                            if (bitmap == null) {
                                throw new IOException(
                                        "Cannot decode image"
                                );
                            }

                            images.add(new PhotoPost(
                                    post.getLong("id"),
                                    post.getLong("author"),
                                    post.optString("title", ""),
                                    post.optString("text", ""),
                                    post.optString("published_date", ""),
                                    imageUrl,
                                    bitmap
                            ));
                        }
                    } finally {
                        imageConnection.disconnect();
                    }
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    currentUserId = loadedUserId;

                    allPosts.clear();
                    allPosts.addAll(images);
                    displayPosts();

                    loadButton.setEnabled(true);

                    loadButton.setEnabled(true);
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    textView.setText(
                            "동기화 실패: " + error.getMessage()
                    );
                    loadButton.setEnabled(true);
                });
                android.util.Log.e("PhotoViewerNetwork", "Sync failed", error);
            }
        });
    }

    private void displayPosts() {
        android.content.SharedPreferences savedPosts =
                getSharedPreferences(
                        "saved_posts",
                        MODE_PRIVATE
                );

        List<PhotoPost> visiblePosts = new ArrayList<>();

        for (PhotoPost post : allPosts) {
            boolean isSaved = savedPosts.getBoolean(
                    "post_" + post.id,
                    false
            );

            if (!showSavedOnly || isSaved) {
                visiblePosts.add(post);
            }
        }

        visiblePosts.sort((first, second) -> {
            int result = postTime(first).compareTo(postTime(second));

            if (result == 0) {
                result = Long.compare(first.id, second.id);
            }

            return latestFirst ? -result : result;
        });

        recyclerView.setAdapter(
                new ImageAdapter(
                        visiblePosts,
                        this::displayPosts,
                        currentUserId,
                        this::deletePost
                )
        );

        if (showSavedOnly) {
            textView.setText(
                    visiblePosts.isEmpty()
                            ? "저장한 게시물이 없습니다."
                            : "저장한 게시물: "
                            + visiblePosts.size() + "개"
            );
        } else {
            textView.setText(
                    visiblePosts.isEmpty()
                            ? "게시물이 없습니다."
                            : "전체 게시물: "
                            + visiblePosts.size() + "개"
            );
        }
    }

    private Instant postTime(PhotoPost post) {
        try {
            return OffsetDateTime.parse(
                    post.publishedDate
            ).toInstant();
        } catch (DateTimeParseException error) {
            return Instant.MIN;
        }
    }

    private long fetchCurrentUserId() throws Exception {
        String token = BuildConfig.PHOTO_API_TOKEN.trim();

        if (token.isEmpty()) {
            return -1;
        }

        HttpURLConnection connection =
                connect(siteUrl + "/api_root/me/");

        connection.setRequestProperty(
                "Authorization",
                "Token " + token
        );
        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        try {
            int code = connection.getResponseCode();

            if (code == 401 || code == 403) {
                return -1;
            }

            if (code != 200) {
                throw new IOException("User API HTTP " + code);
            }

            StringBuilder result = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            "UTF-8"
                    )
            )) {
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            }

            JSONObject user = new JSONObject(result.toString());
            return user.getLong("id");

        } finally {
            connection.disconnect();
        }
    }

    private void deletePost(PhotoPost post) {
        String token = BuildConfig.PHOTO_API_TOKEN.trim();

        if (token.isEmpty() || post.authorId != currentUserId) {
            Toast.makeText(
                    this,
                    "삭제 권한이 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        loadButton.setEnabled(false);
        textView.setText("삭제 중...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection) new URL(
                        siteUrl + "/api_root/Post/" + post.id + "/"
                ).openConnection();

                connection.setRequestMethod("DELETE");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty(
                        "Authorization",
                        "Token " + token
                );

                int code = connection.getResponseCode();

                if (code != 204) {
                    throw new IOException("Delete HTTP " + code);
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    allPosts.removeIf(item -> item.id == post.id);

                    getSharedPreferences("saved_posts", MODE_PRIVATE)
                            .edit()
                            .remove("post_" + post.id)
                            .apply();

                    displayPosts();
                    loadButton.setEnabled(true);

                    Toast.makeText(
                            this,
                            "게시물이 삭제되었습니다.",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    textView.setText(
                            "삭제 실패: " + error.getMessage()
                    );
                    loadButton.setEnabled(true);
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}