package com.guardianapp.mobile.ui.host;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.guardianapp.mobile.R;

import java.util.ArrayList;
import java.util.List;

public class AppControlAdapter extends RecyclerView.Adapter<AppControlAdapter.AppViewHolder> {

    // Modelo interno simple para la UI
    public static class AppItem {
        public String packageName;
        public String appName;
        public boolean isBlocked;
        public String blockedId; // Guardamos el ID del bloqueo por si queremos desbloquearla

        public AppItem(String packageName, String appName, boolean isBlocked, String blockedId) {
            this.packageName = packageName;
            this.appName = appName;
            this.isBlocked = isBlocked;
            this.blockedId = blockedId;
        }
    }

    // Interfaz para avisarle a la Activity que se hizo clic
    public interface OnAppActionClickListener {
        void onBlockClick(AppItem app);
        void onUnblockClick(AppItem app);
    }

    private List<AppItem> appList = new ArrayList<>();
    private final OnAppActionClickListener listener;

    public AppControlAdapter(OnAppActionClickListener listener) {
        this.listener = listener;
    }

    public void setApps(List<AppItem> newApps) {
        this.appList = newApps;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_control, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppItem app = appList.get(position);
        holder.tvAppName.setText(app.appName);

        if (app.isBlocked) {
            holder.btnToggle.setText("Desbloquear");
            holder.btnToggle.setBackgroundColor(Color.parseColor("#4CAF50")); // Verde
            holder.btnToggle.setOnClickListener(v -> listener.onUnblockClick(app));
        } else {
            holder.btnToggle.setText("Bloquear");
            holder.btnToggle.setBackgroundColor(Color.parseColor("#D32F2F")); // Rojo
            holder.btnToggle.setOnClickListener(v -> listener.onBlockClick(app));
        }
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName;
        Button btnToggle;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tvAppNameItem);
            btnToggle = itemView.findViewById(R.id.btnToggleBlock);
        }
    }
}