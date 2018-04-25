package dev.trickybear.ru.gestruequest;

import android.Manifest;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import co.lujun.lmbluetoothsdk.base.State;
import dev.trickybear.ru.gestruequest.bluetooth.Action;
import dev.trickybear.ru.gestruequest.bluetooth.MessagesCodes;
import dev.trickybear.ru.gestruequest.dialogs.BluetoothDevicesDialog;
import dev.trickybear.ru.gestruequest.fragments.MainFragment;
import dev.trickybear.ru.gestruequest.fragments.SetGestureFragment;
import dev.trickybear.ru.gestruequest.fragments.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private static final int BLUETOOTH_ENABLE_REQUEST_CODE = 1;
    private static final String BLUETOOTH_DIALOG_KEY = "bt_dialog";
    private static final String BT_ADDRESS = "bt_address";
    private static final String WAIT_COMMAND = "wait";

    private View decorView;
    private FragmentManager fragmentManager;
    private MainFragment mainFragment;
    private SettingsFragment settingsFragment;
    private SetGestureFragment setGestureFragment;
    private BluetoothDevicesDialog bluetoothDevicesDialog;
    private boolean isConnected = false;
    private Timer connectionTimer;
    private SharedPreferences sharedPref;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPref = getPreferences(Context.MODE_PRIVATE);
        decorView = getWindow().getDecorView();
        fragmentManager = getFragmentManager();
        mainFragment = new MainFragment();
        setGestureFragment = new SetGestureFragment();
        settingsFragment = new SettingsFragment();
        showMainFragment();
        GestureQuest.getBluetoothService().setBluetoothMessagesCallback(bluetoothCallback);
        bluetoothDevicesDialog = new BluetoothDevicesDialog();
        if (!GestureQuest.getBluetoothService().isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, BLUETOOTH_ENABLE_REQUEST_CODE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {  // Only ask for these permissions on runtime when running Android 6.0 or higher
            switch (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION)) {
                case PackageManager.PERMISSION_DENIED:
                    ((TextView) new AlertDialog.Builder(this)
                            .setTitle("Необходимо разрешение")
                            .setMessage("Разрешите доступ к местоположению, чтобы использовать Bluetooth")
                            .setNeutralButton("Хорошо, разрешу!", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                        ActivityCompat.requestPermissions(MainActivity.this,
                                                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                                1);
                                    }
                                }
                            })
                            .show()
                            .findViewById(android.R.id.message))
                            .setMovementMethod(LinkMovementMethod.getInstance());       // Make the link clickable. Needs to be called after show(), in order to generate hyperlinks
                    break;
                case PackageManager.PERMISSION_GRANTED:
                    break;
            }
        }
    }

    protected void onResume() {
        super.onResume();
        hideSystemUI();
        Consts.isWaitingCommand = getWaitCommand();
        if (GestureQuest.getBluetoothService().isBluetoothAvailable() && !isConnected && getLastDevice() != null) {
            new Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            GestureQuest.getBluetoothService().connectBluetoothDevice(getLastDevice());
                        }
                    }, 3L * 1000);
        }
    }

    public void showMainFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.fragments_container, mainFragment)
                .commit();
    }

    public void showSettingsFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.fragments_container, settingsFragment)
                .addToBackStack(null).commit();
    }

    public void showSetGestureFragment() {
        fragmentManager.beginTransaction()
                .replace(R.id.fragments_container, setGestureFragment)
                .addToBackStack(null).commit();
    }

    public void showBluetoothDevicesDialog() {
        bluetoothDevicesDialog.show(getFragmentManager(), BLUETOOTH_DIALOG_KEY);
    }

    public void goBack() {
        fragmentManager.popBackStackImmediate();
    }


    private void hideSystemUI() {
        decorView.setSystemUiVisibility(5894);
    }

    public void saveBluetoothDevice(String address) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(BT_ADDRESS, address);
        editor.commit();
    }

    public void saveWaitCommand(boolean wait) {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(WAIT_COMMAND, wait);
        editor.commit();
    }

    public String getLastDevice() {
        return sharedPref.getString(BT_ADDRESS, null);
    }

    public boolean getWaitCommand() {
        return sharedPref.getBoolean(WAIT_COMMAND, false);
    }

    private Handler.Callback bluetoothCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {

                case MessagesCodes.BLUETOOTH_DATA_RECEIVED:
                    String receivedAction = (String) message.obj;
                    switch (receivedAction) {
                        case Action.RECOGNIZE:
                            mainFragment.setRecognizing(true);
                            break;
                        case Action.RESTART:
                            mainFragment.restart();
                            break;
                    }
                    Toast toast = Toast.makeText(getApplicationContext(), (String) message.obj, Toast.LENGTH_SHORT);
                    toast.show();
                    return true;

                case MessagesCodes.DISCOVERY_STATE:
                    bluetoothDevicesDialog.notifyConnectionStateChanged();
                    return true;

                case MessagesCodes.BLUETOOTH_STATE:

                    int state = (int) message.obj;
                    if (state == State.STATE_DISCONNECTED) {
                        isConnected = false;
                        bluetoothDevicesDialog.notifyConnectionStateChanged();
                        if (connectionTimer != null) {
                            connectionTimer.cancel();
                            connectionTimer.purge();

                            new Handler().postDelayed(
                                    new Runnable() {
                                        public void run() {
                                            if (getLastDevice() != null)
                                                GestureQuest.getBluetoothService().connectBluetoothDevice(getLastDevice());
                                        }
                                    }, 3L * 1000);
                        }

                    } else if (state == State.STATE_CONNECTED) {
                        bluetoothDevicesDialog.notifyConnectionStateChanged();
                        isConnected = true;
                        saveBluetoothDevice(GestureQuest.getBluetoothService().getConnectedDevice().getAddress());
                        Toast.makeText(getApplicationContext(), GestureQuest.getBluetoothService().getConnectedDevice().getName(), Toast.LENGTH_SHORT).show();
                        connectionTimer = new Timer();
                        connectionTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                GestureQuest.getBluetoothService().sendAction(Action.PING);
                            }
                        }, 5000L, 30L * 1000); // each 30 sec
                    }
                    return true;

                case MessagesCodes.DEVICE_FOUND:
                    BluetoothDevice bluetoothDevice = (BluetoothDevice) message.obj;
                    bluetoothDevicesDialog.deviceFound(bluetoothDevice);
                    return true;

                default:
                    return false;
            }
        }
    };

}
