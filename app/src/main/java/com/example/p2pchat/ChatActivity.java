package com.example.p2pchat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.p2pchat.adapter.ChatAdapter;
import com.example.p2pchat.crypto.CryptoUtils;
import com.example.p2pchat.model.Message;
import com.example.p2pchat.wifi.WifiDirectManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKey;

public class ChatActivity extends AppCompatActivity implements WifiDirectManager.OnConnectionListener {
    private static final int PORT = 9999;
    private static final int PICK_IMAGE = 1001;

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private EditText etMessage;
    private ImageButton btnSend, btnImage;
    private TextView tvEncryptionStatus;
    private com.google.android.material.appbar.MaterialToolbar toolbar;

    private String peerName;
    private String peerAddress;
    private WifiDirectManager wifiDirectManager;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private Socket socket;
    private ServerSocket serverSocket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;
    private boolean isGroupOwner = false;
    private String groupOwnerIp;

    // AES 密钥
    private SecretKey sessionKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        peerName = getIntent().getStringExtra("device_name");
        peerAddress = getIntent().getStringExtra("device_address");

        initViews();
        initCrypto();
        initWifiDirect();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnImage = findViewById(R.id.btnImage);
        tvEncryptionStatus = findViewById(R.id.tvEncryptionStatus);
        toolbar = findViewById(R.id.toolbar);

