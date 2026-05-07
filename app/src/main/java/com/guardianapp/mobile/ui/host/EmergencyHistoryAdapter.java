package com.guardianapp.mobile.ui.host;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;
import com.guardianapp.mobile.data.api.EmergencyAlertResponse;
import com.guardianapp.mobile.data.api.EmergencyAudioRecordingResponse;
import com.guardianapp.mobile.data.api.RetrofitClient;
import com.guardianapp.mobile.data.api.UserResponse;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EmergencyHistoryAdapter extends RecyclerView.Adapter<EmergencyHistoryAdapter.ViewHolder> {


    private final Context context;
    private final String requesterId;
    private final SimpleDateFormat apiDateTimeParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat displayDateTimeFormatter = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    private final SimpleDateFormat displayTimeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Map<String, String> userNameCache = new HashMap<>();
    private final List<EmergencyAlertResponse> items = new ArrayList<>();
    private final Map<String, List<EmergencyAudioRecordingResponse>> audioCache = new HashMap<>();

    public EmergencyHistoryAdapter(Context context, String requesterId) {
        this.context = context;
        this.requesterId = requesterId;
    }

    public void setItems(List<EmergencyAlertResponse> alerts) {
        items.clear();
        if (alerts != null) {
            items.addAll(alerts);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_emergency_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyAlertResponse alert = items.get(position);
        String status = alert.getStatus() != null ? alert.getStatus() : "DESCONOCIDA";
        String resolution = alert.getResolutionType() != null ? " | " + alert.getResolutionType() : "";
        String createdLabel = formatCreatedAt(alert.getCreatedAt());
        holder.tvTitle.setText("Emergencia " + status + resolution);
        holder.tvTimestamp.setText(createdLabel);
        holder.tvLocation.setText("Lat: " + alert.getLatitude() + " | Lon: " + alert.getLongitude());
        holder.tvProtectedName.setText(resolveProtectedName(alert.getProtectedUserId(), holder));
        holder.tvSummary.setText(buildSummary(alert, holder));
        holder.btnPlayAudio.setEnabled(false);
        holder.btnPlayAudio.setText("Cargando audio...");

        loadAudioHistory(alert.getId(), holder);

        holder.btnPlayAudio.setOnClickListener(v -> {
            List<EmergencyAudioRecordingResponse> audioList = audioCache.get(alert.getId());
            if (audioList == null || audioList.isEmpty()) {
                return;
            }
            EmergencyAudioRecordingResponse recording = audioList.get(0);
            if (recording.getPlaybackUrl() == null) {
                return;
            }
            String playbackUrl = toAbsolutePlaybackUrl(recording.getPlaybackUrl());
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(playbackUrl));
            context.startActivity(intent);
        });

        holder.btnMap.setOnClickListener(v -> openEmergencyInMaps(alert));
        holder.itemView.setOnClickListener(v -> showEmergencyDetails(alert));
    }

    private void loadAudioHistory(String emergencyId, ViewHolder holder) {
        if (emergencyId == null) {
            holder.btnPlayAudio.setText("Sin audio");
            return;
        }
        List<EmergencyAudioRecordingResponse> cached = audioCache.get(emergencyId);
        if (cached != null) {
            updateAudioButton(holder, cached);
            return;
        }
        RetrofitClient.getApiService().getEmergencyAudioHistory(emergencyId, requesterId)
                .enqueue(new Callback<List<EmergencyAudioRecordingResponse>>() {
                    @Override
                    public void onResponse(Call<List<EmergencyAudioRecordingResponse>> call,
                                           Response<List<EmergencyAudioRecordingResponse>> response) {
                        List<EmergencyAudioRecordingResponse> list = response.isSuccessful() ? response.body() : null;
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        audioCache.put(emergencyId, list);
                        updateAudioButton(holder, list);
                    }

                    @Override
                    public void onFailure(Call<List<EmergencyAudioRecordingResponse>> call, Throwable t) {
                        holder.btnPlayAudio.setEnabled(false);
                        holder.btnPlayAudio.setText("Audio no disponible");
                    }
                });
    }

    private void updateAudioButton(ViewHolder holder, List<EmergencyAudioRecordingResponse> list) {
        if (list == null || list.isEmpty() || list.get(0).getPlaybackUrl() == null) {
            holder.btnPlayAudio.setEnabled(false);
            holder.btnPlayAudio.setText("Sin audio");
        } else {
            holder.btnPlayAudio.setEnabled(true);
            holder.btnPlayAudio.setText("Reproducir audio");
        }
    }

    private String toAbsolutePlaybackUrl(String playbackUrl) {
        if (playbackUrl.startsWith("http://") || playbackUrl.startsWith("https://")) {
            return playbackUrl;
        }
        String baseUrl = RetrofitClient.getBaseUrl();
        if (baseUrl.endsWith("/") && playbackUrl.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + playbackUrl;
        }
        if (!baseUrl.endsWith("/") && !playbackUrl.startsWith("/")) {
            return baseUrl + "/" + playbackUrl;
        }
        return baseUrl + playbackUrl;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvTimestamp;
        final TextView tvLocation;
        final TextView tvSummary;
        final TextView tvProtectedName;
        final Button btnPlayAudio;
        final Button btnMap;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvEmergencyHistoryTitle);
            tvTimestamp = itemView.findViewById(R.id.tvEmergencyHistoryTimestamp);
            tvLocation = itemView.findViewById(R.id.tvEmergencyHistoryLocation);
            tvSummary = itemView.findViewById(R.id.tvEmergencyHistorySummary);
            tvProtectedName = itemView.findViewById(R.id.tvEmergencyHistoryProtectedName);
            btnPlayAudio = itemView.findViewById(R.id.btnEmergencyHistoryAudio);
            btnMap = itemView.findViewById(R.id.btnEmergencyHistoryMap);
        }
    }

    private String formatCreatedAt(String createdAt) {
        Date parsed = parseApiDate(createdAt);
        if (parsed == null) {
            return "Fecha desconocida";
        }
        return displayDateTimeFormatter.format(parsed);
    }

    private String buildSummary(EmergencyAlertResponse alert, ViewHolder holder) {
        StringBuilder sb = new StringBuilder();
        if (alert.getResolutionType() != null && !alert.getResolutionType().isBlank()) {
            sb.append("Resolucion: ");
            sb.append(alert.getResolutionType());
        } else if (alert.getStatus() != null) {
            sb.append("Estado: ");
            sb.append(alert.getStatus());
        }

        if (alert.getResolvedAt() != null && !alert.getResolvedAt().isBlank()) {
            String resolvedLabel = formatResolvedAt(alert.getResolvedAt());
            if (!resolvedLabel.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Resuelto: ");
                sb.append(resolvedLabel);
            }
        }

        String resolvedById = alert.getResolvedByUserId();
        if (resolvedById != null && !resolvedById.isBlank()) {
            String cachedName = userNameCache.get(resolvedById);
            if (cachedName != null && !cachedName.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Resuelto por: ");
                sb.append(cachedName);
            } else {
                fetchUserName(resolvedById, holder);
            }
        }

        if (alert.getSecondsActive() > 0) {
            long seconds = alert.getSecondsActive();
            long minutes = seconds / 60;
            if (minutes > 0) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Duracion: ");
                sb.append(minutes).append(" min");
            }
        }
        return sb.length() == 0 ? "Estado: DESCONOCIDO" : sb.toString();
    }

    private void fetchUserName(String userId, ViewHolder holder) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (userNameCache.containsKey(userId)) {
            return;
        }
        userNameCache.put(userId, "");
        RetrofitClient.getApiService().getUserById(userId)
                .enqueue(new Callback<UserResponse>() {
                    @Override
                    public void onResponse(Call<UserResponse> call,
                                           Response<UserResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getName() != null) {
                            userNameCache.put(userId, response.body().getName());
                            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                                notifyItemChanged(holder.getAdapterPosition());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<UserResponse> call, Throwable t) {
                    }
                });
    }

    private String resolveProtectedName(String userId, ViewHolder holder) {
        if (userId == null || userId.isBlank()) {
            return "Protegido: desconocido";
        }
        String cached = userNameCache.get(userId);
        if (cached != null && !cached.isBlank()) {
            return "Protegido: " + cached;
        }
        fetchUserName(userId, holder);
        return "Protegido: cargando...";
    }

    private void showEmergencyDetails(EmergencyAlertResponse alert) {
        if (context instanceof HostDashboardActivity) {
            ((HostDashboardActivity) context).showEmergencyHistoryDetails(alert, audioCache.get(alert.getId()));
        }
    }

    private String formatResolvedAt(String resolvedAt) {
        Date parsed = parseApiDate(resolvedAt);
        if (parsed == null) {
            return "";
        }
        return displayTimeFormatter.format(parsed);
    }

    private Date parseApiDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value;
        int dotIndex = normalized.indexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        try {
            return apiDateTimeParser.parse(normalized);
        } catch (ParseException ex) {
            return null;
        }
    }

    private void openEmergencyInMaps(EmergencyAlertResponse emergency) {
        if (emergency == null) {
            return;
        }
        if (context instanceof HostDashboardActivity) {
            ((HostDashboardActivity) context).openEmergencyInMapsFromHistory(emergency);
            return;
        }
        String destination = emergency.getLatitude() + "," + emergency.getLongitude();
        String uri = "https://www.google.com/maps/search/?api=1&query=" + destination;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");
        try {
            context.startActivity(intent);
        } catch (Exception ex) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        }
    }
}
