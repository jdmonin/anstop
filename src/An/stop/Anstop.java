/***************************************************************************
 *   Copyright (C) 2009 by mj   										   *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2010 Jeremy Monin jeremy@nand.net *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program; if not, write to the                         *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 ***************************************************************************/

package An.stop;

import java.text.NumberFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Anstop's main activity, showing the current clock, lap times, etc.
 */
public class Anstop extends Activity {

	private static final int MENU_MODE_GROUP = 0;
	private static final int MODE_STOP = 1;
	private static final int MODE_LAP = 2;
	private static final int MODE_COUNTDOWN = 3;

	private static final int MENU_SETTINGS = 4;
	private static final int SETTINGS_ITEM = 5;

	private static final int MENU_ABOUT = 6;
	private static final int ABOUT_ITEM = 7;

	private static final int MENU_SAVE = 8;
	private static final int SAVE_ITEM = 9;

	private static final int MENU_LOAD = 10;
	private static final int LOAD_ITEM = 11;

	private static final int MENU_SEND = 12;
	private static final int SEND_ITEM = 13;

	/** Stopwatch mode (and layout) */
	private static final int STOP = 0;

	/** Countdown mode (and layout) */
	private static final int COUNTDOWN = 1;

	/** Lap mode (and layout) */
	private static final int LAP = 2;

	/** Current mode: {@link #STOP}, {@link #COUNTDOWN} or {@link #LAP}. */
	private int curMode;

	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;

	private static final int SETTINGS_ACTIVITY = 0;

	private static final int VIEW_SIZE = 60;

	private int laps = 1;

	private IClockCounter boundService;

	Button startButton;
	Button resetButton;
	Button refreshButton;
	Button lapButton;
	TextView dsecondsView;
	TextView secondsView;
	TextView minView;
	TextView hourView;
	TextView lapView;
	Spinner secSpinner;
	Spinner minSpinner;
	Spinner hourSpinner;

	MenuItem modeMenuItem;
	MenuItem saveMenuItem;

	static Context context;
	Vibrator vib;
	public NumberFormat nf;

	AccelerometerListener al;

	anstopDbAdapter dbHelper;
	Intent clockServiceIntent;

