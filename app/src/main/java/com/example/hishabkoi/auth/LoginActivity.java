package com.example.hishabkoi.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.MainActivity;
import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Session;
import com.example.hishabkoi.util.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private TextInputLayout tilEmail, tilPassword;
    private EditText etEmail, etPassword;
    private View progress;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Session.isLoggedIn()) {
            openHome();
            return;
        }
        setContentView(R.layout.activity_login);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        progress = findViewById(R.id.progress);
        btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> login());
        findViewById(R.id.btn_signup).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
        findViewById(R.id.btn_forgot).setOnClickListener(v -> resetPassword());
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        tilEmail.setError(Patterns.EMAIL_ADDRESS.matcher(email).matches() ? null : getString(R.string.err_email));
        tilPassword.setError(password.length() >= 6 ? null : getString(R.string.err_password));
        if (tilEmail.getError() != null || tilPassword.getError() != null) return;

        setBusy(true);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    // Refresh so a link clicked after the last sign-in is picked up.
                    user.reload().addOnCompleteListener(t -> {
                        if (user.isEmailVerified()) {
                            // New token so Firestore rules see email_verified == true.
                            user.getIdToken(true).addOnCompleteListener(x -> loadProfileThenOpen(user.getUid()));
                        } else {
                            setBusy(false);
                            showNotVerified(user);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Ui.toast(this, Ui.message(e));
                });
    }

    private void loadProfileThenOpen(String uid) {
        Bg.run(this, () -> Cloud.usernameOf(uid), username -> {
            if (username != null) Session.setUsername(this, username);
            openHome();
        }, () -> setBusy(false));
    }

    /** Unverified users are never left signed in; the dialog signs them out either way. */
    private void showNotVerified(FirebaseUser user) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.verify_title)
                .setMessage(getString(R.string.verify_needed, user.getEmail()))
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (d, w) -> FirebaseAuth.getInstance().signOut())
                .setNeutralButton(R.string.resend_email, (d, w) ->
                        user.sendEmailVerification().addOnCompleteListener(t -> {
                            Ui.toast(this, t.isSuccessful() ? getString(R.string.email_resent)
                                    : Ui.message(t.getException()));
                            FirebaseAuth.getInstance().signOut();
                        }))
                .show();
    }

    private void resetPassword() {
        String email = etEmail.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.err_email));
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(t ->
                Ui.toast(this, t.isSuccessful() ? getString(R.string.reset_sent, email) : Ui.message(t.getException())));
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        btnLogin.setEnabled(!busy);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
