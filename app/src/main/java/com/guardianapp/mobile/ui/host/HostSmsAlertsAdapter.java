package com.guardianapp.mobile.ui.host;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.SmsThreatAlertResponse;

import java.util.ArrayList;
import java.util.List;

public class HostSmsAlertsAdapter extends RecyclerView.Adapter<HostSmsAlertsAdapter.ViewHolder> {

    private final List<SmsThreatAlertResponse> items = new ArrayList<>();

    public void setItems(List<SmsThreatAlertResponse> newItems) {
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
                .inflate(R.layout.item_host_sms_alert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsThreatAlertResponse item = items.get(position);
        holder.tvSender.setText(safe(item.getSender()));
        holder.tvTag.setText(formatTag(item));
        holder.tvMessage.setText(safe(item.getMessageExcerpt()));
        String url = item.getDetectedUrl();
        holder.tvUrl.setVisibility(url == null || url.isBlank() ? View.GONE : View.VISIBLE);
        holder.tvUrl.setText(url == null ? "" : url);
        holder.tvState.setText(resolveState(item));
        holder.tvCreatedAt.setText("Recibido " + formatCreatedAt(item.getCreatedAt()));
        String reason = item.getAnalysisReason();
        holder.tvReason.setVisibility(reason == null || reason.isBlank() ? View.GONE : View.VISIBLE);
        holder.tvReason.setText("Motivo: " + (reason == null ? "" : reason));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Sin dato" : value;
    }

    private String formatTag(SmsThreatAlertResponse item) {
        if (item == null) {
            return "SMS";
        }
        String lifecycle = normalize(item.getStatus());
        String analysis = normalize(item.getAnalysisStatus());
        if (lifecycle.contains("BLOCKED") || !item.isUrlAllowed()) {
            return "BLOQUEADO";
        }
        if ("PHISHING".equals(analysis) || "MALWARE".equals(analysis) || "SUSPICIOUS".equals(analysis) || "ERROR".equals(analysis)) {
            return analysis;
        }
        return "RECIBIDO";
    }

    private String formatCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return "sin fecha";
        }
        String normalized = createdAt.replace('T', ' ');
        int dotIndex = normalized.indexOf('.');
        return dotIndex > 0 ? normalized.substring(0, dotIndex) : normalized;
    }

    private String resolveState(SmsThreatAlertResponse item) {
        String lifecycle = normalize(item == null ? null : item.getStatus());
        String analysis = normalize(item == null ? null : item.getAnalysisStatus());
        if (lifecycle.contains("BLOCKED") || (item != null && !item.isUrlAllowed())) {
            return "Bloqueado por seguridad";
        }
        if ("PHISHING".equals(analysis) || "MALWARE".equals(analysis) || "SUSPICIOUS".equals(analysis) || "ERROR".equals(analysis)) {
            return "Mensaje sospechoso detectado";
        }
        return "Sin bloqueo registrado";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSender;
        final TextView tvTag;
        final TextView tvCreatedAt;
        final TextView tvMessage;
        final TextView tvUrl;
        final TextView tvState;
        final TextView tvReason;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvHostSmsSender);
            tvTag = itemView.findViewById(R.id.tvHostSmsTag);
            tvCreatedAt = itemView.findViewById(R.id.tvHostSmsCreatedAt);
            tvMessage = itemView.findViewById(R.id.tvHostSmsMessage);
            tvUrl = itemView.findViewById(R.id.tvHostSmsUrl);
            tvState = itemView.findViewById(R.id.tvHostSmsState);
            tvReason = itemView.findViewById(R.id.tvHostSmsReason);
        }
    }
}
