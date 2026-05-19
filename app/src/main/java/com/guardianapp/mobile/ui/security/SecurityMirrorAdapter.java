package com.guardianapp.mobile.ui.security;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SecurityMirrorAdapter extends RecyclerView.Adapter<SecurityMirrorAdapter.ViewHolder> {

    private final List<SecurityAnalysisItem> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());

    public void setItems(List<SecurityAnalysisItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_security_mirror_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SecurityAnalysisItem item = items.get(position);
        holder.tvSender.setText(item.getSender());
        holder.tvMessage.setText(item.getMessage());
        holder.tvReceived.setText("Recibido " + timeFormat.format(new Date(item.getTimestampMillis())));

        if (item.isWhitelisted()) {
            holder.tvTag.setText("LISTA BLANCA");
            holder.tvTag.setBackgroundResource(R.drawable.bg_chip_safe);
        } else if (item.isBlocked()) {
            holder.tvTag.setText("PHISHING");
            holder.tvTag.setBackgroundResource(R.drawable.bg_chip_danger);
        } else {
            holder.tvTag.setText(item.getStatus());
            holder.tvTag.setBackgroundResource(R.drawable.bg_chip_neutral);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender;
        TextView tvMessage;
        TextView tvReceived;
        TextView tvTag;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvMirrorSender);
            tvMessage = itemView.findViewById(R.id.tvMirrorMessage);
            tvReceived = itemView.findViewById(R.id.tvMirrorReceived);
            tvTag = itemView.findViewById(R.id.tvMirrorTag);
        }
    }
}
