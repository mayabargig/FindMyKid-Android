package com.example.whereismychildapp.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whereismychildapp.Objects.HelpRequest;
import com.example.whereismychildapp.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class HelpRequestAdapter extends RecyclerView.Adapter<HelpRequestAdapter.VH> {

    public interface Listener {
        void onToggleHandled(HelpRequest item);
        void onShowOnMap(HelpRequest item);
        void onCallPolice(HelpRequest item);
        void onCallMda(HelpRequest item);
    }

    private final List<HelpRequest> items;
    private final Listener listener;

    public HelpRequestAdapter(List<HelpRequest> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sos_request, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HelpRequest item = items.get(position);

        String childName = (item.childName != null && !item.childName.trim().isEmpty())
                ? item.childName : "ילד";

        String note = (item.note != null) ? item.note : "";

        String status = (item.status != null && !item.status.trim().isEmpty())
                ? item.status : "open";

        h.tvChildName.setText(childName);
        h.tvNote.setText(note);
        h.tvStatus.setText("סטטוס: " + status);

        // זמן (timestamp במילישניות)
        h.tvTime.setText(android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", item.timestamp));

        boolean handled = "handled".equalsIgnoreCase(status);
        boolean enabled = !handled;

        h.btnToggleHandled.setText(handled ? "↩️ סמן כלא טופל" : "✅ סמן כטופל");

        // כפתורים שננעלים כשהאירוע טופל
        h.btnShowOnMap.setEnabled(enabled);
        h.btnPolice.setEnabled(enabled);
        h.btnMda.setEnabled(enabled);

        // מראה "נעול"
        float alpha = enabled ? 1f : 0.35f;
        h.btnShowOnMap.setAlpha(alpha);
        h.btnPolice.setAlpha(alpha);
        h.btnMda.setAlpha(alpha);

        // מאזינים
        h.btnToggleHandled.setOnClickListener(v -> listener.onToggleHandled(item));

        h.btnShowOnMap.setOnClickListener(v -> {
            if (enabled) listener.onShowOnMap(item);
        });

        h.btnPolice.setOnClickListener(v -> {
            if (enabled) listener.onCallPolice(item);
        });

        h.btnMda.setOnClickListener(v -> {
            if (enabled) listener.onCallMda(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvChildName, tvTime, tvNote, tvStatus;
        MaterialButton btnToggleHandled, btnShowOnMap, btnPolice, btnMda;

        VH(@NonNull View itemView) {
            super(itemView);
            tvChildName = itemView.findViewById(R.id.tvChildName);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvNote = itemView.findViewById(R.id.tvNote);
            tvStatus = itemView.findViewById(R.id.tvStatus);

            btnToggleHandled = itemView.findViewById(R.id.btnToggleHandled);
            btnShowOnMap = itemView.findViewById(R.id.btnShowOnMap);
            btnPolice = itemView.findViewById(R.id.btnPolice);
            btnMda = itemView.findViewById(R.id.btnMda);
        }
    }
}
