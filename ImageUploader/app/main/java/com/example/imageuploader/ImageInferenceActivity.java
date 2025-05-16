package com.example.imageuploader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

public class ImageInferenceActivity extends AppCompatActivity {

    private static final String TAG = "ImageInferenceActivity";
    private ImageView imageView;
    private TextView inferenceText;
    private ProgressBar progressBar;
    private RequestQueue queue;
    private String serverUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_inference);

        imageView = findViewById(R.id.imageView);
        inferenceText = findViewById(R.id.inferenceText);
        progressBar = findViewById(R.id.progressBar);

        String itemId = getIntent().getStringExtra("itemId");
        serverUrl = getIntent().getStringExtra("serverUrl");

        if (itemId == null || serverUrl == null || serverUrl.isEmpty()) {
            Log.e(TAG, "Missing itemId or serverUrl");
            Toast.makeText(this, "Missing required data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        queue = Volley.newRequestQueue(this);
        fetchItemData(itemId);
    }

    private void fetchItemData(String itemId) {
        progressBar.setVisibility(View.VISIBLE);
        String url = serverUrl.endsWith("/") ? serverUrl + "get-inference-item?id=" + itemId : serverUrl + "/get-inference-item?id=" + itemId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONObject inferenceData = response.getJSONObject("item");
                        Log.d(TAG, "Received item data: " + inferenceData.toString());

                        // Display inference data as text
                        StringBuilder displayText = new StringBuilder();
                        displayText.append("Location: ").append(inferenceData.optString("location", "Unknown")).append("\n");
                        displayText.append("Detected Objects: ").append(inferenceData.optString("detected_objects", "None")).append("\n");
                        displayText.append("Summary: ").append(inferenceData.optString("summary", "None"));
                        inferenceText.setText(displayText.toString());
                        Log.d(TAG, "Text set: " + displayText.toString());

                        // Load the processed image if available, otherwise the original image
                        String base64Image = inferenceData.optString("image", "");
                        if (base64Image.isEmpty()) {
                            base64Image = inferenceData.optString("imageData", "");  // Fallback to original image
                        }

                        if (!base64Image.isEmpty()) {
                            loadImageFromBase64(base64Image);
                        } else {
                            Log.w(TAG, "No image data found in inference");
                            Toast.makeText(this, "No image data in inference", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Parse error: " + e.getMessage(), e);
                        Toast.makeText(this, "Error parsing data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } finally {
                        progressBar.setVisibility(View.GONE);
                    }
                },
                error -> {
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    Log.e(TAG, "Fetch error: " + errorMsg, error);
                    Toast.makeText(this, "Failed to fetch data: " + errorMsg, Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                });
        queue.add(request);
    }

    private void loadImageFromBase64(String base64Image) {
        try {
            Log.d(TAG, "Decoding Base64 image of length: " + base64Image.length());
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Log.d(TAG, "Decoded bytes length: " + decodedBytes.length);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                Log.d(TAG, "Image loaded successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            } else {
                Log.w(TAG, "Bitmap is null - failed to decode image");
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Image decode error: " + e.getMessage(), e);
            Toast.makeText(this, "Error decoding image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}