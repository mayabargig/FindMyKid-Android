package com.example.whereismychildapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.whereismychildapp.Adapters.ChildAdapter;
import com.example.whereismychildapp.Cards.ChildCard;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import com.google.android.material.button.MaterialButton;
import java.util.Collections;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link fragment_select_child#newInstance} factory method to
 * create an instance of this fragment.
 */
public class fragment_select_child extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private RecyclerView rv;
    private TextInputEditText etSearch;
    private MaterialButton btnTrackAll;
    private ChildAdapter adapter;
    private final List<ChildCard> items = new ArrayList<>();
    private DatabaseReference db;

    public fragment_select_child() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment fragment_select_child.
     */
    // TODO: Rename and change types and number of parameters
    public static fragment_select_child newInstance(String param1, String param2) {
        fragment_select_child fragment = new fragment_select_child();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_select_child, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rv = view.findViewById(R.id.rvChildren);
        etSearch = view.findViewById(R.id.etSearch);
        btnTrackAll = view.findViewById(R.id.btnTrackAll);
        btnTrackAll.setOnClickListener(v -> openMapAll());

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChildAdapter(items, (childUid,childNickname) -> openMap(childUid,childNickname));
        rv.setAdapter(adapter);

        db = FirebaseDatabase.getInstance().getReference();

        etSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });

        loadChildrenForParent();
    }
    private void loadChildrenForParent() {
        String parentUid = FirebaseAuth.getInstance().getUid();
        if (parentUid == null) return;

        db.child("parents").child(parentUid).child("childrenUids").get()
                .addOnSuccessListener(snapshot -> {
                    items.clear();

                    // תומך גם ב-Map וגם ב-List
                    List<String> childUids = new ArrayList<>();

                    if (snapshot.getValue() instanceof List) {
                        List<Object> raw = (List<Object>) snapshot.getValue();
                        if (raw != null) {
                            for (Object o : raw) if (o != null) childUids.add(o.toString());
                        }
                    } else {
                        for (DataSnapshot c : snapshot.getChildren()) {
                            childUids.add(c.getKey()); // אם זה map: childUid:true
                        }
                    }

                    if (childUids.isEmpty()) {
                        Toast.makeText(getContext(), "אין ילדים מקושרים להורה", Toast.LENGTH_SHORT).show();
                        adapter.setData(items);
                        return;
                    }

                    // נטען כל ילד
                    for (String childUid : childUids) {
                        loadChildCard(childUid);
                    }
                });
    }
    private void loadChildCard(String childUid) {
        // nickname
        db.child("users").child(childUid).child("nickname").get()
                .addOnSuccessListener(nameSnap -> {
                    String nickname = nameSnap.getValue(String.class);

                    // last timestamp
                    db.child("locations").child(childUid).child("timestamp").get()
                            .addOnSuccessListener(tsSnap -> {
                                Long ts = tsSnap.getValue(Long.class);

                                items.add(new ChildCard(childUid, nickname, ts));
                                adapter.setData(new ArrayList<>(items));

                            });
                });
    }
    private void openMapAll() {
        if (items.isEmpty()) {
            Toast.makeText(getContext(), "אין ילדים להצגה", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> uids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();

        for (ChildCard c : items) {
            if (c == null || c.uid == null) continue;
            uids.add(c.uid);
            names.add(c.nickname == null ? "ילד" : c.nickname);
        }

        if (uids.isEmpty()) {
            Toast.makeText(getContext(), "אין ילדים להצגה", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle b = new Bundle();
        b.putBoolean("trackAll", true);
        b.putStringArrayList("childUids", uids);
        b.putStringArrayList("childNicknames", names);

        NavHostFragment.findNavController(this)
                .navigate(R.id.fragment_parentMap, b);
    }

    private void openMap(String childUid , String childNickname) {
        Bundle b = new Bundle();
        b.putString("childUid", childUid);
        b.putString("childNickname", childNickname);

        NavHostFragment.findNavController(this)
                .navigate(R.id.fragment_parentMap, b);
    }
}