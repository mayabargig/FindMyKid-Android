package com.example.whereismychildapp.Services;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ParentHelpRequestService {

    private final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }

    public void closeRequest(String childUid, String requestId, Callback cb) {
        String parentUid = FirebaseAuth.getInstance().getUid();
        if (parentUid == null) {
            cb.onError("לא מחובר");
            return;
        }
        if (childUid == null || childUid.trim().isEmpty() || requestId == null || requestId.trim().isEmpty()) {
            cb.onError("פרטים חסרים");
            return;
        }

        db.child("helpRequests")
                .child(parentUid)
                .child(childUid)
                .child(requestId)
                .child("status")
                .setValue("closed")
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage() == null ? "שגיאה" : e.getMessage()));
    }
}
