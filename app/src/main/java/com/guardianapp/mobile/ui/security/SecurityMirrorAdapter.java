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
        holder.tvUrl.setVisibility(isBlank(item.getUrl()) ? View.GONE : View.VISIBLE);
        holder.tvUrl.setText(isBlank(item.getUrl()) ? "" : "URL: " + item.getUrl());
        holder.tvReason.setText(item.getReason() == null || item.getReason().isBlank()
                ? "Sin observaciones adicionales."
                : item.getReason());
        holder.tvState.setText(resolveStateText(item));
        holder.tvState.setTextColor(item.isInQuarantine() ? 0xFF9E373A : 0xFF1D736B);

        if (item.isWhitelisted()) {
            holder.tvTag.setText("LISTA BLANCA");
            holder.tvTag.setBackgroundResource(R.drawable.bg_chip_safe);
        } else if (item.isInQuarantine()) {
            holder.tvTag.setText("CUARENTENA");
            holder.tvTag.setBackgroundResource(R.drawable.bg_chip_danger);
        } else if ("NO_URL".equals(normalize(item.getStatus()))) {
            holder.tvTag.setText("SIN URL");
            holder.tvTag.setBackgroundResource(R.drawable.bg_chip_neutral);
        } else {
            holder.tvTag.setText(normalize(item.getStatus()));
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
        TextView tvUrl;
        TextView tvReason;
        TextView tvState;
        TextView tvReceived;
        TextView tvTag;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvMirrorSender);
            tvMessage = itemView.findViewById(R.id.tvMirrorMessage);
            tvUrl = itemView.findViewById(R.id.tvMirrorUrl);
            tvReason = itemView.findViewById(R.id.tvMirrorReason);
            tvState = itemView.findViewById(R.id.tvMirrorState);
            tvReceived = itemView.findViewById(R.id.tvMirrorReceived);
            tvTag = itemView.findViewById(R.id.tvMirrorTag);
        }
    }

    private String resolveStateText(SecurityAnalysisItem item) {
        if (item == null) {
            return "Sin estado";
        }
        String reviewState = normalize(item.getReviewState());
        if (SecurityAnalysisItem.REVIEW_PENDING_HOST.equals(reviewState)) {
            return "En cuarentena - pendiente de revision del anfitrion";
        }
        if (SecurityAnalysisItem.REVIEW_LOCAL_BLOCKED.equals(reviewState)) {
            return "En cuarentena local por riesgo detectado";
        }
        if (SecurityAnalysisItem.REVIEW_HOST_ALLOWED.equals(reviewState)) {
            return "Disponible en recibidos";
        }
        if (SecurityAnalysisItem.REVIEW_HOST_BLOCKED.equals(reviewState)) {
            return "Bloqueado por el anfitrion";
        }
        if ("NO_URL".equals(normalize(item.getStatus()))) {
            return "Disponible en recibidos - sin enlaces detectados";
        }
        return item.isInInbox()
                ? "Disponible en recibidos"
                : "En cuarentena";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
