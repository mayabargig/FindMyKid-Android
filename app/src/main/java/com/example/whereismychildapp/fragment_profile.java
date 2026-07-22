package com.example.whereismychildapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.whereismychildapp.Objects.User;
import com.example.whereismychildapp.Services.ChildTrackingForegroundService;
import com.example.whereismychildapp.Services.ParentSosPollingForegroundService;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link fragment_profile#newInstance} factory method to
 * create an instance of this fragment.
 */
public class fragment_profile extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private FirebaseUser currentUser;

    private LinearLayout  layoutLoggedIn;
    private TextView tvWelcome;
    private Button btnLogout;
    private Button btnShowQr;
    private User loadedUser;
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;


    public fragment_profile() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment fragment_profile.
     */
    // TODO: Rename and change types and number of parameters
    public static fragment_profile newInstance(String param1, String param2) {
        fragment_profile fragment = new fragment_profile();
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
        qrScannerLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (!isAdded()) return;

            if (result.getContents() == null) {
                Toast.makeText(getContext(), "הסריקה בוטלה", Toast.LENGTH_SHORT).show();
                return;
            }

            String scanned = result.getContents().trim();
            String parentUidFromQr = extractParentUidFromPayload(scanned);

            if (parentUidFromQr == null) {
                Toast.makeText(getContext(), "QR לא תקין", Toast.LENGTH_SHORT).show();
                return;
            }


            verifyParentAndLogout(parentUidFromQr);
        });


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        layoutLoggedIn = view.findViewById(R.id.layoutLoggedIn1);
        tvWelcome = view.findViewById(R.id.tvWelcome1);
        btnLogout = view.findViewById(R.id.btnLogout1);
        btnShowQr = view.findViewById(R.id.btnShowQr);
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {

            String uid = currentUser.getUid();

            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid);

            userRef.get().addOnSuccessListener(snapshot -> {
                if (!snapshot.exists()) {
                    Toast.makeText(getContext(), "משתמש לא נמצא", Toast.LENGTH_SHORT).show();
                    return;
                }

                User user = snapshot.getValue(User.class);
                loadedUser = user;

                if (user == null) {
                    Toast.makeText(getContext(), "שגיאה בטעינת פרופיל", Toast.LENGTH_SHORT).show();
                    return;
                }

                // הצגת UI
                layoutLoggedIn.setVisibility(View.VISIBLE);

                // ברכת שלום
                if (user.getUserType() == User.UserType.PARENT) {
                    tvWelcome.setText("שלום הורה " + user.getNickname());
                    btnShowQr.setVisibility(View.VISIBLE);

                    ParentSosPollingForegroundService.start(requireContext().getApplicationContext());

                } else {

                    tvWelcome.setText("שלום ילד " + user.getNickname());
                    btnShowQr.setVisibility(View.GONE);
                }


            }).addOnFailureListener(e -> {
                Toast.makeText(getContext(), "שגיאה בטעינת משתמש", Toast.LENGTH_SHORT).show();
            });

        } else {
            layoutLoggedIn.setVisibility(View.GONE);
            Toast.makeText(getContext(), "אין משתמש מחובר", Toast.LENGTH_SHORT).show();
        }


        //Logout button
        btnLogout.setOnClickListener(v -> {
            if (loadedUser == null) return;

            if (loadedUser.getUserType() == User.UserType.CHILD) {
                showChildLogoutDialog();
            } else {
                // הורה – התנתקות רגילה
                MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity != null) mainActivity.updateMenuByAuth();
                doLogout();
                layoutLoggedIn.setVisibility(View.GONE);
            }
        });



        btnShowQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentUser != null) {
                    String parentUid = currentUser.getUid();
                    if (parentUid == null) return;
                    showQrDialog(parentUid);
                }
            }
        });

        return view;
    }

    private void doLogout() {
        Log.d("LOGOUT", "Before signOut user=" + FirebaseAuth.getInstance().getCurrentUser());
        FirebaseAuth.getInstance().signOut();
        Log.d("LOGOUT", "After signOut user=" + FirebaseAuth.getInstance().getCurrentUser());

        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.updateMenuByAuth();
            mainActivity.selectLoginTab();
        }

        NavController nav = NavHostFragment.findNavController(this);

        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(nav.getGraph().getId(), true)
                .build();

        requireContext().stopService(new Intent(requireContext(), ChildTrackingForegroundService.class));
        requireContext().stopService(new Intent(requireContext(), ParentSosPollingForegroundService.class));

        nav.navigate(R.id.fragment_login, null, options);
    }




    private Bitmap generateQrBitmap(String parentUid) {
        try {
            String payload = "WIMC_PARENT_UID:" + parentUid;
            BarcodeEncoder encoder = new BarcodeEncoder();
            return encoder.encodeBitmap(payload, BarcodeFormat.QR_CODE, 700, 700);
        } catch (Exception  e) {
            Log.e("QR", "Failed to generate QR", e);
            return null;
        }
    }

    private void showQrDialog(String parentUid) {
        Bitmap qrBitmap = generateQrBitmap(parentUid);
        if (qrBitmap == null) {
            Toast.makeText(getContext(), "לא הצלחתי ליצור QR", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(qrBitmap);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        imageView.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("סרקו כדי לקשר או לנתק ילד")
                .setMessage("פתח הרשמה / דף הבית של הילד וסרוק את ה-QR הזה")
                .setView(imageView)
                .setPositiveButton("סגור", null)
                .show();
    }

    private void showChildLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("התנתקות ילד")
                .setMessage("כדי להתנתק יש לסרוק QR של ההורה או להקליד ידנית את הקוד (UID).")
                .setPositiveButton("סריקת QR", (d, w) -> startQrScan())
                .setNeutralButton("הקלדה ידנית", (d, w) -> showManualParentUidDialog())
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void startQrScan() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("סרוק QR של ההורה");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        qrScannerLauncher.launch(options);
    }

    private void showManualParentUidDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint("הדבק/הקלד Parent UID");

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(requireContext())
                .setTitle("הקלדה ידנית")
                .setMessage("הכנס את קוד ההורה (UID)")
                .setView(input)
                .setPositiveButton("אישור", (d, w) -> {
                    String typed = input.getText().toString().trim();
                    if (typed.isEmpty()) {
                        Toast.makeText(getContext(), "לא הוזן קוד", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    verifyParentAndLogout(typed);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private String extractParentUidFromPayload(String payload) {
        // תואם למה שאתה מייצר ב-generateQrBitmap
        // "WIMC_PARENT_UID:<uid>"
        final String prefix = "WIMC_PARENT_UID:";
        if (payload.startsWith(prefix)) {
            String uid = payload.substring(prefix.length()).trim();
            return uid.isEmpty() ? null : uid;
        }
        // אם מישהו יקליד UID בלי prefix
        return payload.isEmpty() ? null : payload;
    }

    private void verifyParentAndLogout(String providedParentUid) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String childUid = user.getUid();

        DatabaseReference parentUidRef = FirebaseDatabase.getInstance()
                .getReference("children")
                .child(childUid)
                .child("parentUid");

        parentUidRef.get().addOnSuccessListener(snapshot -> {
            String realParentUid = snapshot.getValue(String.class);

            if (realParentUid == null || realParentUid.trim().isEmpty()) {
                Toast.makeText(getContext(), "אין הורה מקושר לילד הזה", Toast.LENGTH_SHORT).show();
                return;
            }

            // השוואה נקייה
            if (!realParentUid.trim().equals(providedParentUid.trim())) {
                Toast.makeText(getContext(), "קוד הורה לא נכון", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ פה רק מתנתקים רגיל - בלי למחוק כלום ב-DB
            Toast.makeText(getContext(), "אומת בהצלחה. מתנתק...", Toast.LENGTH_SHORT).show();

            MainActivity mainActivity = (MainActivity) getActivity();
            if (mainActivity != null) mainActivity.updateMenuByAuth();

            doLogout();
            layoutLoggedIn.setVisibility(View.GONE);

        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "שגיאה באימות הורה", Toast.LENGTH_SHORT).show()
        );
    }





}