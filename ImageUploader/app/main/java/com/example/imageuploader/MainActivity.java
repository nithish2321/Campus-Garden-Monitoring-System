package com.example.imageuploader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private EditText urlEditText;
    private FloatingActionButton btnCamera, btnGallery, btnUpload, btnViewData;
    private ImageView previewImage;
    private ProgressBar progressBar;
    private TextView locationLabel;
    private Bitmap bitmap;
    private String currentPhotoPath;
    private FusedLocationProviderClient fusedLocationClient;
    private String locationTag = "Unknown";
    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlEditText = findViewById(R.id.urlEditText);
        btnCamera = findViewById(R.id.btnCamera);
        btnGallery = findViewById(R.id.btnGallery);
        btnUpload = findViewById(R.id.btnUpload);
        btnViewData = findViewById(R.id.btnViewData);
        previewImage = findViewById(R.id.previewImage);
        progressBar = findViewById(R.id.progressBar);
        locationLabel = findViewById(R.id.locationLabel);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        checkPermissions();

        setupSocket();

        ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        File imgFile = new File(currentPhotoPath);
                        if (imgFile.exists()) {
                            bitmap = BitmapFactory.decodeFile(currentPhotoPath);
                            if (bitmap != null) {
                                previewImage.setImageBitmap(bitmap);
                                previewImage.setVisibility(View.VISIBLE);
                                btnUpload.setVisibility(View.VISIBLE);
                                getLocation();
                            } else {
                                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

        ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedImageUri = result.getData().getData();
                        try {
                            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                            if (bitmap != null) {
                                previewImage.setImageBitmap(bitmap);
                                previewImage.setVisibility(View.VISIBLE);
                                btnUpload.setVisibility(View.VISIBLE);
                                currentPhotoPath = saveBitmapToFile(bitmap);
                                getLocation();
                            }
                        } catch (IOException e) {
                            Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        btnCamera.setOnClickListener(v -> {
            try {
                File imageFile = createImageFile();
                Uri imageUri = FileProvider.getUriForFile(this, "com.example.imageuploader.fileprovider", imageFile);
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                cameraLauncher.launch(cameraIntent);
            } catch (IOException e) {
                Toast.makeText(this, "Error capturing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        btnGallery.setOnClickListener(v -> {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(galleryIntent);
        });

        btnUpload.setOnClickListener(v -> {
            if (bitmap != null && currentPhotoPath != null) {
                uploadImage();
            } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            }
        });

        btnViewData.setOnClickListener(v -> {
            Intent intent = new Intent(this, InferenceDataActivity.class);
            intent.putExtra("serverUrl", urlEditText.getText().toString().trim());
            startActivity(intent);
        });
    }

    private void setupSocket() {
        try {
            String serverUrl = urlEditText.getText().toString().trim();
            socket = IO.socket(serverUrl);
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to server", Toast.LENGTH_SHORT).show());
                }
            });
            socket.on("new_file", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "New file uploaded", Toast.LENGTH_SHORT).show());
                }
            });
            socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Disconnected from server", Toast.LENGTH_SHORT).show());
                }
            });
            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "Socket.IO setup error: " + e.getMessage(), e);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private String saveBitmapToFile(Bitmap bitmap) throws IOException {
        Bitmap compressedBitmap = compressBitmap(bitmap, 800, 70);
        File file = createImageFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos);
            fos.flush();
        }
        return file.getAbsolutePath();
    }

    private Bitmap compressBitmap(Bitmap original, int maxSize, int quality) {
        int width = original.getWidth();
        int height = original.getHeight();
        float scale = Math.min((float) maxSize / width, (float) maxSize / height);
        if (scale >= 1) return original;
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private void getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    try {
                        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                        if (!addresses.isEmpty()) {
                            locationTag = addresses.get(0).getLocality() != null ? addresses.get(0).getLocality() : "Unknown";
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Geocoder failed: " + e.getMessage());
                    }
                    locationLabel.setText("Location: " + locationTag);
                    locationLabel.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void uploadImage() {
        String baseUrl = urlEditText.getText().toString().trim();
        String uploadUrl = baseUrl.endsWith("/") ? baseUrl + "upload" : baseUrl + "/upload";
        if (uploadUrl.isEmpty()) {
            Toast.makeText(this, "Please enter the server URL", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        File file = new File(currentPhotoPath);

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        VolleyMultipartRequest multipartRequest = new VolleyMultipartRequest(Request.Method.POST, uploadUrl, file, locationTag,
                response -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        String responseStr = new String(response.data, StandardCharsets.UTF_8);
                        JSONObject jsonResponse = new JSONObject(responseStr);
                        String message = jsonResponse.getString("message");
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                        previewImage.setVisibility(View.GONE);
                        btnUpload.setVisibility(View.GONE);
                        locationLabel.setVisibility(View.GONE);
                        bitmap = null;
                        currentPhotoPath = null;
                    } catch (Exception e) {
                        Toast.makeText(this, "Error parsing response: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Response error: " + e.getMessage(), e);
                    }
                },
                error -> {
                    progressBar.setVisibility(View.GONE);
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    Toast.makeText(this, "Upload failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Upload error: " + errorMsg, error);
                });

        multipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                60000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(multipartRequest);
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        if (!allPermissionsGranted(permissions)) {
            requestPermissions(permissions, 1);
        }
    }

    private boolean allPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }
    }
}

