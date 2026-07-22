package com.example.whereismychildapp.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whereismychildapp.Cards.ChildCard;
import com.example.whereismychildapp.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;


public class ChildAdapter extends RecyclerView.Adapter<ChildAdapter.VH> {

    public interface OnChildClick {
        void onTrack(String childUid,String childNickname);
    }

    private List<ChildCard> list;
    private List<ChildCard> filtered;
    private OnChildClick listener;
    private String currentQuery = "";


    public ChildAdapter(List<ChildCard> list, OnChildClick listener) {
        this.list = list;
        this.filtered = new ArrayList<>(list);
        this.listener = listener;
    }
    private void sortByLastTimestampDesc(List<ChildCard> target) {
        Collections.sort(target, (a, b) -> {
            long ta = (a.lastTimestamp == null) ? Long.MIN_VALUE : a.lastTimestamp;
            long tb = (b.lastTimestamp == null) ? Long.MIN_VALUE : b.lastTimestamp;
            return Long.compare(tb, ta); // יורד = הכי חדש קודם
        });
    }


    public void setData(List<ChildCard> newList) {
        this.list = newList;
        sortByLastTimestampDesc(list);
        filter(currentQuery);
    }

    public void filter(String q) {
        currentQuery = (q == null) ? "" : q.trim().toLowerCase();
        filtered.clear();

        for (ChildCard c : list) {
            String name = (c.nickname == null) ? "" : c.nickname.toLowerCase();
            if (currentQuery.isEmpty() || name.contains(currentQuery)) {
                filtered.add(c);
            }
        }

        sortByLastTimestampDesc(filtered);
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_child_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ChildCard c = filtered.get(position);
        h.tvName.setText(c.nickname != null ? c.nickname : "ילד");
        h.tvStatus.setText(formatLastSeen(c.lastTimestamp));
        h.btnTrack.setOnClickListener(v -> listener.onTrack(c.uid,c.nickname));
    }

    @Override
    public int getItemCount() {
        return filtered.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus;
        MaterialButton btnTrack;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnTrack = itemView.findViewById(R.id.btnTrack);
        }
    }

    private String formatLastSeen(Long ts) {
        if (ts == null) return "אין מיקום עדיין";
        long diff = System.currentTimeMillis() - ts;
        long sec = diff / 1000;
        if (sec < 60) return "עודכן לפני רגע";
        long min = sec / 60;
        if (min < 60) return "עודכן לפני " + min + " דקות";
        long hr = min / 60;
        if (hr < 24) return "עודכן לפני " + hr + " שעות";
        long days = hr / 24;
        return "עודכן לפני " + days + " ימים";
    }
}



