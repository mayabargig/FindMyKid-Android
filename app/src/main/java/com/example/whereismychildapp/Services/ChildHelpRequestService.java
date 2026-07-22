package com.example.whereismychildapp.Services;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class ChildHelpRequestService {

    private final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

    public interface Callback {
        void onSuccess(String requestId);
        void onError(String msg);
    }

    public void sendSOS(@Nullable String note, Callback cb) {
        String childUid = FirebaseAuth.getInstance().getUid();
        if (childUid == null) {
            cb.onError("לא מחובר");
            return;
        }

        db.child("locations").child(childUid).get()
                .addOnSuccessListener(locSnap -> {
                    if (!locSnap.exists()) {
                        cb.onError("אין מיקום אחרון שמור");
                        return;
                    }

                    Double lat = locSnap.child("latitude").getValue(Double.class);
                    Double lng = locSnap.child("longitude").getValue(Double.class);

                    if (lat == null || lng == null) {
                        cb.onError("מיקום לא תקין");
                        return;
                    }

                    db.child("children").child(childUid).child("parentUid").get()
                            .addOnSuccessListener(parentSnap -> {
                                String parentUid = parentSnap.getValue(String.class);

                                if (parentUid == null || parentUid.trim().isEmpty()) {
                                    cb.onError("לא נמצא הורה מקושר לילד");
                                    return;
                                }

                                db.child("users").child(childUid).child("nickname").get()
                                        .addOnSuccessListener(nameSnap -> {

                                            String childName = nameSnap.getValue(String.class);
                                            if (childName == null || childName.trim().isEmpty()) {
                                                childName = "הילד";
                                            }

                                            String requestId = db.child("helpRequests")
                                                    .child(parentUid)
                                                    .child(childUid)
                                                    .push()
                                                    .getKey();

                                            if (requestId == null) {
                                                cb.onError("שגיאה ביצירת requestId");
                                                return;
                                            }

                                            Map<String, Object> req = new HashMap<>();
                                            req.put("latitude", lat);
                                            req.put("longitude", lng);
                                            req.put("timestamp", System.currentTimeMillis());
                                            req.put("note", note == null ? "" : note.trim());
                                            req.put("status", "open");
                                            req.put("childUid", childUid);
                                            req.put("childName", childName);

                                            db.child("helpRequests")
                                                    .child(parentUid)
                                                    .child(childUid)
                                                    .child(requestId)
                                                    .setValue(req)
                                                    .addOnSuccessListener(v -> cb.onSuccess(requestId))
                                                    .addOnFailureListener(e -> cb.onError(
                                                            e.getMessage() == null ? "שגיאה לא ידועה" : e.getMessage()
                                                    ));
                                        })
                                        .addOnFailureListener(e -> cb.onError(
                                                e.getMessage() == null ? "שגיאה בשליפת שם הילד" : e.getMessage()
                                        ));
                            })
                            .addOnFailureListener(e -> cb.onError(
                                    e.getMessage() == null ? "שגיאה בשליפת parentUid" : e.getMessage()
                            ));

                })
                .addOnFailureListener(e -> cb.onError(
                        e.getMessage() == null ? "שגיאה בשליפת מיקום" : e.getMessage()
                ));
    }
}
