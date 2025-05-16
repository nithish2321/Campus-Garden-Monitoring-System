package com.example.imageuploader;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class InferenceDataActivity extends AppCompatActivity {

    private static final String TAG = "InferenceDataActivity";
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private FloatingActionButton btnRefresh;
    private InferenceAdapter adapter;
    private List<JSONObject> inferenceDataList;
    private RequestQueue queue;
    private String serverUrl;
    private int totalItems = 0;
    private int fetchedItems = 0;
    private static final int BATCH_SIZE = 10;
    private Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inference_data);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Inference Data");
        }

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        btnRefresh = findViewById(R.id.btnRefresh);
        inferenceDataList = new ArrayList<>();
        adapter = new InferenceAdapter(inferenceDataList, this::onItemClick);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        serverUrl = getIntent().getStringExtra("serverUrl");
        if (serverUrl == null || serverUrl.isEmpty()) {
            Log.e(TAG, "Server URL not provided");
            Toast.makeText(this, "Server URL not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        queue = Volley.newRequestQueue(this);
        setupSocket();
        fetchData();

        btnRefresh.setOnClickListener(v -> {
            inferenceDataList.clear();
            adapter.notifyDataSetChanged();
            fetchedItems = 0;
            fetchData();
        });
    }

    private void setupSocket() {
        try {
            socket = IO.socket(serverUrl);
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> Log.d(TAG, "Socket.IO connected"));
                }
            });
            socket.on("new_file", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> {
                        Toast.makeText(InferenceDataActivity.this, "New file uploaded", Toast.LENGTH_SHORT).show();
                        fetchData();
                    });
                }
            });
            socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    runOnUiThread(() -> Log.d(TAG, "Socket.IO disconnected"));
                }
            });
            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "Socket.IO setup error: " + e.getMessage(), e);
        }
    }

    private void fetchData() {
        fetchTotalCount();
    }

    private void fetchTotalCount() {
        String url = serverUrl.endsWith("/") ? serverUrl + "get-inference-count" : serverUrl + "/get-inference-count";
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        totalItems = response.getInt("count");
                        Log.d(TAG, "Total items: " + totalItems);
                        if (totalItems == 0) {
                            Toast.makeText(this, "No data available", Toast.LENGTH_SHORT).show();
                            progressBar.setVisibility(View.GONE);
                            return;
                        }
                        progressBar.setMax(totalItems);
                        progressBar.setProgress(0);
                        progressBar.setVisibility(View.VISIBLE);
                        fetchBatch(0);
                    } catch (Exception e) {
                        Log.e(TAG, "Count error: " + e.getMessage(), e);
                        Toast.makeText(this, "Error fetching count: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    }
                },
                error -> {
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    Log.e(TAG, "Count fetch error: " + errorMsg, error);
                    Toast.makeText(this, "Failed to fetch count: " + errorMsg, Toast.LENGTH_LONG).show();
                    progressBar.setVisibility(View.GONE);
                });
        queue.add(request);
    }

    private void fetchBatch(int startIndex) {
        if (startIndex >= totalItems) {
            progressBar.setVisibility(View.GONE);
            Log.d(TAG, "All items fetched");
            return;
        }

        int limit = Math.min(BATCH_SIZE, totalItems - startIndex);
        String url = serverUrl.endsWith("/")
                ? serverUrl + "get-inference-batch?start=" + startIndex + "&limit=" + limit
                : serverUrl + "/get-inference-batch?start=" + startIndex + "&limit=" + limit;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray items = response.getJSONArray("items");
                        int itemsFetched = items.length();
                        for (int i = 0; i < itemsFetched; i++) {
                            inferenceDataList.add(items.getJSONObject(i));
                        }
                        adapter.notifyItemRangeInserted(fetchedItems, itemsFetched);
                        fetchedItems += itemsFetched;
                        progressBar.setProgress(fetchedItems);
                        Log.d(TAG, "Fetched batch: " + itemsFetched + " items, total: " + fetchedItems);
                        fetchBatch(startIndex + itemsFetched);
                    } catch (Exception e) {
                        Log.e(TAG, "Batch parse error: " + e.getMessage(), e);
                        Toast.makeText(this, "Error parsing batch: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        fetchBatch(startIndex + BATCH_SIZE);
                    }
                },
                error -> {
                    String errorMsg = error.getMessage() != null ? error.getMessage() : "Unknown error";
                    Log.e(TAG, "Batch fetch error: " + errorMsg, error);
                    Toast.makeText(this, "Failed to fetch batch: " + errorMsg, Toast.LENGTH_LONG).show();
                    fetchBatch(startIndex + BATCH_SIZE);
                });
        queue.add(request);
    }

    private void onItemClick(JSONObject item) {
        try {
            String itemId = item.optString("_id", null);
            if (itemId == null) {
                Log.e(TAG, "No _id found in item: " + item.toString());
                Toast.makeText(this, "Invalid item data", Toast.LENGTH_SHORT).show();
                return;
            }
            Log.d(TAG, "Item clicked, _id: " + itemId);
            Intent intent = new Intent(this, ImageInferenceActivity.class);
            intent.putExtra("itemId", itemId);
            intent.putExtra("serverUrl", serverUrl);  // Pass server URL to fetch data
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting ImageInferenceActivity: " + e.getMessage(), e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}