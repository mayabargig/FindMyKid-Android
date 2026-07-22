package com.example.whereismychildapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.whereismychildapp.Adapters.HelpRequestAdapter;
import com.example.whereismychildapp.Objects.HelpRequest;
import com.example.whereismychildapp.Objects.User;
import com.example.whereismychildapp.Services.ChildHelpRequestService;
import com.example.whereismychildapp.Services.ChildTrackingForegroundService;
import com.example.whereismychildapp.Services.ParentHelpRequestService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class fragment_home extends Fragment {

    private LinearLayout layoutChild, layoutParent;
    private Button btnUpdateLocation, btnOpenMap, btnSOS;
    private TextView txtSosHint;

    // SOS hold
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean longPressTriggered = false;
    private static final long HOLD_MS = 2000;
    private Animation sosPulseAnim;
    private Vibrator vibrator;

    // ===== Parent SOS list =====
    private RecyclerView rvSos;
    private HelpRequestAdapter sosAdapter;
    private final List<HelpRequest> sosList = new ArrayList<>();
    private ValueEventListener sosListener;
    private DatabaseReference helpRef;
    private String parentUid;

    private final ActivityResultLauncher<String> fineLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                Context ctx = getContext();
                if (ctx == null) return;

                if (granted) {
                    ChildTrackingForegroundService.start(ctx.getApplicationContext());
                } else {
                    Toast.makeText(ctx, "צריך הרשאת מיקום כדי שהמעקב יעבוד", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutChild = view.findViewById(R.id.layoutChild);
        layoutParent = view.findViewById(R.id.layoutParent);

        btnUpdateLocation = view.findViewById(R.id.btnUpdateLocation);
        btnOpenMap = view.findViewById(R.id.btnOpenMap);

        btnSOS = view.findViewById(R.id.btnSOS);
        txtSosHint = view.findViewById(R.id.txtSosHint);

        // RecyclerView (Parent UI) - חייב להיות קיים ב-XML בתוך layoutParent
        rvSos = view.findViewById(R.id.rvSos);

        sosPulseAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.sos_pulse);
        initVibrator();

        requestNotificationPermissionIfNeeded();

        showGuestUI(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        refreshHomeState();
    }

    @Override
    public void onStop() {
        super.onStop();
        // שלא נשאיר מאזין חי כשעוזבים את המסך
        stopListenParentSos();
    }

    private void initVibrator() {
        Context ctx = getContext();
        if (ctx == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = (vm != null) ? vm.getDefaultVibrator() : null;
            } else {
                vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Exception e) {
            vibrator = null;
        }
    }

    private void vibrate(int ms) {
        try {
            if (vibrator == null) return;
            boolean canVibrate;
            try {
                canVibrate = vibrator.hasVibrator();
            } catch (Exception e) {
                canVibrate = true;
            }
            if (!canVibrate) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    // For Android 13+
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "נדרשת הרשאה להתראות כדי לאפשר מעקב מיקום ברקע", Toast.LENGTH_SHORT).show();
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        200
                );
            }
        }
    }

    private void refreshHomeState() {
        // תמיד מאפס
        showGuestUI(false);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            showGuestUI(true);
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

        userRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded() || getContext() == null) return;

                    User user = snapshot.getValue(User.class);

                    if (user == null || user.getUserType() == null) {
                        showGuestUI(true);
                        return;
                    }

                    if (user.getUserType() == User.UserType.CHILD) {
                        setupChildUI();
                    } else if (user.getUserType() == User.UserType.PARENT) {
                        setupParentUI();
                    } else {
                        showGuestUI(true);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    showGuestUI(true);
                });
    }

    private void showGuestUI(boolean showToast) {
        stopListenParentSos();

        if (layoutChild != null) layoutChild.setVisibility(View.GONE);
        if (layoutParent != null) layoutParent.setVisibility(View.GONE);

        if (btnUpdateLocation != null) {
            btnUpdateLocation.setOnClickListener(null);
            btnUpdateLocation.setVisibility(View.GONE);
        }
        if (btnOpenMap != null) btnOpenMap.setOnClickListener(null);

        if (btnSOS != null) {
            btnSOS.setOnClickListener(null);
            btnSOS.setOnTouchListener(null);
            btnSOS.setVisibility(View.GONE);
            btnSOS.setEnabled(true);
        }
        if (txtSosHint != null) txtSosHint.setVisibility(View.GONE);

        if (showToast && isAdded() && getContext() != null) {
            Toast.makeText(getContext(), "אתה לא מחובר", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupChildUI() {
        stopListenParentSos();

        if (!isAdded()) return;

        layoutChild.setVisibility(View.VISIBLE);
        layoutParent.setVisibility(View.GONE);

        btnUpdateLocation.setVisibility(View.GONE);

        if (btnSOS != null) {
            btnSOS.setVisibility(View.VISIBLE);
            btnSOS.setEnabled(true);
            setupHoldToSendSOS();
        }
        if (txtSosHint != null) txtSosHint.setVisibility(View.VISIBLE);

        ensurePermissionsThenStartTrackingService();
    }

    private void ensurePermissionsThenStartTrackingService() {
        if (!isAdded()) return;
        Context ctx = getContext();
        if (ctx == null) return;

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        ChildTrackingForegroundService.start(ctx.getApplicationContext());
    }

    private void setupParentUI() {
        if (!isAdded()) return;

        layoutParent.setVisibility(View.VISIBLE);
        layoutChild.setVisibility(View.GONE);

        if (btnSOS != null) {
            btnSOS.setOnTouchListener(null);
            btnSOS.setVisibility(View.GONE);
        }
        if (txtSosHint != null) txtSosHint.setVisibility(View.GONE);

        btnOpenMap.setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(R.id.fragment_select_child);
        });

        // רשימה
        setupSosRecyclerForParent();
        startListenParentSos();

        // הדיאלוג שלך עדיין נשאר (אם מגיע Intent extras)
        showSosDialogIfNeededForParent();
    }

    // ===================== PARENT SOS LIST =====================

    private void setupSosRecyclerForParent() {
        if (!isAdded() || getContext() == null) return;
        if (rvSos == null) return;

        rvSos.setLayoutManager(new LinearLayoutManager(requireContext()));

        sosAdapter = new HelpRequestAdapter(sosList, new HelpRequestAdapter.Listener() {
            @Override
            public void onToggleHandled(HelpRequest item) {
                String uid = FirebaseAuth.getInstance().getUid();
                if (uid == null) return;

                String current = (item.status == null) ? "open" : item.status;
                String newStatus = "handled".equalsIgnoreCase(current) ? "open" : "handled";

                FirebaseDatabase.getInstance().getReference()
                        .child("helpRequests")
                        .child(uid)
                        .child(item.childUid)
                        .child(item.requestId)
                        .child("status")
                        .setValue(newStatus);
            }

            @Override
            public void onShowOnMap(HelpRequest item) {
                // פתיחה למפה אצלך (fragment_parentMap)
                Bundle b = new Bundle();
                b.putBoolean("trackAll", false);
                b.putString("childUid", item.childUid);
                b.putString("childNickname", item.childName == null ? "הילד" : item.childName);
                b.putBoolean("fromSOS", true);
                b.putFloat("lat", (float) item.latitude);
                b.putFloat("lng", (float) item.longitude);

                NavHostFragment.findNavController(fragment_home.this)
                        .navigate(R.id.fragment_parentMap, b);
            }

            @Override
            public void onCallPolice(HelpRequest item) {
                dialNumber("100");
            }

            @Override
            public void onCallMda(HelpRequest item) {
                dialNumber("101");
            }
        });

        rvSos.setAdapter(sosAdapter);
    }

    private void startListenParentSos() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        parentUid = uid;

        // קודם מנקים מאזין ישן (אם היה)
        stopListenParentSos();

        // עכשיו מגדירים ref חדש
        helpRef = FirebaseDatabase.getInstance().getReference()
                .child("helpRequests")
                .child(parentUid);

        sosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                sosList.clear();

                for (DataSnapshot childSnap : snapshot.getChildren()) {
                    String childUid = childSnap.getKey();
                    if (childUid == null) continue;

                    for (DataSnapshot reqSnap : childSnap.getChildren()) {
                        HelpRequest r = reqSnap.getValue(HelpRequest.class);
                        if (r == null) continue;

                        r.childUid = childUid;
                        r.requestId = reqSnap.getKey();

                        if (r.status == null || r.status.trim().isEmpty()) r.status = "open";

                        sosList.add(r);
                    }
                }

                Collections.sort(sosList, (a, b) -> Long.compare(b.timestamp, a.timestamp));

                if (sosAdapter != null) sosAdapter.notifyDataSetChanged();

                scrollToFocusedSosIfNeeded();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Firebase error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        // ✅ הגנה נוספת (לא חובה, אבל בטוח)
        if (helpRef != null) {
            helpRef.addValueEventListener(sosListener);
        }
    }

    private void stopListenParentSos() {
        if (helpRef != null && sosListener != null) {
            helpRef.removeEventListener(sosListener);
        }
        sosListener = null;
        helpRef = null;
    }


    private void scrollToFocusedSosIfNeeded() {
        // 1) קודם כל נבדוק args של fragment (אם MainActivity ניווט עם Bundle)
        Bundle args = getArguments();
        if (args == null) return;

        String focusRequestId = args.getString("sos_requestId");
        String focusChildUid = args.getString("sos_childUid");
        if (focusRequestId == null || focusChildUid == null) return;

        int index = -1;
        for (int i = 0; i < sosList.size(); i++) {
            HelpRequest r = sosList.get(i);
            if (focusRequestId.equals(r.requestId) && focusChildUid.equals(r.childUid)) {
                index = i;
                break;
            }
        }

        if (index != -1 && rvSos != null) {
            rvSos.scrollToPosition(index);
            // כדי שלא יקפוץ שוב כל refresh
            args.remove("sos_requestId");
            args.remove("sos_childUid");
        }
    }

    private void dialNumber(String number) {
        try {
            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            startActivity(i);
        } catch (Exception e) {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), "לא ניתן לפתוח חיוג", Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== SOS FLOW (DIALOG FROM NOTIF) =====================

    private void showSosDialogIfNeededForParent() {
        if (!isAdded() || getActivity() == null) return;

        // גם extras מה-Intent (מהשירות) וגם args (אם ניווטו אליך עם bundle)
        Bundle src = null;
        if (getActivity().getIntent() != null && getActivity().getIntent().getExtras() != null) {
            src = getActivity().getIntent().getExtras();
        }

        if (src == null && getArguments() != null) {
            src = getArguments();
        }
        if (src == null) return;

        String childUid = src.getString("sos_childUid");
        String requestId = src.getString("sos_requestId");
        double lat = src.getDouble("sos_lat", 0);
        double lng = src.getDouble("sos_lng", 0);
        String note = src.getString("sos_note", "");
        String childName = src.getString("sos_childName", "הילד");

        if (childUid == null || requestId == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("🚨 SOS מ-" + childName)
                .setMessage((note == null || note.isEmpty()) ? "נשלחה בקשת עזרה" : note)
                .setNegativeButton("סגור", null)
                .setNeutralButton("🗺 פתח במפה", (d, w) -> {
                    Bundle b = new Bundle();
                    b.putBoolean("trackAll", false);
                    b.putString("childUid", childUid);
                    b.putString("childNickname", childName);
                    b.putBoolean("fromSOS", true);
                    b.putFloat("lat", (float) lat);
                    b.putFloat("lng", (float) lng);
                    NavHostFragment.findNavController(this).navigate(R.id.fragment_parentMap, b);
                })
                .setPositiveButton("✅ טופל", (d, w) -> {
                    new ParentHelpRequestService().closeRequest(childUid, requestId,
                            new ParentHelpRequestService.Callback() {
                                @Override
                                public void onSuccess() {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(), "סומן כטופל ✅", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(String msg) {
                                    if (!isAdded()) return;
                                    Toast.makeText(requireContext(), "שגיאה: " + msg, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show();

        // ניקוי extras מה-Intent כדי שלא יקפוץ שוב
        Intent i = getActivity().getIntent();
        if (i != null) {
            i.removeExtra("sos_childUid");
            i.removeExtra("sos_requestId");
            i.removeExtra("sos_lat");
            i.removeExtra("sos_lng");
            i.removeExtra("sos_note");
            i.removeExtra("sos_childName");
        }

        // ניקוי args אם הגיע משם
        if (getArguments() != null) {
            getArguments().remove("sos_childUid");
            getArguments().remove("sos_requestId");
            getArguments().remove("sos_lat");
            getArguments().remove("sos_lng");
            getArguments().remove("sos_note");
            getArguments().remove("sos_childName");
        }
    }

    // ===================== CHILD SOS SEND =====================

    @SuppressLint("ClickableViewAccessibility")
    private void setupHoldToSendSOS() {
        btnSOS.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    longPressTriggered = false;

                    if (sosPulseAnim != null) btnSOS.startAnimation(sosPulseAnim);

                    vibrate(40);

                    btnSOS.setText("המשך להחזיק...");

                    handler.postDelayed(() -> {
                        longPressTriggered = true;

                        btnSOS.clearAnimation();

                        vibrate(120);

                        btnSOS.setText("🚨 SOS – צריך עזרה");
                        showSosDialog();
                    }, HOLD_MS);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacksAndMessages(null);

                    btnSOS.clearAnimation();

                    if (!longPressTriggered) {
                        btnSOS.setText("🚨 SOS – צריך עזרה");
                        Toast.makeText(requireContext(),
                                "לחיצה קצרה - כדי לשלוח החזק 2 שניות",
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
            return false;
        });
    }

    private void showSosDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("אופציונלי: מה קרה?");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        new AlertDialog.Builder(requireContext())
                .setTitle("שליחת בקשת עזרה להורה")
                .setMessage("אפשר להוסיף הערה, או לשלוח מיד.")
                .setView(input)
                .setNegativeButton("ביטול", (d, w) -> d.dismiss())
                .setPositiveButton("שלח", (d, w) -> {
                    String note = input.getText() == null ? "" : input.getText().toString();
                    sendSos(note);
                })
                .show();
    }

    private void sendSos(@Nullable String note) {
        if (btnSOS == null) return;

        btnSOS.setEnabled(false);

        new ChildHelpRequestService().sendSOS(note, new ChildHelpRequestService.Callback() {
            @Override
            public void onSuccess(String requestId) {
                if (!isAdded()) return;
                btnSOS.setEnabled(true);
                vibrate(80);
                Toast.makeText(requireContext(), "נשלח להורה ✅", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String msg) {
                if (!isAdded()) return;
                btnSOS.setEnabled(true);
                vibrate(200);
                Toast.makeText(requireContext(), "שגיאה: " + msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
