
package com.esbo.spotifybridge;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION =
            "com.esbo.spotifybridge.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private UsbDeviceConnection usbConnection;
    private TextView status;
    private android.os.Handler handler;
    private String lastPayload = "";

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);

                if (granted && device != null) {
                    connectDevice(device);
                } else {
                    setStatus("USB permission denied");
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbManager = (UsbManager) getSystemService(USB_SERVICE);
        handler = new android.os.Handler(getMainLooper());

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 30, 24, 24);

        TextView heading = new TextView(this);
        heading.setText("ESBO Spotify Bridge");
        heading.setTextSize(25);
        layout.addView(heading);

        status = new TextView(this);
        status.setTextSize(16);
        status.setText("Ready. Enable notification access first.");
        layout.addView(status);

        Button notifications = new Button(this);
        notifications.setText("1. Enable notification access");
        notifications.setOnClickListener(v -> {
            Intent i = new Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(i);
        });
        layout.addView(notifications);

        Button connect = new Button(this);
        connect.setText("2. Connect Arduino Nano");
        connect.setOnClickListener(v -> findArduino());
        layout.addView(connect);

        Button disconnect = new Button(this);
        disconnect.setText("Disconnect USB");
        disconnect.setOnClickListener(v -> disconnectArduino());
        layout.addView(disconnect);

        setContentView(layout);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        handler.post(sendUpdates);
    }

    private void findArduino() {
        List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber()
                        .findAllDrivers(usbManager);

        if (drivers.isEmpty()) {
            setStatus("No supported USB serial device found. Check OTG.");
            return;
        }

        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();

        if (usbManager.hasPermission(device)) {
            connectDevice(device);
        } else {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION)
                            .setPackage(getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_MUTABLE);

            usbManager.requestPermission(device, permissionIntent);
            setStatus("Approve the USB permission popup.");
        }
    }

    private void connectDevice(UsbDevice device) {
        try {
            List<UsbSerialDriver> drivers =
                    UsbSerialProber.getDefaultProber()
                            .findAllDrivers(usbManager);

            UsbSerialDriver chosen = null;
            for (UsbSerialDriver driver : drivers) {
                if (driver.getDevice().getDeviceId()
                        == device.getDeviceId()) {
                    chosen = driver;
                    break;
                }
            }

            if (chosen == null) {
                setStatus("USB serial driver not found.");
                return;
            }

            usbConnection = usbManager.openDevice(device);
            if (usbConnection == null) {
                setStatus("Cannot open USB device.");
                return;
            }

            serialPort = chosen.getPorts().get(0);
            serialPort.open(usbConnection);
            serialPort.setParameters(115200, 8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);

            setStatus("Arduino connected at 115200 baud!");
        } catch (Exception e) {
            disconnectArduino();
            setStatus("Connection error: " + e.getMessage());
        }
    }

    private final Runnable sendUpdates = new Runnable() {
        @Override
        public void run() {
            if (serialPort != null) {
                android.content.SharedPreferences prefs =
                        getSharedPreferences("esbo_music",
                                MODE_PRIVATE);

                String title = clean(prefs.getString(
                        "title", "Waiting for Spotify"));
                String artist = clean(prefs.getString(
                        "artist", "Play a song"));
                String state = clean(prefs.getString(
                        "state", "WAITING"));

                String payload = "TITLE=" + title + "\n"
                        + "ARTIST=" + artist + "\n"
                        + "STATE=" + state + "\n"
                        + "DURATION=0\n"
                        + "POSITION=0\n";

                if (!payload.equals(lastPayload)) {
                    try {
                        serialPort.write(payload.getBytes(
                                StandardCharsets.UTF_8), 1000);
                        lastPayload = payload;
                        setStatus("Connected • sending song details");
                    } catch (Exception e) {
                        setStatus("USB write failed. Reconnect Nano.");
                        disconnectArduino();
                    }
                }
            }

            handler.postDelayed(this, 1000);
        }
    };

    private String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('=', '-');
    }

    private void disconnectArduino() {
        try {
            if (serialPort != null) serialPort.close();
        } catch (Exception ignored) {
        }

        try {
            if (usbConnection != null) usbConnection.close();
        } catch (Exception ignored) {
        }

        serialPort = null;
        usbConnection = null;
        lastPayload = "";
        setStatus("USB disconnected.");
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(sendUpdates);
        disconnectArduino();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
      }
