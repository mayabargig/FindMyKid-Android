package com.example.whereismychildapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.whereismychildapp.Objects.User;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private NavHostFragment navHostFragment;
    private NavController navController;

    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNav;
    private FirebaseAuth.AuthStateListener authListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        loadMenu();

        authListener = firebaseAuth -> {
            boolean isLoggedIn = firebaseAuth.getCurrentUser() != null;


            updateMenuByAuth();

            // אם אין משתמש מחובר – תמיד תוודא שאנחנו במסך login
            if (!isLoggedIn && navController != null) {
                bottomNav.setSelectedItemId(R.id.fragment_login);
            }
        };

        mAuth.addAuthStateListener(authListener);

        // ✅ אחרי שה-navController כבר מוכן
        handleSosIntentIfExists(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // ✅ כשהאפליקציה כבר פתוחה ומגיעה התראה חדשה
        handleSosIntentIfExists(intent);
    }

    private void loadMenu() {
        bottomNav = findViewById(R.id.bottom_navigation);

        navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainerView1);

        navController = navHostFragment.getNavController();

        NavigationUI.setupWithNavController(bottomNav, navController);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.fragment_home) {
                NavOptions opts = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.fragment_home, false)
                        .build();
                navController.navigate(R.id.fragment_home, null, opts);
                return true;
            }

            if (id == R.id.fragment_profile) {
                NavOptions opts = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build();
                navController.navigate(R.id.fragment_profile, null, opts);
                return true;
            }

            if (id == R.id.fragment_login) {
                NavOptions opts = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .build();
                navController.navigate(R.id.fragment_login, null, opts);
                return true;
            }


            return false;
        });

        updateMenuByAuth();
    }
    public void selectLoginTab() {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.fragment_login);
        }
    }


    public void updateMenuByAuth() {
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        bottomNav.getMenu().findItem(R.id.fragment_login).setVisible(!isLoggedIn);
        bottomNav.getMenu().findItem(R.id.fragment_profile).setVisible(isLoggedIn);
    }

    // ===================== AUTH =====================

    public void login(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "success login", Toast.LENGTH_SHORT).show();
                        updateMenuByAuth();

                        NavOptions opts = new NavOptions.Builder()
                                .setPopUpTo(navController.getGraph().getId(), false)
                                .setLaunchSingleTop(true)
                                .build();
                        navController.navigate(R.id.fragment_profile, null, opts);

                        bottomNav.setSelectedItemId(R.id.fragment_profile);
                    } else {
                        Toast.makeText(MainActivity.this, "details are wrong", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void register(String email, String password, String nickname, String parentCode) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "success register", Toast.LENGTH_SHORT).show();
                        updateMenuByAuth();

                        writeToDBNewStructure(email, nickname, parentCode);

                        NavOptions opts = new NavOptions.Builder()
                                .setPopUpTo(navController.getGraph().getId(), false)
                                .setLaunchSingleTop(true)
                                .build();
                        navController.navigate(R.id.fragment_profile, null, opts);

                        bottomNav.setSelectedItemId(R.id.fragment_profile);
                    } else {
                        String message = "שגיאה בהרשמה";
                        Exception e = task.getException();

                        if (e instanceof FirebaseAuthUserCollisionException) {
                            message = "האימייל כבר רשום במערכת";
                        } else if (e instanceof FirebaseAuthWeakPasswordException) {
                            message = "הסיסמה חלשה מדי (לפחות 6 תווים)";
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            message = "כתובת האימייל לא חוקית";
                        } else if (e != null) {
                            message = e.getLocalizedMessage();
                        }

                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void writeToDBNewStructure(String email, String nickname, String parentCode) {
        String uid = mAuth.getUid();
        if (uid == null) return;

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        Map<String, Object> updates = new HashMap<>();

        boolean isChild = parentCode != null && !parentCode.trim().isEmpty();

        if (isChild) {
            String parentUid = parentCode.trim();

            User childUser = new User(email, nickname, uid, User.UserType.CHILD);
            updates.put("users/" + uid, childUser);

            updates.put("children/" + uid + "/parentUid", parentUid);
            updates.put("parents/" + parentUid + "/childrenUids/" + uid, true);

        } else {
            User parentUser = new User(email, nickname, uid, User.UserType.PARENT);
            updates.put("users/" + uid, parentUser);

            updates.put("parents/" + uid + "/notificationsEnabled", true);
        }

        rootRef.updateChildren(updates)
                .addOnSuccessListener(unused -> Log.d("DB", "Saved successfully (new structure)"))
                .addOnFailureListener(e -> Log.e("DB", "Failed to save (new structure)", e));
    }

    // ===================== NOTIFICATIONS =====================
    private void handleSosIntentIfExists(Intent intent) {
        if (intent == null) return;

        String childUid = intent.getStringExtra("sos_childUid");
        String requestId = intent.getStringExtra("sos_requestId");
        if (childUid == null || requestId == null) return;

        Bundle b = new Bundle();
        b.putString("sos_childUid", childUid);
        b.putString("sos_requestId", requestId);
        b.putDouble("sos_lat", intent.getDoubleExtra("sos_lat", 0));
        b.putDouble("sos_lng", intent.getDoubleExtra("sos_lng", 0));
        b.putString("sos_note", intent.getStringExtra("sos_note"));
        b.putString("sos_childName", intent.getStringExtra("sos_childName"));

        // ✅ נווט ל-Home עם הנתונים
        NavOptions opts = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .build();

        navController.navigate(R.id.fragment_home, b, opts);

        // ✅ לסנכרן את התפריט למצב HOME
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.fragment_home);

        // ✅ כדי שלא ינווט שוב ושוב
        intent.removeExtra("sos_childUid");
        intent.removeExtra("sos_requestId");
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (authListener != null) {
            mAuth.removeAuthStateListener(authListener);
        }
    }

}
