package com.example.offlinechatapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class UsernameActivity extends AppCompatActivity {

    private EditText edtUsername;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_username);

        edtUsername = findViewById(R.id.edtUsername);
        btnSubmit = findViewById(R.id.btnSubmit);

        btnSubmit.setOnClickListener(v -> {
            String username = edtUsername.getText().toString().trim();
            if (!username.isEmpty()) {
                Intent intent = new Intent(UsernameActivity.this, ClientListActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
            }
        });
    }
}
