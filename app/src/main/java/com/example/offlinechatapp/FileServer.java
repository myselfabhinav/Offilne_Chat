package com.example.offlinechatapp;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("Server is listening on port 8080");

        while (true) {
            try (Socket socket = serverSocket.accept();
                 DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                 DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {

                String command = dataInputStream.readUTF();
                if (command.startsWith("UPLOAD")) {
                    String fileName = command.substring(7);
                    long fileSize = dataInputStream.readLong();
                    File file = new File("uploaded_files/" + fileName);
                    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        long totalBytesRead = 0;
                        int bytesRead;
                        while (totalBytesRead < fileSize && (bytesRead = dataInputStream.read(buffer)) != -1) {
                            fileOutputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        }
                    }
                    dataOutputStream.writeUTF("Upload successful");

                } else if (command.equals("LIST_FILES")) {
                    File folder = new File("uploaded_files");
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
                    File file = new File("uploaded_files/" + fileName);
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
