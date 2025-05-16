package com.example.imageuploader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;
import java.util.function.Consumer;

public class InferenceAdapter extends RecyclerView.Adapter<InferenceAdapter.ViewHolder> {

    private final List<JSONObject> dataList;
    private final Consumer<JSONObject> onItemClick;

    public InferenceAdapter(List<JSONObject> dataList, Consumer<JSONObject> onItemClick) {
        this.dataList = dataList;
        this.onItemClick = onItemClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = dataList.get(position);
        String location = item.optString("location", "Unknown Location");
        String uploadTime = item.optString("upload_time", "Unknown Time");
        holder.text1.setText("Location: " + location);
        holder.text2.setText("Uploaded: " + uploadTime);
        holder.itemView.setOnClickListener(v -> onItemClick.accept(item));
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;

        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }
    }
}