package com.example.p2pchat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.p2pchat.adapter.DeviceAdapter;
import com.example.p2pchat.model.PeerDevice;
import com.example.p2pchat.wifi.WifiDirectManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements WifiDirectManager.OnPeerDiscoveryListener {
    private RecyclerView recyclerView;
    private DeviceAdapter adapter;
    private ProgressBar progressScan;
    private TextView tvStatus, tvEmpty;
    private MaterialButton btnCreateGroup;
    private WifiDirectManager wifiDirectManager;
    private List<PeerDevice> deviceList = new ArrayList<>();

    // 权限请求
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initPermissions();
        wifiDirectManager = new WifiDirectManager(this);
        wifiDirectManager.setOnPeerDiscoveryListener(this);
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        progressScan = findViewById(R.id.progressScan);
        tvStatus = findViewById(R.id.tvStatus);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnCreateGroup = findViewById(R.id.btnCreateGroup);

        adapter = new DeviceAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnDeviceClickListener(device -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("device_name", device.getDisplayName());
            intent.putExtra("device_address", device.getDeviceAddress());
            startActivity(intent);
        });

        btnCreateGroup.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GroupChatActivity.class);
            startActivity(intent);
        });

        tvStatus.setText(getString(R.string.scanning));
    }

    private void initPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        startP2PDiscovery();
                    } else {
                        Toast.makeText(this, "需要位置权限才能发现设备", Toast.LENGTH_LONG).show();
                    }
                });

        requestPermissionsIfNeeded();
    }

    private void requestPermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }

        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        } else {
            startP2PDiscovery();
        }
    }

    private void startP2PDiscovery() {
        wifiDirectManager.startDiscovery();
    }

    // ====== 发现回调 ======

    @Override
    public void onPeersUpdated(List<PeerDevice> peers) {
        deviceList = peers;
        adapter.updateDevices(peers);

        if (peers.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDiscoveryStarted() {
        tvStatus.setText("正在搜索附近设备…");
        progressScan.setVisibility(View.VISIBLE);
    }

    @Override
    public void onDiscoveryStopped() {
        tvStatus.setText("搜索已停止");
        progressScan.setVisibility(View.GONE);
    }

    @Override
    public void onError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        tvStatus.setText("错误: " + error);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wifiDirectManager != null) {
            wifiDirectManager.startDiscovery();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wifiDirectManager != null) {
            wifiDirectManager.stopDiscovery();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiDirectManager != null) {
            wifiDirectManager.onDestroy();
        }
    }
}
