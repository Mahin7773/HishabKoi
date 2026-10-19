package com.example.hishabkoi.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hishabkoi.R;
import com.example.hishabkoi.cloud.Cloud;
import com.example.hishabkoi.util.Bg;
import com.example.hishabkoi.util.Ui;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class SignupActivity extends AppCompatActivity {
    public static final String USERNAME_PATTERN = "^[a-z0-9_]{3,20}$";

    private TextInputLayout tilUsername, tilEmail, tilPassword, tilConfirm;
    private EditText etUsername, etEmail, etPassword, etConfirm;
    private View progress;
    private Button btnSignup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        tilUsername = findViewById(R.id.til_username);
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        tilConfirm = findViewById(R.id.til_confirm);
        etUsername = findViewById(R.id.et_username);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm);
        progress = findViewById(R.id.progress);
        btnSignup = findViewById(R.id.btn_signup);

        btnSignup.setOnClickListener(v -> signup());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void signup() {
        String username = etUsername.getText().toString().trim().toLowerCase(Locale.US);
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm = etConfirm.getText().toString();

        tilUsername.setError(username.matches(USERNAME_PATTERN) ? null : getString(R.string.err_username));
        tilEmail.setError(Patterns.EMAIL_ADDRESS.matcher(email).matches() ? null : getString(R.string.err_email));
        tilPassword.setError(password.length() >= 6 ? null : getString(R.string.err_password));
        tilConfirm.setError(password.equals(confirm) ? null : getString(R.string.err_password_match));
        if (tilUsername.getError() != null || tilEmail.getError() != null
                || tilPassword.getError() != null || tilConfirm.getError() != null) return;

        setBusy(true);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Bg.run(this, () -> {
            if (Cloud.isUsernameTaken(username)) return false;
            FirebaseUser user = Tasks.await(auth.createUserWithEmailAndPassword(email, password)).getUser();
            try {
                Cloud.reserveUsername(user.getUid(), username, email);
            } catch (Exception e) {
                // Someone grabbed the name in between; don't leave an account without a username.
                Tasks.await(user.delete());
                throw e;
            }
            Tasks.await(user.sendEmailVerification());
            auth.signOut();
            return true;
        }, created -> {
            setBusy(false);
            if (!created) {
                tilUsername.setError(getString(R.string.err_username_taken));
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.verify_title)
                    .setMessage(getString(R.string.verify_sent, email))
                    .setPositiveButton(R.string.ok, (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }, () -> setBusy(false));
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        btnSignup.setEnabled(!busy);
    }
}
