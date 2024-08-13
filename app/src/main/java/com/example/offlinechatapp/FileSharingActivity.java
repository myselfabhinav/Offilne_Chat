package com.example.offlinechatapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class FileSharingActivity extends AppCompatActivity {

    private ListView lvFiles;
    private Button btnUploadFile;
    private List<String> files;
    private static final int SERVER_PORT = 8080;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isServerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_sharing);

        lvFiles = findViewById(R.id.lvSharedFiles);
        btnUploadFile = findViewById(R.id.btnUploadFile);
        files = new ArrayList<>();

        btnUploadFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 1);
        });

        lvFiles.setOnItemClickListener((parent, view, position, id) -> {
            String selectedFile = files.get(position);
            downloadFile(selectedFile);
        });

        // Start the file server in a separate thread
        startServer();

        // Populate file list from the server or local storage
        populateFileList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            uploadFile(fileUri);
        }
    }

    private void startServer() {
        if (isServerRunning) return;
        isServerRunning = true;
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                while (isServerRunning) {
                    Socket socket = serverSocket.accept();
                    new Thread(new FileServerHandler(socket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
    }

    private void stopServer() {
        isServerRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadFile(Uri fileUri) {
        new Thread(() -> {
            File file = new File(getCacheDir(), getFileName(fileUri));
            try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
                 FileOutputStream outputStream = new FileOutputStream(file)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                // Notify the server that a file is uploaded
                runOnUiThread(() -> Toast.makeText(this, "File uploaded successfully.", Toast.LENGTH_SHORT).show());
                populateFileList();

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "File handling failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void downloadFile(String fileName) {
        new Thread(() -> {
            File file = new File(getExternalFilesDir(null), fileName);
            try (Socket socket = new Socket("localhost", SERVER_PORT);
                 DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(file);
                 DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

                dataOutputStream.writeUTF("DOWNLOAD " + fileName);

                long fileSize = dataInputStream.readLong();
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                while (totalBytesRead < fileSize && (bytesRead = dataInputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                runOnUiThread(() -> Toast.makeText(this, "File downloaded successfully.", Toast.LENGTH_SHORT).show());

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "File download failed.", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void populateFileList() {
        File folder = new File(getCacheDir().toURI());
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            runOnUiThread(() -> {
                files.clear();
                for (File file : listOfFiles) {
                    files.add(file.getName());
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, files);
                lvFiles.setAdapter(adapter);
            });
        }
    }

    private String getFileName(Uri uri) {
        String[] projection = {DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return uri.getLastPathSegment();
    }

    private class FileServerHandler implements Runnable {
        private final Socket socket;

        public FileServerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                 DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {

                String command = dataInputStream.readUTF();
                if (command.startsWith("UPLOAD")) {
                    String fileName = command.substring(7);
                    long fileSize = dataInputStream.readLong();
                    File file = new File(getCacheDir(), fileName);
                    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        long totalBytesRead = 0;
                        int bytesRead;
                        while (totalBytesRead < fileSize && (bytesRead = dataInputStream.read(buffer)) != -1) {
                            fileOutputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        }
                        dataOutputStream.writeUTF("Upload successful");
                    }
                } else if (command.equals("LIST_FILES")) {
                    File folder = new File(getCacheDir().toURI());
                    File[] listOfFiles = folder.listFiles();
                    if (listOfFiles != null) {
                        dataOutputStream.writeInt(listOfFiles.length);
                        for (File file : listOfFiles) {
                            dataOutputStream.writeUTF(file.getName());
                        }
                    } else {
                        dataOutputStream.writeInt(0);
                    }
                } else if (command.startsWith("DOWNLOAD")) {
                    String fileName = command.substring(9);
                    File file = new File(getCacheDir(), fileName);
                    if (file.exists()) {
                        dataOutputStream.writeLong(file.length());
                        try (FileInputStream fileInputStream = new FileInputStream(file)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                                dataOutputStream.write(buffer, 0, bytesRead);
                            }
                        }
                    } else {
                        dataOutputStream.writeLong(0);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
