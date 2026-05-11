package com.example.p2pchat.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.p2pchat.R;
import com.example.p2pchat.model.PeerDevice;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
    private List<PeerDevice> devices = new ArrayList<>();
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(PeerDevice device);
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.listener = listener;
    }

    public void updateDevices(List<PeerDevice> devices) {
        this.devices = devices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PeerDevice device = devices.get(position);
        holder.bind(device);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvDeviceName, tvDeviceInfo;
        ImageView ivConnectionStatus;

        ViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
            tvDeviceInfo = itemView.findViewById(R.id.tvDeviceInfo);
            ivConnectionStatus = itemView.findViewById(R.id.ivConnectionStatus);
        }

        void bind(PeerDevice device) {
            tvDeviceName.setText(device.getDisplayName());
            tvDeviceInfo.setText(device.getStatusText());
            
            // 头像取首字母
            String name = device.getDisplayName();
            if (name != null && !name.isEmpty()) {
                tvAvatar.setText(name.substring(0, 1).toUpperCase());
            } else {
                tvAvatar.setText("?");
            }

            // 连接状态点
            if (device.isConnected()) {
                ivConnectionStatus.setColorFilter(0xFF07C160);
            } else {
                ivConnectionStatus.setColorFilter(0xFFBDBDBD);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeviceClick(device);
                }
            });
        }
    }
}
