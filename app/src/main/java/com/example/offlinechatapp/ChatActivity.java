package com.example.offlinechatapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatActivity extends AppCompatActivity {

    private TextView txtChatDisplay;
    private EditText edtMessage;
    private Button btnSendMessage;

    private String connectedClientIp;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private static final int PORT = 8080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        txtChatDisplay = findViewById(R.id.txtChatDisplay);
        edtMessage = findViewById(R.id.edtMessage);
        btnSendMessage = findViewById(R.id.btnSendMessage);

        connectedClientIp = getIntent().getStringExtra("clientIp");

        if (connectedClientIp != null) {
            // Start the client connection in a background thread
            new Thread(this::startClient).start();
        } else {
            // Start the server to listen for incoming connections
            new Thread(this::startServer).start();
        }

        // Implement chat logic
        btnSendMessage.setOnClickListener(v -> {
            String message = edtMessage.getText().toString().trim();
            if (!message.isEmpty()) {
                // Display the message in the chat window
                txtChatDisplay.append("Me: " + message + "\n");

                // Clear the input field
                edtMessage.setText("");

                // Send the message to the connected client
                sendMessageToClient(message);
            }
        });
    }

    private void startServer() {
        try {
            // Create a server socket to listen for incoming connections
            serverSocket = new ServerSocket(PORT);
            runOnUiThread(() -> txtChatDisplay.append("Server is listening on port: " + PORT + "\n"));
            clientSocket = serverSocket.accept(); // Accept the connection from the client

            // Setup input and output streams
            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            // Listen for incoming messages
            listenForMessages();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startClient() {
        try {
            // Connect to the server (client mode)
            clientSocket = new Socket(connectedClientIp, PORT);
            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            // Listen for incoming messages
            listenForMessages();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToClient(String message) {
        new Thread(() -> {
            try {
                if (dataOutputStream != null) {
                    // Send the message to the connected client
                    dataOutputStream.writeUTF(message);
                    dataOutputStream.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void listenForMessages() {
        try {
            while (true) {
                // Read the incoming message from the client
                String message = dataInputStream.readUTF();

                // Display the message in the chat window
                runOnUiThread(() -> txtChatDisplay.append("Client: " + message + "\n"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (dataInputStream != null) {
                dataInputStream.close();
            }
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
