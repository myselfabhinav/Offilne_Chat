package com.example.offlinechatapp;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientListActivity extends AppCompatActivity {

    private ListView lvClients;
    private Button btnStartChatting;
    private Button btnFileSharing;
    private String username;
    private List<String> clients;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_list);

        lvClients = findViewById(R.id.lvClients);
        btnStartChatting = findViewById(R.id.btnStartChatting);
        btnFileSharing = findViewById(R.id.btnFileSharing);
        username = getIntent().getStringExtra("username");

        // Initialize the clients list
        clients = new ArrayList<>();

        // Initialize executor service for background tasks
        executorService = Executors.newFixedThreadPool(4);

        // Get connected clients in the background
        executorService.execute(() -> {
            List<String> connectedClients = getConnectedClients();

            // Update UI with the list of connected clients
            runOnUiThread(() -> {
                if (connectedClients == null || connectedClients.isEmpty()) {
                    Toast.makeText(ClientListActivity.this, "No clients connected.", Toast.LENGTH_SHORT).show();
                } else {
                    clients.addAll(connectedClients);
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(ClientListActivity.this, android.R.layout.simple_list_item_1, clients);
                    lvClients.setAdapter(adapter);
                }
            });
        });

        lvClients.setOnItemClickListener((parent, view, position, id) -> {
            String selectedClient = clients.get(position);
            // Send a connection request to the selected client
            sendConnectionRequest(selectedClient);
        });
        btnFileSharing.setOnClickListener(v -> {
            Intent intent = new Intent(ClientListActivity.this, FileSharingActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });
        btnStartChatting.setOnClickListener(v -> {
            if (clients.isEmpty()) {
                Toast.makeText(this, "No clients connected. Unable to start chat.", Toast.LENGTH_SHORT).show();
            } else {
                // Start chatting with the first client in the list or modify as needed
                Intent intent = new Intent(ClientListActivity.this, ChatActivity.class);
                intent.putExtra("clients", new ArrayList<>(clients));
                intent.putExtra("username", username);
                startActivity(intent);
            }
        });
    }

    private List<String> getConnectedClients() {
        List<String> clients = new ArrayList<>();
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();

        if (dhcpInfo == null) {
            return clients; // Return empty list if DhcpInfo is null
        }

        String serverIp = formatIpAddress(dhcpInfo.serverAddress);
        String prefix = serverIp.substring(0, serverIp.lastIndexOf('.') + 1);

        for (int i = 1; i < 255; i++) {
            String testIp = prefix + i;
            try {
                InetAddress address = InetAddress.getByName(testIp);
                if (address.isReachable(100)) {
                    clients.add(testIp);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return clients;
    }

    private void sendConnectionRequest(String clientIp) {
        executorService.execute(() -> {
            Socket socket = null;
            try {
                int port = 8080;
                socket = new Socket(clientIp, port);
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

                String message = "Connection request from " + username;
                dataOutputStream.writeUTF(message);

                dataOutputStream.flush();
                runOnUiThread(() ->
                        Toast.makeText(ClientListActivity.this, "Connection request sent to " + clientIp, Toast.LENGTH_SHORT).show()
                );
            } catch (IOException e) {
                e.printStackTrace();
                String errorMessage;
                if (e instanceof java.net.ConnectException) {
                    errorMessage = "Connection refused by " + clientIp;
                } else {
                    errorMessage = "Failed to send request to " + clientIp;
                }
                runOnUiThread(() ->
                        Toast.makeText(ClientListActivity.this, errorMessage, Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }



    private String formatIpAddress(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 24) & 0xFF);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the executor service when activity is destroyed
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
