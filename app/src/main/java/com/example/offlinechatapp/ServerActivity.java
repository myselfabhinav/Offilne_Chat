package com.example.offlinechatapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerActivity extends AppCompatActivity {

    private static final String TAG = "ServerActivity";
    private static final int SERVER_PORT = 8080;
    private final File FILE_DIRECTORY = new File(getFilesDir(), "shared_files"); // Directory to store files
    private TextView txtServerStatus;
    private ServerSocket serverSocket;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);

        txtServerStatus = findViewById(R.id.tvServerStatus);

        // Create directory if it doesn't exist
        if (!FILE_DIRECTORY.exists()) {
            FILE_DIRECTORY.mkdirs();
        }

        // Start the server in a background thread
        startServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the server socket when the activity is destroyed
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
        executorService.shutdown();
    }

    private void startServer() {
        txtServerStatus.setText("Starting server...");

        executorService.execute(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                runOnUiThread(() -> txtServerStatus.setText("Server is running on port: " + SERVER_PORT));

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    Log.d(TAG, "Client connected: " + clientSocket.getInetAddress().getHostAddress());

                    // Handle each client in a separate thread
                    executorService.execute(() -> handleClient(clientSocket));
                }
            } catch (IOException e) {
                Log.e(TAG, "Error starting server", e);
                runOnUiThread(() -> txtServerStatus.setText("Error starting server: " + e.getMessage()));
            }
        });
    }

    private void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
            DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());

            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                Log.d(TAG, "Received message from client: " + clientMessage);

                if (clientMessage.startsWith("UPLOAD")) {
                    handleFileUpload(dataInputStream, clientMessage.substring(7));
                } else if (clientMessage.startsWith("DOWNLOAD")) {
                    handleFileDownload(out, clientMessage.substring(9));
                } else if (clientMessage.equals("LIST_FILES")) {
                    listFiles(out);
                } else {
                    // For demonstration, just echo the message back to the client
                    out.writeBytes("Server: " + clientMessage + "\n");
                }

                // Update the UI with the received message
                updateServerStatus("Client: " + clientMessage);
            }

            clientSocket.close();
            Log.d(TAG, "Client disconnected: " + clientSocket.getInetAddress().getHostAddress());

        } catch (IOException e) {
            Log.e(TAG, "Error handling client", e);
        }
    }

    private void handleFileUpload(DataInputStream in, String fileName) {
        try {
            long fileSize = in.readLong();
            File file = new File(FILE_DIRECTORY, fileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                while (totalBytesRead < fileSize && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }
            Log.d(TAG, "File uploaded: " + fileName);
            runOnUiThread(() -> updateServerStatus("File uploaded: " + fileName));
        } catch (IOException e) {
            Log.e(TAG, "Error uploading file", e);
        }
    }

    private void handleFileDownload(DataOutputStream out, String fileName) {
        File file = new File(FILE_DIRECTORY, fileName);
        if (file.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                out.writeLong(file.length());
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                Log.d(TAG, "File downloaded: " + fileName);
                runOnUiThread(() -> updateServerStatus("File downloaded: " + fileName));
            } catch (IOException e) {
                Log.e(TAG, "Error downloading file", e);
            }
        } else {
            try {
                out.writeLong(-1); // Indicate that the file does not exist
            } catch (IOException e) {
                Log.e(TAG, "Error sending file size", e);
            }
        }
    }

    private void listFiles(DataOutputStream out) {
        File[] files = FILE_DIRECTORY.listFiles();
        if (files != null) {
            try {
                out.writeInt(files.length);
                for (File file : files) {
                    out.writeUTF(file.getName());
                }
            } catch (IOException e) {
                Log.e(TAG, "Error listing files", e);
            }
        } else {
            try {
                out.writeInt(0);
            } catch (IOException e) {
                Log.e(TAG, "Error sending file list", e);
            }
        }
    }

    private void updateServerStatus(String message) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> txtServerStatus.append("\n" + message));
    }
}
