/*
Code based on http://stackoverflow.com/a/13923345/5587187, by Majdi_la
*/

package party.dabble.pianodoorbell;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity
{
	private static final String DEFAULT_BLUETOOTH_DEVICE_NAME = "PLab_Anders";
	private static final long DEFAULT_NOTIFICATION_DELAY = 200; // milliseconds

	private BluetoothAdapter bluetoothAdapter;
	private BluetoothSocket bluetoothSocket;
	private BluetoothDevice bluetoothDevice;

	private OutputStream outputStream;
	private InputStream inputStream;

	private EditText deviceNameBox;
	private EditText messageBox;
	private TextView notificationText;

	private Button connectButton;
	private Button disconnectButton;
	private Button sendButton;

	private PianoKey[] keys;

	private Thread listenerThread;
	private volatile boolean keepListening;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		deviceNameBox = findViewById(R.id.device);
		deviceNameBox.setText(DEFAULT_BLUETOOTH_DEVICE_NAME);

		messageBox = findViewById(R.id.message);

		connectButton = findViewById(R.id.connect);
		disconnectButton = findViewById(R.id.disconnect);
		sendButton = findViewById(R.id.send);

		notificationText = findViewById(R.id.notification);

		// Connect button
		connectButton.setOnClickListener((View v) ->
		{
			final String deviceName = deviceNameBox.getText().toString();
			bluetoothDevice = findBluetoothDevice(deviceName);
			if (bluetoothDevice == null)
				return;

			try
			{
				connectToBluetoothDevice();
			} catch (IOException e)
			{
				showError("Could not connect to Bluetooth device \"" + deviceName + "\"", DEFAULT_NOTIFICATION_DELAY);
				e.printStackTrace();
				return;
			}

			setButtonsToConnectedMode(true);
		});

		// Disconnect button
		disconnectButton.setOnClickListener((View v) ->
		{
			setButtonsToConnectedMode(false);

			disconnectFromBluetoothDevice();
		});

		// Send button
		sendButton.setOnClickListener((View v) ->
		{
			sendData(messageBox.getText().toString());
		});
	}

	private void setButtonsToConnectedMode(final boolean isConnected)
	{
		runOnUiThread(() ->
		{
			deviceNameBox.setEnabled(!isConnected);
			messageBox.setEnabled(isConnected);

			connectButton.setEnabled(!isConnected);
			disconnectButton.setEnabled(isConnected);
			sendButton.setEnabled(isConnected);

			if (isConnected)
				messageBox.requestFocus();
			else
				messageBox.clearFocus();
		});
	}

	private BluetoothDevice findBluetoothDevice(String deviceName)
	{
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
		{
			showError("No Bluetooth adapter available", DEFAULT_NOTIFICATION_DELAY);
			return null;
		}

		if (!bluetoothAdapter.isEnabled())
		{
			Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetooth, 0);
			showError("Bluetooth turned off; please try again", DEFAULT_NOTIFICATION_DELAY);
			return null;
		}

		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		if (pairedDevices.size() > 0)
		{
			for (BluetoothDevice device : pairedDevices)
			{
				String name = device.getName().trim();
				if (name.equals(deviceName))
					return device;
			}
		}
		showError("Paired Bluetooth device \"" + deviceName + "\" not found", DEFAULT_NOTIFICATION_DELAY);
		return null;
	}

	private void connectToBluetoothDevice() throws IOException
	{
		showText("", 0); // Clear text first, since connecting usually takes pretty long

		UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
		bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
		bluetoothSocket.connect();
		outputStream = bluetoothSocket.getOutputStream();
		inputStream = bluetoothSocket.getInputStream();

		beginListeningForData();

		showText("Connected to Bluetooth device", DEFAULT_NOTIFICATION_DELAY);
	}

	private void disconnectFromBluetoothDevice()
	{
		stopListeningForData();

		try
		{
			if (outputStream != null)
				outputStream.close();
			if (inputStream != null)
				inputStream.close();
			if (bluetoothSocket != null)
				bluetoothSocket.close();
		} catch (IOException e)
		{
			showError("Error while closing Bluetooth connection", DEFAULT_NOTIFICATION_DELAY);
			e.printStackTrace();
			return;
		}

		showText("Disconnected from Bluetooth device", DEFAULT_NOTIFICATION_DELAY);
	}

	private void sendData(String message)
	{
		message += "\n";
		try
		{
			outputStream.write(message.getBytes());
		} catch (IOException e)
		{
			showError("Error while sending data", DEFAULT_NOTIFICATION_DELAY);
			e.printStackTrace();
			return;
		}

		showText("Data sent", DEFAULT_NOTIFICATION_DELAY);
	}

	private void beginListeningForData()
	{
		initPianoKeys();

		keepListening = true;
		listenerThread = new Thread(() ->
		{
			while (keepListening && !Thread.currentThread().isInterrupted())
			{
				int bytesAvailable;
				byte[] packetBytes;
				try
				{
					bytesAvailable = inputStream.available();
					if (bytesAvailable <= 0)
						continue;

					packetBytes = new byte[bytesAvailable];
					inputStream.read(packetBytes);
				} catch (IOException ex)
				{
					new Handler(Looper.getMainLooper()).post(this::disconnectFromBluetoothDevice); // Disconnect in main thread to avoid this thread joining itself (in stopListeningForData())
					setButtonsToConnectedMode(false);
					showError("Error while listening to Bluetooth device; please try connecting again", DEFAULT_NOTIFICATION_DELAY);
					break;
				}

				for (int i = 0; i < bytesAvailable; i++)
				{
					final byte b = packetBytes[i];

					byte key = (byte)Math.abs(b);
					byte action = (byte)(b >>> 7);

					if (key < 0 || key >= keys.length)
						continue;

					if (action == 0)
						keys[key].play();
					else
						keys[key].stop();
				}
			}

			terminatePianoKeys();
		});
		listenerThread.start();
	}

	private void stopListeningForData()
	{
		keepListening = false;
		try
		{
			listenerThread.join();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	private void initPianoKeys()
	{
		keys = new PianoKey[]
				{
						new PianoKey(440.00f),
						new PianoKey(466.16f),
						new PianoKey(493.88f),
						new PianoKey(523.25f),
						new PianoKey(554.37f),
						new PianoKey(587.33f),

//						new PianoKey(622.25f),
//						new PianoKey(659.25f),
//						new PianoKey(698.46f),
//						new PianoKey(739.99f),
//						new PianoKey(783.99f),
//						new PianoKey(830.61f),
				};
	}

	private void terminatePianoKeys()
	{
		for (PianoKey key : keys)
			key.terminate();
	}

	private void showText(String notification, long delayMillis)
	{
		showText(notification, "#8A000000", delayMillis); // default text color
	}

	private void showError(String notification, long delayMillis)
	{
		showText(notification, "#FF4081", delayMillis); // @color/colorAccent
	}

	private void showText(final String notification, final String colorHex, final long delayMillis)
	{
		runOnUiThread(() ->
		{
			if (delayMillis > 0)
				showText("", 0); // Clear text, to give the user feedback even when the text doesn't change

			new Handler().postDelayed(() ->
			{
				notificationText.setTextColor(Color.parseColor(colorHex));
				notificationText.setText(notification);
			}, delayMillis);
		});
	}
}
