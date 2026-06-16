package com.guardianapp.mobile.ui.protecteduser;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.ui.security.SecurityAnalysisItem;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProtectedSmsInboxAdapter extends RecyclerView.Adapter<ProtectedSmsInboxAdapter.ViewHolder> {

    private final List<SecurityAnalysisItem> items = new ArrayList<>();

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
                .inflate(R.layout.item_protected_sms_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SecurityAnalysisItem item = items.get(position);
        holder.tvSender.setText(safe(item.getSender()));
        holder.tvMessage.setText(safe(item.getMessage()));

        String url = item.getUrl();
        if (url == null || url.isBlank()) {
            holder.tvUrl.setVisibility(View.GONE);
        } else {
            holder.tvUrl.setVisibility(View.VISIBLE);
            holder.tvUrl.setText(url);
        }

        holder.tvTag.setText(resolveTag(item));
        holder.tvTag.setBackgroundResource(resolveTagBackground(item));
        holder.tvState.setText(resolveStateText(item));
        holder.tvReceived.setText("Recibido " + formatReceived(item.getTimestampMillis()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String resolveTag(SecurityAnalysisItem item) {
        if (item == null) {
            return "SMS";
        }
        if (item.isWhitelisted()) {
            return "LISTA BLANCA";
        }
        if (item.isBlocked()) {
            return "SOSPECHOSO";
        }
        String status = item.getStatus();
        if (status == null || status.isBlank() || "NO_URL".equalsIgnoreCase(status)) {
            return "RECIBIDO";
        }
        return status.toUpperCase(Locale.ROOT);
    }

    private int resolveTagBackground(SecurityAnalysisItem item) {
        if (item == null) {
            return R.drawable.bg_chip_neutral;
        }
        if (item.isWhitelisted()) {
            return R.drawable.bg_chip_safe;
        }
        if (item.isBlocked()) {
            return R.drawable.bg_chip_danger;
        }
        return R.drawable.bg_chip_neutral;
    }

    private String resolveStateText(SecurityAnalysisItem item) {
        if (item == null) {
            return "Sin estado";
        }
        if (item.isBlocked()) {
            return "Bloqueado por seguridad";
        }
        if (item.isWhitelisted()) {
            return "Validado por lista blanca";
        }
        return "Disponible en tu bandeja";
    }

    private String formatReceived(long timestampMillis) {
        return DateFormat.format("dd MMM, hh:mm a", new Date(timestampMillis)).toString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Sin contenido" : value;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSender;
        final TextView tvTag;
        final TextView tvMessage;
        final TextView tvUrl;
        final TextView tvState;
        final TextView tvReceived;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvProtectedSmsSender);
            tvTag = itemView.findViewById(R.id.tvProtectedSmsTag);
            tvMessage = itemView.findViewById(R.id.tvProtectedSmsMessage);
            tvUrl = itemView.findViewById(R.id.tvProtectedSmsUrl);
            tvState = itemView.findViewById(R.id.tvProtectedSmsState);
            tvReceived = itemView.findViewById(R.id.tvProtectedSmsReceived);
        }
    }
}
