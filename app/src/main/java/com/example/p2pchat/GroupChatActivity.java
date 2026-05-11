package com.example.p2pchat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.p2pchat.adapter.ChatAdapter;
import com.example.p2pchat.crypto.CryptoUtils;
import com.example.p2pchat.model.Message;
import com.example.p2pchat.wifi.WifiDirectManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKey;

public class GroupChatActivity extends AppCompatActivity {
    private static final int PORT = 10000;

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText etMessage;
    private ImageButton btnSend, btnImage;
    private MaterialToolbar toolbar;

    private WifiDirectManager wifiDirectManager;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private ServerSocket serverSocket;
    private List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private boolean isGroupOwner = false;

    private SecretKey groupKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        initViews();
        initCrypto();
        initWifiDirect();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnImage = findViewById(R.id.btnImage);
        toolbar = findViewById(R.id.toolbar);

        toolbar.setTitle("群组聊天");
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ChatAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendGroupMessage());
    }

    private void initCrypto() {
        try {
            groupKey = CryptoUtils.generateKey();
            addSystemMessage("✓ 群组密钥已生成 (AES-256/GCM)");
        } catch (Exception e) {
            addSystemMessage("⚠ 加密失败");
        }
    }

    private void initWifiDirect() {
        wifiDirectManager = new WifiDirectManager(this);
        wifiDirectManager.createGroup();

        wifiDirectManager.setOnConnectionListener(new WifiDirectManager.OnConnectionListener() {
            @Override
            public void onConnected(android.net.wifi.p2p.WifiP2pInfo info) {
                isGroupOwner = info.isGroupOwner;
                if (isGroupOwner) {
                    startGroupServer();
                }
                addSystemMessage("群组成员: " + (isGroupOwner ? "Group Owner" : "成员"));
            }

            @Override
            public void onDisconnected() {
                addSystemMessage("成员已离开");
            }

            @Override
            public void onGroupFormed(android.net.wifi.p2p.WifiP2pGroup group) {
                addSystemMessage("✓ 群组已创建");
            }

            @Override
            public void onError(String error) {
                addSystemMessage("错误: " + error);
            }
        });
    }

    private void startGroupServer() {
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                addSystemMessage("群组服务器已启动，等待成员加入...");

                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clients.add(handler);
                    executor.execute(handler);
                    addSystemMessage("新成员加入: " + clientSocket.getInetAddress().getHostAddress());
                }
            } catch (IOException e) {
                mainHandler.post(() -> addSystemMessage("服务器错误: " + e.getMessage()));
            }
        });
    }

    private void sendGroupMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        Message msg = new Message(UUID.randomUUID().toString(), "self",
                "我", text, Message.TYPE_TEXT, System.currentTimeMillis(), true);
        adapter.addMessage(msg);
        scrollToBottom();
        etMessage.setText("");

        // 广播到所有客户端
        for (ClientHandler client : clients) {
            client.sendMessage(text);
        }
    }

    private void addSystemMessage(String text) {
        Message msg = new Message(UUID.randomUUID().toString(), "system",
                "系统", text, Message.TYPE_SYSTEM, System.currentTimeMillis(), false);
        adapter.addMessage(msg);
        scrollToBottom();
    }

    private void scrollToBottom() {
        recyclerView.postDelayed(() -> recyclerView.scrollToPosition(adapter.getItemCount() - 1), 100);
    }

    // ====== 客户端处理 ======

    class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    mainHandler.post(() -> {
                        try {
                            String decrypted = groupKey != null ?
                                    CryptoUtils.decrypt(msg, groupKey) : msg;
                            Message message = new Message(UUID.randomUUID().toString(),
                                    socket.getInetAddress().getHostAddress(),
                                    "群成员", decrypted, Message.TYPE_TEXT,
                                    System.currentTimeMillis(), false);
                            adapter.addMessage(message);
                            scrollToBottom();
                        } catch (Exception e) {
                            addSystemMessage("解密失败");
                        }
                    });
                }
            } catch (IOException e) {
                clients.remove(this);
                addSystemMessage("成员离开");
            }
        }

        void sendMessage(String text) {
            if (out != null) {
                try {
                    String encrypted = groupKey != null ?
                            CryptoUtils.encrypt(text, groupKey) : text;
                    out.println(encrypted);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ClientHandler client : clients) {
            try { client.socket.close(); } catch (IOException e) {}
        }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        executor.shutdown();
        if (wifiDirectManager != null) wifiDirectManager.onDestroy();
    }
}
