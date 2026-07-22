package com.example.whereismychildapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;

import org.osmdroid.views.overlay.infowindow.InfoWindow;


public class fragment_parentMap extends Fragment {

    private MapView map;

    private DatabaseReference locRef;
    private ValueEventListener locListener;
    private Marker childMarker;
    private String childUid;
    private String childNickname;

    private boolean trackAll = false;
    private ArrayList<String> childUids;
    private ArrayList<String> childNicknames;

    private final Map<String, DatabaseReference> locRefs = new HashMap<>();
    private final Map<String, ValueEventListener> locListeners = new HashMap<>();
    private final Map<String, Marker> markersByUid = new HashMap<>();
    private final Map<String, String> nameByUid = new HashMap<>();

    private boolean fromSOS = false;
    private double sosLat = 0;
    private double sosLng = 0;
    private Marker sosMarker;

    public fragment_parentMap() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());
        return inflater.inflate(R.layout.fragment_parentmap, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            trackAll = getArguments().getBoolean("trackAll", false);
            fromSOS = getArguments().getBoolean("fromSOS", false);
            sosLat = getArguments().getDouble("sosLat", 0);
            sosLng = getArguments().getDouble("sosLng", 0);

            if (trackAll) {
                childUids = getArguments().getStringArrayList("childUids");
                childNicknames = getArguments().getStringArrayList("childNicknames");
            } else {
                childUid = getArguments().getString("childUid");
                childNickname = getArguments().getString("childNickname");
            }
        }

        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);

        map.setOnTouchListener((v, event) -> {
            InfoWindow.closeAllInfoWindowsOn(map);
            return false; // סגור לחצני ילד כאשר זזים או לחוצים במפה
        });


        if (trackAll) {
            if (childUids == null || childUids.isEmpty()) {
                Toast.makeText(getContext(), "אין ילדים להצגה", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }

            for (int i = 0; i < childUids.size(); i++) {
                String uid = childUids.get(i);
                String name = (childNicknames != null && i < childNicknames.size())
                        ? childNicknames.get(i)
                        : "ילד";
                if (uid != null) nameByUid.put(uid, (name == null || name.isEmpty()) ? "ילד" : name);
            }

            startListeningToAllChildren();
        } else {
            if (childUid == null || childUid.isEmpty()) {
                Toast.makeText(getContext(), "לא נבחר ילד להצגה", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }

            if (childNickname == null || childNickname.isEmpty()) childNickname = "ילד";

            if (fromSOS && (sosLat != 0 || sosLng != 0)) {
                showSosMarker(childNickname, sosLat, sosLng);
            }

            startListeningToSingleChild(childUid, childNickname);
        }
    }

    private void showSosMarker(String nickname, double lat, double lng) {
        GeoPoint point = new GeoPoint(lat, lng);

        if (sosMarker == null) {
            sosMarker = new Marker(map);
            sosMarker.setTitle("SOS - " + nickname);
            sosMarker.setPosition(point);
            map.getOverlays().add(sosMarker);
        } else {
            sosMarker.setPosition(point);
            sosMarker.setTitle("SOS - " + nickname);
        }

        map.getController().setCenter(point);
        map.getController().animateTo(point);
        map.invalidate();
    }

    private void startListeningToSingleChild(String uid, String nickname) {
        locRef = FirebaseDatabase.getInstance().getReference("locations").child(uid);

        locListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lon = snapshot.child("longitude").getValue(Double.class);
                if (lat == null || lon == null) return;

                GeoPoint point = new GeoPoint(lat, lon);

                if (childMarker == null) {
                    childMarker = new Marker(map);
                    childMarker.setTitle(nickname);
                    childMarker.setPosition(point);
                    map.getOverlays().add(childMarker);

                    childMarker.setInfoWindow(new ChildInfoWindow(map));


                } else {
                    childMarker.setPosition(point);
                }

                map.getController().animateTo(point);
                map.invalidate();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };

        locRef.addValueEventListener(locListener);
    }

    private void startListeningToAllChildren() {
        for (String uid : childUids) {
            if (uid == null || uid.isEmpty()) continue;

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("locations").child(uid);

            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Double lat = snapshot.child("latitude").getValue(Double.class);
                    Double lon = snapshot.child("longitude").getValue(Double.class);
                    if (lat == null || lon == null) return;

                    GeoPoint point = new GeoPoint(lat, lon);

                    Marker m = markersByUid.get(uid);
                    if (m == null) {
                        m = new Marker(map);
                        m.setTitle(nameByUid.getOrDefault(uid, "ילד"));
                        m.setPosition(point);

                        m.setInfoWindow(new ChildInfoWindow(map));



                        map.getOverlays().add(m);
                        markersByUid.put(uid, m);

                        if (markersByUid.size() == 1) {
                            map.getController().setCenter(point);
                        }
                    } else {
                        m.setPosition(point);
                    }

                    map.invalidate();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) { }
            };

            locRefs.put(uid, ref);
            locListeners.put(uid, listener);
            ref.addValueEventListener(listener);
        }
    }

    private void openNavigation(double lat, double lon, @NonNull String label) {
        // 1) נסה Waze
        try {
            Uri wazeUri = Uri.parse("https://waze.com/ul?ll=" + lat + "," + lon + "&navigate=yes");
            Intent wazeIntent = new Intent(Intent.ACTION_VIEW, wazeUri);
            wazeIntent.setPackage("com.waze");

            startActivity(wazeIntent);
            return;
        } catch (Exception ignored) { }

        // 2) fallback: Google Maps
        try {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lon + "&mode=d");
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps"); // יעדיף גוגל מאפס

            startActivity(mapIntent);
            return;
        } catch (Exception ignored) { }

        // 3) fallback כללי: chooser לכל אפליקציית מפות
        Uri geoUri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon + "(" + Uri.encode(label) + ")");
        Intent geoIntent = new Intent(Intent.ACTION_VIEW, geoUri);

        if (geoIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(Intent.createChooser(geoIntent, "בחר אפליקציית ניווט"));
        } else {
            Toast.makeText(getContext(), "אין אפליקציית ניווט זמינה", Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (locRef != null && locListener != null) {
            locRef.removeEventListener(locListener);
        }

        for (String uid : locRefs.keySet()) {
            DatabaseReference ref = locRefs.get(uid);
            ValueEventListener l = locListeners.get(uid);
            if (ref != null && l != null) ref.removeEventListener(l);
        }

        locRefs.clear();
        locListeners.clear();
        markersByUid.clear();
        nameByUid.clear();
        sosMarker = null;
    }

    private class ChildInfoWindow extends InfoWindow {

        public ChildInfoWindow(MapView mapView) {
            super(R.layout.marker_child_info, mapView);
        }

        @Override
        public void onOpen(Object item) {
            Marker marker = (Marker) item;

            TextView txtTitle = mView.findViewById(R.id.txtTitle);
            TextView btnNavigate = mView.findViewById(R.id.btnNavigate);

            txtTitle.setText(marker.getTitle());

            GeoPoint p = marker.getPosition();
            btnNavigate.setOnClickListener(v ->
                    openNavigation(p.getLatitude(), p.getLongitude(), marker.getTitle())
            );
        }

        @Override
        public void onClose() { }
    }

}
