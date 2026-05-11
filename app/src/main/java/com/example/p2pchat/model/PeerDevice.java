package com.example.p2pchat.model;

import android.net.wifi.p2p.WifiP2pDevice;

public class PeerDevice {
    private WifiP2pDevice wifiP2pDevice;
    private String displayName;
    private boolean isConnected;
    private boolean isGroupOwner;

    public PeerDevice(WifiP2pDevice wifiP2pDevice) {
        this.wifiP2pDevice = wifiP2pDevice;
        this.displayName = wifiP2pDevice.deviceName;
        this.isConnected = wifiP2pDevice.status == WifiP2pDevice.CONNECTED;
    }

    public WifiP2pDevice getWifiP2pDevice() { return wifiP2pDevice; }
    public void setWifiP2pDevice(WifiP2pDevice wifiP2pDevice) { this.wifiP2pDevice = wifiP2pDevice; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDeviceAddress() { return wifiP2pDevice.deviceAddress; }

    public boolean isConnected() { return isConnected; }
    public void setConnected(boolean connected) { isConnected = connected; }

    public boolean isGroupOwner() { return isGroupOwner; }
    public void setGroupOwner(boolean groupOwner) { isGroupOwner = groupOwner; }

    public String getStatusText() {
        if (isConnected) return "✓ 已连接";
        switch (wifiP2pDevice.status) {
            case WifiP2pDevice.AVAILABLE: return "可用";
            case WifiP2pDevice.INVITED: return "已邀请";
            case WifiP2pDevice.FAILED: return "连接失败";
            default: return "未知";
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PeerDevice) {
            return getDeviceAddress().equals(((PeerDevice) obj).getDeviceAddress());
        }
        return false;
    }
}
