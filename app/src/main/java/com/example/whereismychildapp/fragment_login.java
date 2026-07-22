package com.example.whereismychildapp;

import static android.view.View.INVISIBLE;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link fragment_login#newInstance} factory method to
 * create an instance of this fragment.
 */
public class fragment_login extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private Button btnLogin;
    private TextView tvGoToRegister;
    private FirebaseUser currentUser;

    private LinearLayout layoutLogin, layoutLoggedIn;


    private TextInputEditText etEmail, etPassword;

    public fragment_login() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment fragment_login.
     */
    // TODO: Rename and change types and number of parameters
    public static fragment_login newInstance(String param1, String param2) {
        fragment_login fragment = new fragment_login();
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
        View view = inflater.inflate(R.layout.fragment_login, container, false);
        currentUser = FirebaseAuth.getInstance().getCurrentUser();


        tvGoToRegister = view.findViewById(R.id.tvGoToRegister);
        btnLogin = view.findViewById(R.id.btnLogin);

        etPassword = view.findViewById(R.id.etPasswordL);
        etEmail = view.findViewById(R.id.etEmailL);

        layoutLogin = view.findViewById(R.id.layoutLogin);
        layoutLoggedIn = view.findViewById(R.id.layoutLoggedIn);


        if(currentUser != null) // User is already online
        {
            layoutLogin.setVisibility(View.GONE);
            layoutLoggedIn.setVisibility(View.VISIBLE);
            String email = currentUser.getEmail();

        }
        else {
            // User isn't online
            layoutLogin.setVisibility(View.VISIBLE);
            layoutLoggedIn.setVisibility(View.GONE);
        }

        tvGoToRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               // Navigation.findNavController(view).navigate(R.id.action_fragment_login_to_fragment_register);
                NavHostFragment.findNavController(fragment_login.this).navigate(R.id.fragment_register);
            }
        });

        // Login
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(getContext(),
                            "יש למלא אימייל וסיסמה",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.login(email,password);
            }
        });




        return view;
    }
}