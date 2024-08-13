package com.example.offlinechatapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button btnStartServer, btnGoAsClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStartServer = findViewById(R.id.btnStartServer);
        btnGoAsClient = findViewById(R.id.btnGoAsClient);

        btnStartServer.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ServerActivity.class);
            startActivity(intent);
        });

        btnGoAsClient.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UsernameActivity.class);
            startActivity(intent);
        });
    }
}