	private ServiceConnection clockCounterCon = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder service) {
			boundService = IClockCounter.Stub.asInterface(service);
			try {
				boundService.setMode(curMode);
				boundService.registerCallback(callback);
			} catch (RemoteException e) {
				Log.d("onServiceConnected", e.getMessage());
			}
		}

		public void onServiceDisconnected(ComponentName name) {
			boundService = null;
		}

	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		dbHelper = new anstopDbAdapter(this);
		dbHelper.open();
		
		nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(2);  // The minimum Digits required is 2
        nf.setMaximumIntegerDigits(2); // The maximum Digits required is 2
		
		clockServiceIntent = new Intent(this, ClockService.class);
		bindService(clockServiceIntent, clockCounterCon,
				Context.BIND_AUTO_CREATE);
		// read Preferences
		readSettings(true);
	}

	/**
	 * Read our settings, at startup or after calling the SettingsActivity.
	 * 
	 * @param isStartup
	 *            Are we just starting now, not already running?
	 */
	private void readSettings(final boolean isStartup) {

		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (settings.getBoolean("use_motion_sensor", false)) {
			al = new AccelerometerListener(this);
			al.start();
		} else {
			if (al != null)
				al.stop();
		}

		if (settings.getBoolean("vibrate", true))
			vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		else
			vib = null;

		if (!isStartup)
			return;

		// "mode" setting: Clock mode at startup; an int saved as string.
		try {
			int settingMode = Integer.parseInt(settings.getString("mode", "0")); // 0
			// ==
			// STOP
			switch (settingMode) {
			case STOP:
				stopwatch();
				break;

			case COUNTDOWN:
				countdown();
				break;

			case LAP:
				lap();
				break;
			}
		} catch (NumberFormatException e) {
		}
	}

	/**
	 * Set the layout to {@link #COUNTDOWN}, and inform clock class to count
	 * down now.
	 */
	public void countdown() {

		// set the Layout to the countdown layout
		setContentView(R.layout.countdown);

		// set Views Buttons and Listeners for the new Layout
		dsecondsView = (TextView) findViewById(R.id.dsecondsView);
		secondsView = (TextView) findViewById(R.id.secondsView);
		minView = (TextView) findViewById(R.id.minView);
		hourView = (TextView) findViewById(R.id.hourView);

		// set the size
		TextView sepView = (TextView) findViewById(R.id.sepView1);
		sepView.setTextSize(VIEW_SIZE - 10);

		sepView = (TextView) findViewById(R.id.sepView2);
		sepView.setTextSize(VIEW_SIZE - 10);

		dsecondsView.setTextSize(VIEW_SIZE);
		secondsView.setTextSize(VIEW_SIZE);
		minView.setTextSize(VIEW_SIZE);
		hourView.setTextSize(VIEW_SIZE - 30);

		// adding spinners
		secSpinner = (Spinner) findViewById(R.id.secSpinner);
		minSpinner = (Spinner) findViewById(R.id.minSpinner);
		hourSpinner = (Spinner) findViewById(R.id.hourSpinner);

		// creating Adapter for Spinners
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.num, android.R.layout.simple_spinner_item);
		adapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		// set the adapter to the spinners
		secSpinner.setAdapter(adapter);
		minSpinner.setAdapter(adapter);
		hourSpinner.setAdapter(adapter);

		// set onlicklisteners
		startButton = (Button) findViewById(R.id.startButton);
		startButton.setOnClickListener(new startButtonListener());

		refreshButton = (Button) findViewById(R.id.refreshButton);
		refreshButton.setOnClickListener(new refreshButtonListener());

		curMode = COUNTDOWN;
	}

	/**
	 * Set the layout to {@link #LAP}, and inform clock class to count laps now
	 * (same clock-action as STOP).
	 */
	public void lap() {

		// set the Layout to the lap-mode layout
		setContentView(R.layout.lap);

		// set Views Buttons and Listeners for the new Layout
		dsecondsView = (TextView) findViewById(R.id.dsecondsView);
		secondsView = (TextView) findViewById(R.id.secondsView);
		minView = (TextView) findViewById(R.id.minView);
		hourView = (TextView) findViewById(R.id.hourView);

		// set the size
		TextView sepView = (TextView) findViewById(R.id.sepView1);
		sepView.setTextSize(VIEW_SIZE - 10);

		sepView = (TextView) findViewById(R.id.sepView2);
		sepView.setTextSize(VIEW_SIZE - 10);

		dsecondsView.setTextSize(VIEW_SIZE);
		secondsView.setTextSize(VIEW_SIZE);
		minView.setTextSize(VIEW_SIZE);
		hourView.setTextSize(VIEW_SIZE - 30);

		lapView = (TextView) findViewById(R.id.lapView);
		lapView.setTextSize(VIEW_SIZE - 30);

		lapButton = (Button) findViewById(R.id.lapButton);
		lapButton.setOnClickListener(new lapButtonListener());

		startButton = (Button) findViewById(R.id.startButton);
		startButton.setOnClickListener(new startButtonListener());

		resetButton = (Button) findViewById(R.id.resetButton);
		resetButton.setOnClickListener(new resetButtonListener());

		curMode = LAP;

	}

	/**
	 * Set the mode to {@link #STOP}, and set layout to that normal layout.
	 */
	private void stopwatch() {
		setContentView(R.layout.main);

		// set the View Objects
		dsecondsView = (TextView) findViewById(R.id.dsecondsView);
		secondsView = (TextView) findViewById(R.id.secondsView);
		minView = (TextView) findViewById(R.id.minView);
		hourView = (TextView) findViewById(R.id.hourView);

		// set the size
		TextView sepView = (TextView) findViewById(R.id.sepView1);
		sepView.setTextSize(VIEW_SIZE - 10);

		sepView = (TextView) findViewById(R.id.sepView2);
		sepView.setTextSize(VIEW_SIZE - 10);

		dsecondsView.setTextSize(VIEW_SIZE);
		secondsView.setTextSize(VIEW_SIZE);
		minView.setTextSize(VIEW_SIZE);
		hourView.setTextSize(VIEW_SIZE - 30);
		
		// set Buttons and Listeners
		startButton = (Button) findViewById(R.id.startButton);
		startButton.setOnClickListener(new startButtonListener());

		resetButton = (Button) findViewById(R.id.resetButton);
		resetButton.setOnClickListener(new resetButtonListener());

		curMode = STOP;

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Save Send & Load
		saveMenuItem = menu.add(MENU_SAVE, SAVE_ITEM, 0, R.string.save)
				.setIcon(android.R.drawable.ic_menu_save);
		menu.add(MENU_SEND, SEND_ITEM, 0, R.string.send).setIcon(
				android.R.drawable.ic_menu_send);
		menu.add(MENU_LOAD, LOAD_ITEM, 0, R.string.load).setIcon(
				android.R.drawable.ic_menu_upload);

		// Mode Submenu
		SubMenu modeMenu = menu.addSubMenu(R.string.mode).setIcon(
				android.R.drawable.ic_menu_more);
		modeMenu.add(MENU_MODE_GROUP, MODE_STOP, 0, R.string.stop);
		modeMenu.add(MENU_MODE_GROUP, MODE_LAP, 0, R.string.lap_mode);
		modeMenu.add(MENU_MODE_GROUP, MODE_COUNTDOWN, 0, R.string.countdown);
		modeMenu.setGroupCheckable(MENU_MODE_GROUP, true, true);
		modeMenuItem = modeMenu.getItem();

		try {
			if (boundService.isStarted()) {
				modeMenuItem.setEnabled(false);
				saveMenuItem.setEnabled(false);
			}
		} catch (RemoteException e) {
			Log.d("boundService", e.getMessage());
		}

		// Settings Menu
		menu.add(MENU_SETTINGS, SETTINGS_ITEM, 0, R.string.settings).setIcon(
				android.R.drawable.ic_menu_preferences);

		// about
		menu.add(MENU_ABOUT, ABOUT_ITEM, 0, R.string.about).setIcon(
				android.R.drawable.ic_menu_info_details);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i = new Intent();

		switch (item.getItemId()) {
		case SETTINGS_ITEM:
			i.setClass(this, settingsActivity.class);
			startActivityForResult(i, SETTINGS_ACTIVITY);
			readSettings(false);
			return true;

		case MODE_STOP:
			stopwatch();
			return true;

		case MODE_COUNTDOWN:
			countdown(); // set the layout to the countdown Layout
			return true;

		case MODE_LAP:
			lap();
			return true;

		case ABOUT_ITEM:
			showDialog(ABOUT_DIALOG);
			return true;

		case SAVE_ITEM:
			showDialog(SAVE_DIALOG);
			return true;

		case SEND_ITEM:
			startSendMailIntent(this, getResources().getString(
					R.string.app_name)
					+ ": " + currentModeAsString(), createBodyFromCurrent());
			return true;

		case LOAD_ITEM:
			i.setClass(this, loadActivity.class);
			startActivity(i);
			return true;

		}
		return false;
	}

	/**
	 * Start the appropriate intent for the user to send an E-Mail or SMS with
	 * these contents.
	 * 
	 * @param caller
	 *            The calling activity; used to launch Send intent
	 * @param title
	 *            Subject line for e-mail; if user has only SMS configured, this
	 *            will be unused.
	 * @param body
	 *            Body of e-mail
	 */
	public static void startSendMailIntent(Activity caller, final String title,
			final String body) {
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		String[] empty_recipients = new String[] { "" };
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL,
				empty_recipients);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
		emailIntent.setType("text/plain");
		// use Chooser, in case multiple apps are installed
		caller.startActivity(Intent.createChooser(emailIntent, caller
				.getResources().getString(R.string.send)));
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		switch (id) {
		case ABOUT_DIALOG:
			AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
			aboutBuilder.setMessage(R.string.about_dialog).setCancelable(true)
					.setPositiveButton("Ok",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.dismiss();
								}
							});

			dialog = aboutBuilder.create();
			break;

		case SAVE_DIALOG:
			AlertDialog.Builder saveBuilder = new AlertDialog.Builder(this);
			saveBuilder.setTitle(R.string.save);
			saveBuilder.setMessage(R.string.save_dialog);
			final EditText input = new EditText(this);
			saveBuilder.setView(input);

			saveBuilder.setPositiveButton("Ok",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {

							String body = createBodyFromCurrent();
							dbHelper
									.createNew(input.getText().toString(), body);
							showToast(R.string.saved_succes);
						}

					});

			saveBuilder.setNegativeButton(R.string.cancel,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							dialog.dismiss();
						}
					});
			saveBuilder.setCancelable(false);
			dialog = saveBuilder.create();
			break;

		default:
			dialog = null;
		}

		return dialog;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		readSettings(false); // because only settingsactivity is started for
		// result, we can launch that without checking the parameters.
	}

	/**
	 * Given the {@link #curMode current mode}, the name of that mode from
	 * resources.
	 * 
	 * @return mode name, or if unknown mode, "(unknown)".
	 */
	private String currentModeAsString() {
		int modus;
		switch (curMode) {
		case LAP:
			modus = R.string.lap_mode;
			break;
		case COUNTDOWN:
			modus = R.string.countdown;
			break;
		case STOP:
			modus = R.string.stop;
			break;
		default:
			return "(unknown)";
		}
		return context.getResources().getString(modus);
	}

	/**
	 * Construct a string with the current mode, time, and laps (if applicable).
	 */
	private String createBodyFromCurrent() {
		String body;
		switch (curMode) {
		case LAP:
			body = context.getResources().getString(R.string.mode_was) + " "
					+ context.getResources().getString(R.string.lap_mode)
					+ "\n" + hourView.getText().toString() + " "
					+ context.getResources().getString(R.string.hour) + "\n"
					+ minView.getText().toString() + ":"
					+ secondsView.getText().toString() + ":"
					+ dsecondsView.getText().toString() + "\n"
					+ lapView.getText().toString();
			break;
		case COUNTDOWN:
			body = context.getResources().getString(R.string.mode_was) + " "
					+ context.getResources().getString(R.string.countdown)
					+ "\n" + hourView.getText().toString() + " "
					+ context.getResources().getString(R.string.hour) + "\n"
					+ minView.getText().toString() + ":"
					+ secondsView.getText().toString() + ":"
					+ dsecondsView.getText().toString() + "\n"
					+ context.getResources().getString(R.string.start_time)
					+ "\n" + hourSpinner.getSelectedItemPosition() + " "
					+ context.getResources().getString(R.string.hour) + "\n"
					+ nf.format(secSpinner.getSelectedItemPosition()) + ":"
					+ nf.format(minSpinner.getSelectedItemPosition()) + ".0";
			break;
		case STOP:
			body = context.getResources().getString(R.string.mode_was) + " "
					+ context.getResources().getString(R.string.stop) + "\n"
					+ hourView.getText().toString() + " "
					+ context.getResources().getString(R.string.hour) + "\n"
					+ minView.getText().toString() + ":"
					+ secondsView.getText().toString() + ":"
					+ dsecondsView.getText().toString();
			break;
		default:
			body = "ModeError";
			break;
		}
		return body;
	}

	public static void showToast(int id) {
		Toast toast = Toast.makeText(context, id, Toast.LENGTH_SHORT);
		toast.show();
	}

	public void startCounting() {

		try {
			boundService.setMode(curMode);
			if(curMode == COUNTDOWN) {
				boundService.setSeconds(secSpinner.getSelectedItemPosition());
				boundService.setMinutes(minSpinner.getSelectedItemPosition());
				boundService.setHours(hourSpinner.getSelectedItemPosition());
			}
			
			if (modeMenuItem != null) {
				modeMenuItem.setEnabled(!boundService.isStarted());
				saveMenuItem.setEnabled(!boundService.isStarted());
			}
			
			startService(clockServiceIntent);
		} catch (RemoteException e) {
			Log.d("boundService", e.getMessage());
		}

		if (vib != null)
			vib.vibrate(50);
	}

	private class startButtonListener implements OnClickListener {

		public void onClick(View v) {
			startCounting();
		}
	}

	private class resetButtonListener implements OnClickListener {

		public void onClick(View v) {

			try {
				if (!boundService.isStarted()) {
					// reset all Views to zero
					dsecondsView.setText("0");
					secondsView.setText("00");
					minView.setText("00");
					hourView.setText("0");

					laps = 1;
					if (lapView != null)
						lapView.setText(R.string.laps);
				} else {
					showToast(R.string.reset_during_count);
				}
			} catch (RemoteException e) {
				Log.d("boundService", e.getMessage());
			}
		}
	}

	private class refreshButtonListener implements OnClickListener {

		public void onClick(View v) {

			try {
				if (!boundService.isStarted()) {
					// set the Views to the input data
					dsecondsView.setText("0");

					// looking for the selected Item position (is the same as
					// the
					// Item itself)
					// using the NumberFormat from class clock to format
					secondsView.setText(""
							+ nf.format(secSpinner.getSelectedItemPosition()));
					minView.setText(""
							+ nf.format(minSpinner.getSelectedItemPosition()));
					hourView
							.setText("" + hourSpinner.getSelectedItemPosition());

				} else {
					// Show error when currently counting
					Toast toast = Toast.makeText(context,
							R.string.refresh_during_count, Toast.LENGTH_SHORT);
					toast.show();
				}
			} catch (RemoteException e) {
				Log.d("boundService", e.getMessage());
			}
		}
	}

	private class lapButtonListener implements OnClickListener {

		public void onClick(View v) {
			lapView.append("\n" + (laps++) + ". " + hourView.getText() + "h "
					+ minView.getText() + ":" + secondsView.getText() + ":"
					+ dsecondsView.getText());

			if (vib != null)
				vib.vibrate(50);
		}
	}
	
	private IClockCounterCallback.Stub callback = new IClockCounterCallback.Stub() {

		public void dsecChanged(int dsec) throws RemoteException {
			dsecondsView.setText("" + dsec);	
		}

		public void hourChanged(int hour) throws RemoteException {
			hourView.setText("" + hour);
		}

		public void minChanged(int min) throws RemoteException {
			minView.setText("" + nf.format(min));
		}

		public void secChanged(int sec) throws RemoteException {
			secondsView.setText("" + nf.format(sec));
		}
	};

}