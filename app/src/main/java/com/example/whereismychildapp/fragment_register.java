package com.example.whereismychildapp;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.whereismychildapp.Objects.User;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.activity.result.ActivityResultLauncher;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link fragment_register#newInstance} factory method to
 * create an instance of this fragment.
 */
public class fragment_register extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private TextInputLayout tilParentCode;
    private RadioGroup rgUserType;

    private TextInputEditText etEmail, etPassword,etNickName,etParentCode;

    private Button btnRegister;
    private boolean isParentOrChildIsFilled;


    public fragment_register() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment fragment_register.
     */
    // TODO: Rename and change types and number of parameters
    public static fragment_register newInstance(String param1, String param2) {
        fragment_register fragment = new fragment_register();
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

        View view = inflater.inflate(R.layout.fragment_register, container, false);

        tilParentCode = view.findViewById(R.id.tilParentCode);
        rgUserType = view.findViewById(R.id.rgUserType);
        btnRegister = view.findViewById(R.id.btnRegister);
        etPassword = view.findViewById(R.id.etPasswordR);
        etParentCode = view.findViewById(R.id.etParentCode);
        etNickName = view.findViewById(R.id.etNicknameR);
        etEmail = view.findViewById(R.id.etEmailR);

        rgUserType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbChild) {
                tilParentCode.setVisibility(View.VISIBLE);
            } else {
                tilParentCode.setVisibility(View.GONE);
                etParentCode.setText("");
            }
            isParentOrChildIsFilled = true;
        });

        // Move to login if user exists
        TextView tvGoToRegister = view.findViewById(R.id.tvGoToLogin);
        tvGoToRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Navigation.findNavController(view).navigate(R.id.action_fragment_register_to_fragment_login);
                NavHostFragment.findNavController(fragment_register.this).navigate(R.id.fragment_login);

            }
        });

        // registration
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                String ParentUID = etParentCode.getText().toString().trim();
                String nickname = etNickName.getText().toString().trim();

                boolean isChild = !ParentUID.isEmpty();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(getContext(),
                            "יש למלא אימייל וסיסמה",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (nickname.isEmpty()) {
                    Toast.makeText(getContext(),
                            "יש למלא כינוי",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isParentOrChildIsFilled) {
                    Toast.makeText(getContext(),
                            "יש למלא הורה או ילד",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                int checkedId = rgUserType.getCheckedRadioButtonId();
                boolean isChildSelected = (checkedId == R.id.rbChild);




                if (isChildSelected && ParentUID.isEmpty()) {
                    Toast.makeText(getContext(), "יש לסרוק QR של הורה או להזין קוד", Toast.LENGTH_LONG).show();
                    return;
                }

                if(isChildSelected) {
                    if (ParentUID.length() < 10) {
                        Toast.makeText(getContext(), "קוד קצר מדי", Toast.LENGTH_SHORT).show();

                    }
                    else
                        validateParentCodeThenRegister(email,password,nickname,ParentUID);
                }
                else {
                    MainActivity mainActivity = (MainActivity) getActivity();
                    mainActivity.register(email, password, nickname, isChildSelected ? ParentUID : "");
                }
            }
        });

        tilParentCode.setEndIconOnClickListener(v -> {
            startQrScan();
        });
        tilParentCode.setEndIconVisible(true);
        tilParentCode.setEndIconActivated(true);

        return view;
    }

    private void validateParentCodeThenRegister(String email, String password, String nickname, String parentUid) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(parentUid)
                .child("userType");

        userRef.get().addOnSuccessListener(snapshot -> {
            String typeStr = snapshot.getValue(String.class);

            if (typeStr == null) {
                Toast.makeText(getContext(), "קוד הורה לא נמצא", Toast.LENGTH_LONG).show();
                return;
            }

            // אימות שזה באמת הורה
            if (!typeStr.equals(User.UserType.PARENT.name())) {
                Toast.makeText(getContext(), "הקוד לא שייך להורה", Toast.LENGTH_LONG).show();
                return;
            }

            //  תקין ממשיכים לרישום
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.register(email, password, nickname, parentUid);

        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "שגיאה באימות הקוד", Toast.LENGTH_LONG).show();
        });
    }

    private void startQrScan() {
        tilParentCode.setError(null);
        tilParentCode.setErrorEnabled(false);

        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("סרוק QR של ההורה");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrLauncher.launch(options);
    }


    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    Toast.makeText(getContext(), "סריקה בוטלה", Toast.LENGTH_SHORT).show();
                    return;
                }

                String contents = result.getContents().trim();

                String prefix = "WIMC_PARENT_UID:";
                String parentUid = contents.startsWith(prefix)
                        ? contents.substring(prefix.length()).trim()
                        : contents;

                if (parentUid.isEmpty()) {
                    Toast.makeText(getContext(), "QR לא תקין", Toast.LENGTH_SHORT).show();
                    return;
                }

                etParentCode.setText(parentUid);
                Toast.makeText(getContext(), "קוד הורה הוזן ✅", Toast.LENGTH_SHORT).show();
            });

}