class VolleyMultipartRequest extends Request<NetworkResponse> {
    private final Response.Listener<NetworkResponse> mListener;
    private final Response.ErrorListener mErrorListener;
    private final File mFile;
    private final String mLocationTag;
    private static final String LINE_END = "\r\n";
    private static final String TWO_HYPHENS = "--";
    private final String boundary = "apiclient-" + UUID.randomUUID().toString();

    public VolleyMultipartRequest(int method, String url, File file, String locationTag, Response.Listener<NetworkResponse> listener, Response.ErrorListener errorListener) {
        super(method, url, errorListener);
        this.mListener = listener;
        this.mErrorListener = errorListener;
        this.mFile = file;
        this.mLocationTag = locationTag;
    }

    @Override
    protected void deliverResponse(NetworkResponse response) {
        mListener.onResponse(response);
    }

    @Override
    public void deliverError(VolleyError error) {
        mErrorListener.onErrorResponse(error);
    }

    @Override
    public String getBodyContentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    @Override
    public byte[] getBody() throws com.android.volley.AuthFailureError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] fileData = getFileDataFromPath(mFile);
            if (fileData == null) {
                throw new com.android.volley.AuthFailureError("No file data available");
            }

            String fileHeader = TWO_HYPHENS + boundary + LINE_END +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"" + LINE_END +
                    "Content-Type: image/jpeg" + LINE_END +
                    LINE_END;
            bos.write(fileHeader.getBytes(StandardCharsets.UTF_8));
            bos.write(fileData);
            bos.write(LINE_END.getBytes(StandardCharsets.UTF_8));

            String locationHeader = TWO_HYPHENS + boundary + LINE_END +
                    "Content-Disposition: form-data; name=\"location\"" + LINE_END +
                    "Content-Type: text/plain" + LINE_END +
                    LINE_END;
            bos.write(locationHeader.getBytes(StandardCharsets.UTF_8));
            bos.write(mLocationTag.getBytes(StandardCharsets.UTF_8));
            bos.write(LINE_END.getBytes(StandardCharsets.UTF_8));

            String footer = TWO_HYPHENS + boundary + TWO_HYPHENS + LINE_END;
            bos.write(footer.getBytes(StandardCharsets.UTF_8));

            return bos.toByteArray();
        } catch (IOException e) {
            Log.e("VolleyMultipartRequest", "Error building body: " + e.getMessage(), e);
            throw new com.android.volley.AuthFailureError("Failed to build request body");
        }
    }

    @Override
    protected Response<NetworkResponse> parseNetworkResponse(NetworkResponse response) {
        return Response.success(response, HttpHeaderParser.parseCacheHeaders(response));
    }

    private byte[] getFileDataFromPath(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }
}