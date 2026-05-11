package com.example.p2pchat.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Build;
import android.util.Log;

import com.example.p2pchat.model.PeerDevice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiDirectManager {
    private static final String TAG = "WifiDirectManager";
    private static final String SERVICE_TYPE = "_p2pchat._tcp";

    private Context context;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private boolean isWifiP2pEnabled = false;
    private boolean isDiscovering = false;

    private OnPeerDiscoveryListener peerDiscoveryListener;
    private OnConnectionListener connectionListener;
    private List<PeerDevice> peerDevices = new ArrayList<>();

    private boolean retryChannel = false;
    private int bindCount = 0;

    // ========== 接口定义 ==========

    public interface OnPeerDiscoveryListener {
        void onPeersUpdated(List<PeerDevice> peers);
        void onDiscoveryStarted();
        void onDiscoveryStopped();
        void onError(String error);
    }

    public interface OnConnectionListener {
        void onConnected(WifiP2pInfo info);
        void onDisconnected();
        void onGroupFormed(WifiP2pGroup group);
        void onError(String error);
    }

    // ========== 构造函数 ==========

    public WifiDirectManager(Context context) {
        this.context = context;
        this.manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        initialize();
    }

    private void initialize() {
        if (manager == null) {
            Log.e(TAG, "设备不支持 Wi-Fi Direct");
            return;
        }
        
        channel = manager.initialize(context, context.getMainLooper(), () -> {
            if (isWifiP2pEnabled) {
                Log.i(TAG, "Wi-Fi Direct 通道就绪");
            } else if (retryChannel) {
                bindCount++;
                Log.w(TAG, "通道丢失，重试绑定... (尝试 " + bindCount + ")");
                retryChannel = false;
            }
        });

        registerReceiver();
    }

    // ========== BroadcastReceiver ==========

    private final BroadcastReceiver wifiDirectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    isWifiP2pEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                    if (!isWifiP2pEnabled && peerDiscoveryListener != null) {
                        peerDiscoveryListener.onError("Wi-Fi Direct 未启用");
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                    if (manager != null) {
                        manager.requestPeers(channel, peerList -> {
                            updatePeerList(peerList);
                        });
                    }
                    break;

                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                    handleConnectionChange(intent);
                    break;

                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                    break;
            }
        }
    };

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        context.registerReceiver(wifiDirectReceiver, filter);
    }

    private void updatePeerList(WifiP2pDeviceList deviceList) {
        peerDevices.clear();
        for (WifiP2pDevice device : deviceList.getDeviceList()) {
            peerDevices.add(new PeerDevice(device));
        }
        if (peerDiscoveryListener != null) {
            peerDiscoveryListener.onPeersUpdated(new ArrayList<>(peerDevices));
        }
    }

    private void handleConnectionChange(Intent intent) {
        if (manager == null) return;
        
        WifiP2pInfo wifiP2pInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
        WifiP2pGroup wifiP2pGroup = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP);

        if (wifiP2pInfo != null && wifiP2pInfo.groupFormed) {
            if (connectionListener != null) {
                connectionListener.onConnected(wifiP2pInfo);
            }
        } else {
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
        }

        if (wifiP2pGroup != null && connectionListener != null) {
            connectionListener.onGroupFormed(wifiP2pGroup);
        }
    }

    // ========== 发现设备 ==========

    public void startDiscovery() {
        if (manager == null || channel == null || isDiscovering) return;

        isDiscovering = true;
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                isDiscovering = true;
                if (peerDiscoveryListener != null) {
                    peerDiscoveryListener.onDiscoveryStarted();
                }
                Log.i(TAG, "设备发现已启动");
            }

            @Override
            public void onFailure(int reason) {
                isDiscovering = false;
                String error = reasonToError(reason);
                if (peerDiscoveryListener != null) {
                    peerDiscoveryListener.onError(error);
                }
                Log.e(TAG, "设备发现失败: " + error);
            }
        });
    }

    public void stopDiscovery() {
        if (manager != null && channel != null) {
            manager.stopPeerDiscovery(channel, null);
        }
        isDiscovering = false;
        if (peerDiscoveryListener != null) {
            peerDiscoveryListener.onDiscoveryStopped();
        }
    }

    // ========== 连接设备 ==========

    public void connectToDevice(WifiP2pDevice device) {
        if (manager == null || channel == null) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        
        // 群组连接关键：改为 0（不主动做 GO）让双方协商
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            config.groupOwnerBand = WifiP2pConfig.GROUP_OWNER_BAND_AUTO;
        }

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "连接请求已发送给 " + device.deviceAddress);
            }

            @Override
            public void onFailure(int reason) {
                if (connectionListener != null) {
                    connectionListener.onError("连接失败: " + reasonToError(reason));
                }
            }
        });
    }

    // ========== 创建群组 ==========

    public void createGroup() {
        if (manager == null || channel == null) return;

        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "群组创建成功，本机为 Group Owner");
            }

            @Override
            public void onFailure(int reason) {
                // 如果已有群组则先移除再创建
                manager.requestGroupInfo(channel, group -> {
                    if (group != null) {
                        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                createGroup();
                            }
                            @Override
                            public void onFailure(int reason2) {
                                if (connectionListener != null) {
                                    connectionListener.onError("创建群组失败");
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    public void requestGroupInfo() {
        if (manager != null && channel != null) {
            manager.requestGroupInfo(channel, group -> {
                if (group != null && connectionListener != null) {
                    connectionListener.onGroupFormed(group);
                }
            });
        }
    }

    public void removeGroup() {
        if (manager != null && channel != null) {
            manager.removeGroup(channel, null);
        }
    }

    // ========== 获取连接信息 ==========

    public void requestConnectionInfo(WifiP2pManager.ConnectionInfoListener listener) {
        if (manager != null && channel != null) {
            manager.requestConnectionInfo(channel, listener);
        }
    }

    // ========== 取消注册 ==========

    public void onDestroy() {
        stopDiscovery();
        removeGroup();
        try {
            context.unregisterReceiver(wifiDirectReceiver);
        } catch (IllegalArgumentException e) {
            // already unregistered
        }
    }

    // ========== Setters ==========

    public void setOnPeerDiscoveryListener(OnPeerDiscoveryListener listener) {
        this.peerDiscoveryListener = listener;
    }

    public void setOnConnectionListener(OnConnectionListener listener) {
        this.connectionListener = listener;
    }

    // ========== 工具方法 ==========

    private String reasonToError(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED: return "设备不支持 P2P";
            case WifiP2pManager.ERROR: return "内部错误";
            case WifiP2pManager.BUSY: return "系统繁忙，请稍后重试";
            default: return "错误码: " + reason;
        }
    }
}