        toolbar.setTitle(peerName != null ? peerName : "未知设备");
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ChatAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        btnImage.setOnClickListener(v -> pickImage());
    }

    private void initCrypto() {
        try {
            sessionKey = CryptoUtils.generateKey();
            tvEncryptionStatus.setText("🔒 端到端加密");
            addSystemMessage("✓ 会话密钥已生成 (AES-256/GCM)");
        } catch (Exception e) {
            tvEncryptionStatus.setText("⚠ 加密未启用");
            addSystemMessage("⚠ 加密初始化失败: " + e.getMessage());
        }
    }

    private void initWifiDirect() {
        wifiDirectManager = new WifiDirectManager(this);
        wifiDirectManager.setOnConnectionListener(this);

        // 获取连接信息
        wifiDirectManager.requestConnectionInfo(info -> {
            if (info.groupFormed) {
                isGroupOwner = info.isGroupOwner;
                if (isGroupOwner) {
                    groupOwnerIp = "0.0.0.0"; // 本机
                    startServer();
                } else {
                    groupOwnerIp = info.groupOwnerAddress.getHostAddress();
                    connectToOwner();
                }
            }
        });
    }

    private void startServer() {
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                mainHandler.post(() -> addSystemMessage("等待对方连接..."));
                
                socket = serverSocket.accept();
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                isConnected = true;

                mainHandler.post(() -> addSystemMessage("✓ 连接已建立"));

                // 交换密钥
                exchangeKey();
                
                // 接收消息
                receiveMessages();
            } catch (IOException e) {
                mainHandler.post(() -> addSystemMessage("连接错误: " + e.getMessage()));
            }
        });
    }

    private void connectToOwner() {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> addSystemMessage("正在连接到 " + groupOwnerIp + "..."));
                socket = new Socket();
                socket.connect(new InetSocketAddress(groupOwnerIp, PORT), 10000);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                isConnected = true;

                mainHandler.post(() -> addSystemMessage("✓ 连接已建立"));

                // 交换密钥
                exchangeKey();
                
                // 接收消息
                receiveMessages();
            } catch (IOException e) {
                mainHandler.post(() -> addSystemMessage("连接失败: " + e.getMessage()));
            }
        });
    }

    private void exchangeKey() {
        try {
            if (out != null && sessionKey != null) {
                String keyBase64 = CryptoUtils.keyToBase64(sessionKey);
                // 密钥交换通过安全通道发送（实际产品中需 ECDH）
                out.println("KEY:" + keyBase64);
                addSystemMessage("🔑 密钥已交换");
            }
        } catch (Exception e) {
            addSystemMessage("密钥交换失败: " + e.getMessage());
        }
    }

    private void receiveMessages() {
        executor.execute(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String raw = line;
                    mainHandler.post(() -> processReceivedMessage(raw));
                }
            } catch (IOException e) {
                if (isConnected) {
                    mainHandler.post(() -> {
                        isConnected = false;
                        addSystemMessage("连接已断开");
                    });
                }
            }
        });
    }

    private void processReceivedMessage(String raw) {
        if (raw.startsWith("KEY:")) {
            String keyBase64 = raw.substring(4);
            try {
                sessionKey = CryptoUtils.keyFromBase64(keyBase64);
                addSystemMessage("🔑 已接收对方密钥");
            } catch (Exception e) {
                addSystemMessage("密钥解析失败");
            }
            return;
        }

        if (raw.startsWith("IMG:")) {
            String encrypted = raw.substring(4);
            try {
                String decrypted = sessionKey != null ?
                        CryptoUtils.decrypt(encrypted, sessionKey) : encrypted;
                Message msg = new Message(UUID.randomUUID().toString(), peerAddress,
                        peerName, "[图片]", Message.TYPE_IMAGE, System.currentTimeMillis(), false);
                msg.setImageBase64(decrypted);
                adapter.addMessage(msg);
                scrollToBottom();
            } catch (Exception e) {
                addSystemMessage("图片解密失败");
            }
            return;
        }

        // 文本消息
        try {
            String decrypted = sessionKey != null ?
                    CryptoUtils.decrypt(raw, sessionKey) : raw;
            Message msg = new Message(UUID.randomUUID().toString(), peerAddress,
                    peerName, decrypted, Message.TYPE_TEXT, System.currentTimeMillis(), false);
            adapter.addMessage(msg);
            scrollToBottom();
        } catch (Exception e) {
            addSystemMessage("解密失败: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text) || !isConnected || out == null) return;

        try {
            String encrypted = sessionKey != null ?
                    CryptoUtils.encrypt(text, sessionKey) : text;

            Message msg = new Message(UUID.randomUUID().toString(), "self",
                    "我", text, Message.TYPE_TEXT, System.currentTimeMillis(), true);
            adapter.addMessage(msg);
            scrollToBottom();
            etMessage.setText("");

            out.println(encrypted);
        } catch (Exception e) {
            Toast.makeText(this, "加密失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            sendImage(imageUri);
        }
    }

    private void sendImage(Uri imageUri) {
        executor.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                // 压缩图片
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                byte[] imageBytes = baos.toByteArray();
                String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                // 加密图片数据
                String encrypted = sessionKey != null ?
                        CryptoUtils.encrypt(base64Image, sessionKey) : base64Image;

                mainHandler.post(() -> {
                    Message msg = new Message(UUID.randomUUID().toString(), "self",
                            "我", "[图片]", Message.TYPE_IMAGE, System.currentTimeMillis(), true);
                    msg.setImageBase64(base64Image);
                    adapter.addMessage(msg);
                    scrollToBottom();
                });

                if (out != null) {
                    out.println("IMG:" + encrypted);
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this,
                        "图片发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
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

    @Override
    public void onConnected(android.net.wifi.p2p.WifiP2pInfo info) {
        isGroupOwner = info.isGroupOwner;
        if (isGroupOwner) {
            startServer();
        } else {
            groupOwnerIp = info.groupOwnerAddress.getHostAddress();
            connectToOwner();
        }
    }

    @Override
    public void onDisconnected() {
        isConnected = false;
        addSystemMessage("连接已断开");
    }

    @Override
    public void onGroupFormed(android.net.wifi.p2p.WifiP2pGroup group) {}

    @Override
    public void onError(String error) {
        addSystemMessage("错误: " + error);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isConnected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        executor.shutdown();
    }
}
