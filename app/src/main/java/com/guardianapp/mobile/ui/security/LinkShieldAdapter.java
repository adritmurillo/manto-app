package com.guardianapp.mobile.ui.security;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LinkShieldAdapter extends RecyclerView.Adapter<LinkShieldAdapter.ViewHolder> {

    private final List<SecurityAnalysisItem> items = new ArrayList<>();
    private final SimpleDateFormat dtFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    private OnUnblockClickListener unblockClickListener;

    public interface OnUnblockClickListener {
        void onUnblock(SecurityAnalysisItem item, int position);
    }

    public void setOnUnblockClickListener(OnUnblockClickListener listener) {
        this.unblockClickListener = listener;
    }

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
                .inflate(R.layout.item_link_shield, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SecurityAnalysisItem item = items.get(position);
        holder.tvUrl.setText(item.getUrl() == null ? "(sin URL)" : item.getUrl());
        holder.tvMeta.setText(dtFormat.format(new Date(item.getTimestampMillis())));

        if (item.isWhitelisted()) {
            holder.tvStatus.setText("SAFE");
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_safe);
            holder.btnAction.setText("Confiable");
            holder.btnAction.setEnabled(false);
            holder.btnAction.setOnClickListener(null);
        } else if (item.isBlocked()) {
            holder.tvStatus.setText(item.getStatus());
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_danger);
            holder.btnAction.setText("Desbloquear");
            holder.btnAction.setEnabled(true);
            holder.btnAction.setOnClickListener(v -> {
                if (unblockClickListener != null) {
                    int adapterPosition = holder.getAdapterPosition();
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        unblockClickListener.onUnblock(item, adapterPosition);
                    }
                }
            });
        } else {
            holder.tvStatus.setText(item.getStatus());
            holder.tvStatus.setBackgroundResource(R.drawable.bg_chip_neutral);
            holder.btnAction.setText("Revisado");
            holder.btnAction.setEnabled(false);
            holder.btnAction.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUrl;
        TextView tvStatus;
        TextView tvMeta;
        Button btnAction;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUrl = itemView.findViewById(R.id.tvShieldUrl);
            tvStatus = itemView.findViewById(R.id.tvShieldStatus);
            tvMeta = itemView.findViewById(R.id.tvShieldMeta);
            btnAction = itemView.findViewById(R.id.btnShieldAction);
        }
    }
}
