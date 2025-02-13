package com.example.a7_0project;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;


import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.a7_0project.databinding.ActivityMainBinding;



import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String ACTION_USB_PERMISSION = "com.android.example.a7_0project.USB_PERMISSION";

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private PackageManager pacman;
    private Button serialButton;
    private TextView label1;
    private TextView label2;
    private TextView label3;
    private TextView label4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        this.setContentView(R.layout.layout);

        this.pacman = getPackageManager();

        this.label1 = this.findViewById(R.id.example_label);
        this.label2 = this.findViewById(R.id.textView5);
        this.label3 = this.findViewById(R.id.textView3);
        this.label4 = this.findViewById(R.id.textView4);

        this.serialButton = (Button)this.findViewById(R.id.serial_button);
        this.serialButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //default button color is blue
                serialButton.setBackgroundColor(Color.BLUE);

                // Find all available drivers from attached devices.
                UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

                //list all devices in label 1
                label1.setText(manager.getDeviceList().toString());

                //find all usb drivers
                List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
                if (availableDrivers.isEmpty()) {
                    serialButton.setBackgroundColor(Color.MAGENTA);
                    return;
                }
                //set the first found usb driver to label 2
                label2.setText(availableDrivers.get(0).toString());

                //
                PendingIntent permissionIntent = PendingIntent.getBroadcast(v.getContext(), 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                UsbDevice device = manager.getDeviceList().get(manager.getDeviceList().keySet().iterator().next());
                manager.requestPermission(device, permissionIntent);


                label3.setText(String.valueOf(manager.hasPermission(manager.getDeviceList().get(manager.getDeviceList().keySet().iterator().next()))));

                // Open a connection to the first available driver.
                UsbSerialDriver driver = availableDrivers.get(0);
                UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
                if (connection == null) {
                    serialButton.setBackgroundColor(Color.RED);
                    //UsbManager.requestPermission(driver.getDevice(), );
                    return;
                }

                label4.setText(String.valueOf(connection));

                UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
                try {
                    port.open(connection);
                    port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                    port.write("hello".getBytes(), 1000);
                    byte[] response = new byte[20];
                    int len = port.read(response, 1500);
                    StringBuilder stringBuilder = new StringBuilder();
                    for (byte b : response) {
                        stringBuilder.append((char) b);
                    }
                    serialButton.setBackgroundColor(Color.YELLOW);
                    label1.setText(stringBuilder);
                } catch (IOException e) {
                    serialButton.setBackgroundColor(Color.BLACK);
                    throw new RuntimeException(e);
                }


            }
        });


        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.fab)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}