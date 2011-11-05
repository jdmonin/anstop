/***************************************************************************
 *   Copyright (C) 2009 by mj   										   *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2010-2011 Jeremy Monin            *
 *    jeremy@nand.net                                                      *
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

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;

/**
 * Anstop's main activity, showing the current clock, lap times, etc.
 *<P>
 * Uses 1 of 3 layouts, depending on the {@link #current} mode:
 * <tt>main</tt>, <tt>countdown</tt>, <tt>lap</tt>.
 */
public class Anstop extends Activity implements OnGesturePerformedListener {
    
	
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

	/** Stopwatch mode (and layout), for {@link #current} */
	private static final int STOP = 0;

	/** Countdown mode (and layout), for {@link #current} */
	private static final int COUNTDOWN = 1;

	/** Lap mode (and layout), for {@link #current} */
	private static final int LAP = 2;

	/**
	 * Current mode: {@link #STOP}, {@link #COUNTDOWN} or {@link #LAP}.
	 * Corresponds to <tt>{@link Clock}.v</tt> mode.
	 */
	private int current;
	
	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;
	
	private static final int SETTINGS_ACTIVITY = 0;
	
	private static final int VIEW_SIZE = 60;
	
	/**
	 * If true, we already wrote the start date/time into {@link #lapView}.
	 * Visibility is non-private for use by {@link Clock#fillSaveState(Bundle)}
	 * and {@link Clock#restoreFromSaveState(Bundle)}.
	 */
	boolean wroteStartTime;

	/**
	 * Date formatter for day of week + user's medium date format + hh:mm:ss;
	 * used in {@link startButtonListener#onClick(View)}
	 * for "started at:".
	 */
	private StringBuffer fmt_dow_meddate_time;

	Clock clock;
	/** start/stop (resume/pause) */
	Button startButton;
	/** reset the count */
	Button resetButton;
	/** in countdown mode, sets the hour/minute/second views to the input spinners' current data */
	Button refreshButton;
	Button lapButton;
	TextView dsecondsView;
	TextView secondsView;
	TextView minView;
	TextView hourView;
	/** shows start time in layouts which don't contain {@link #lapView} */
	TextView startTimeView;
	TextView lapView;
	Spinner secSpinner;
	Spinner minSpinner;
	Spinner hourSpinner;
	/** scrollview containing {@link #lapView} */
	ScrollView lapScroll;

	/** Context menu item for Mode. Null until {@link #onCreateOptionsMenu(Menu)} is called. */
	MenuItem modeMenuItem;

	/** Context menu item for Save. Null until {@link #onCreateOptionsMenu(Menu)} is called. */
	MenuItem saveMenuItem;
	
	Context mContext;
	Vibrator vib;
	
	AccelerometerListener al;
	GestureOverlayView gestureOverlay;
	GestureLibrary gestureLibrary;
	
	/**
	 * DatabaseHelper, if opened.
	 * Usually null, unless we've saved a time to the database.
	 * Closed and set to null in {@link #onPause()}.
	 */
	AnstopDbAdapter dbHelper;

	/** Mode Menu's items, for {@link #updateModeMenuFromCurrent()} to indicate current mode. */
	private MenuItem modeMenu_itemStop;
	private MenuItem modeMenu_itemLap;
	private MenuItem modeMenu_itemCountdown;

	/**
	 * Called when the activity is first created.
	 * Assumes {@link #STOP} mode and sets that layout.
	 * Preferences are read, which calls setCurrentMode.
	 * Also called later, to set the mode/layout back to {@link #STOP}.
	 */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        current = STOP;
        //set the View Objects
        dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        startTimeView = (TextView) findViewById(R.id.main_startTimeView);
        setupGesture();
        lapView = null;
        if (startTimeView.length() == 0)
        	wroteStartTime = false;

        //set the size
        TextView sepView = (TextView) findViewById(R.id.sepView1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) findViewById(R.id.sepView2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);
        startTimeView.setTextSize(VIEW_SIZE - 30);

        //set the clock object
        final boolean isInitialStartup = (clock == null);
        if (isInitialStartup)
        	clock = new Clock(this);
        
        //set Buttons and Listeners
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new resetButtonListener());



        if (! isInitialStartup)
        	return;  // <--- Early return: Already did startup once ---

        // Below here are things that should be done only at
        // initial startup, because they set the current mode.
        // Because stopwatch() calls onCreate(), this would create a loop.

        mContext = getApplicationContext();

        //read Preferences
        readSettings(true);

        // check for state restore
        if ((savedInstanceState != null)
        	&& savedInstanceState.containsKey("clockAnstopCurrent"))
        {
        	// bundle from onSaveInstanceState
        	onRestoreInstanceState(savedInstanceState);
        } else {
        	// prefs from onPause when isFinishing
        	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        	onRestoreInstanceState(settings);
        }
        
        gestureLibrary = GestureLibraries.fromRawResource(this, R.raw.gestures);
        gestureLibrary.load();
    }

    /**
     * Read our settings, at startup or after calling the SettingsActivity.
     *<P>
     * Does not check <tt>"anstop_in_use"</tt> or read the instance state
     * when the application is finishing; for that, see {@link Clock#restoreFromSaveState(SharedPreferences)}.
     *
     * @param isStartup Are we just starting now, not already running?
     */
    private void readSettings(final boolean isStartup) {

    	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
    	if( settings.getBoolean("use_motion_sensor", false) ) {
        	al = new AccelerometerListener(this, clock);
        	al.start();
        }
        else {
        	if(al != null)
        		al.stop();
        }
        
        if( settings.getBoolean("vibrate", true) ) 
        	vib = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        else
        	vib = null;

        if(!isStartup) return; // app was started before, user changed settings
        
        // "mode" setting: Clock mode at startup; an int saved as string.
        try
        {
	        int settingMode = Integer.parseInt(settings.getString("mode", "0")); // 0 == STOP
	        setCurrentMode(settingMode);
        } catch (NumberFormatException e) {}
        
        if(settings.getBoolean("first_start", true)) {
        	Toast.makeText(getApplicationContext(), R.string.first_start, Toast.LENGTH_LONG).show();
        	settings.edit().putBoolean("first_start", false);
        	settings.edit().commit();
        }
    }

    /**
     * Get the current mode.
     * @return {@link #STOP}, {@link #COUNTDOWN} or {@link #LAP}.
     */
    public int getCurrentMode()
    {
    	return current;
    }

    /**
     * Set the current mode, if the clock isn't currently started,
     * and load the right Layout for it.
     *<P>
     * Does nothing if {@link Clock#isStarted}, or if current mode is already newCurrent.
     *
     * @param newCurrent {@link #STOP}, {@link #COUNTDOWN} or {@link #LAP}.
     */
	private void setCurrentMode(final int newCurrent)
	{
		if (clock.isStarted || (current == newCurrent)) 
			return;

		switch (newCurrent)
		{
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
	}

    /**
     * Set the layout to {@link #COUNTDOWN}, and
     * inform clock class to count down now.
     */
    public void countdown() {
        current = COUNTDOWN;
    	//set the Layout to the countdown layout
    	setContentView(R.layout.countdown);
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        startTimeView = (TextView) findViewById(R.id.countdown_startTimeView);
        setupGesture();
        lapView = null;
        if (startTimeView.length() == 0)
        	wroteStartTime = false;

        //set the size
        TextView sepView = (TextView) findViewById(R.id.sepView1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) findViewById(R.id.sepView2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);
        startTimeView.setTextSize(VIEW_SIZE - 30);
        
        //adding spinners
        secSpinner = (Spinner) findViewById(R.id.secSpinner);
        minSpinner = (Spinner) findViewById(R.id.minSpinner);
        hourSpinner = (Spinner) findViewById(R.id.hourSpinner);
        
        //creating Adapter for Spinners
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.num, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        //set the adapter to the spinners
        secSpinner.setAdapter(adapter);
        minSpinner.setAdapter(adapter);
        hourSpinner.setAdapter(adapter);
        
        //set onlicklisteners
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        refreshButton  = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new refreshButtonListener());
        

        
        clock.reset(COUNTDOWN, 0, 0, 0);  // inform clock class to count down now
    }

    /**
     * Set the layout to {@link #LAP}, and
     * inform clock class to count laps now (same clock-action as STOP).
     */
    public void lap() {
        
        current = LAP;
    	//set the Layout to the lap-mode layout
    	setContentView(R.layout.lap);
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        setupGesture();
        
        //set the size
        TextView sepView = (TextView) findViewById(R.id.sepView1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) findViewById(R.id.sepView2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);

        startTimeView = null;
        lapView = (TextView) findViewById(R.id.lapView);
        lapView.setTextSize(VIEW_SIZE - 30);
        wroteStartTime = false;

        lapButton = (Button) findViewById(R.id.lapButton);
        lapButton.setOnClickListener(new lapButtonListener());
        
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new resetButtonListener());

        lapScroll = (ScrollView) findViewById(R.id.ScrollView01);


        // From clock's point of view:
        clock.reset(STOP, 0, 0, 0);  // lapmode behaves the same as stop
    }

    private void setupGesture() {
    	LinearLayout layout = getLayout(current);
    	gestureOverlay = new GestureOverlayView(this);
		layout.addView(gestureOverlay, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));
		ViewGroup mainViewGroup = (ViewGroup) findViewById(R.id.mainLayout);
		layout.removeView(mainViewGroup);
		gestureOverlay.addView(mainViewGroup);
		gestureOverlay.addOnGesturePerformedListener(this);
		gestureOverlay.setGestureVisible(false);

	}

    /**
     * Get the layout corresponding to a counting mode.
     * @param index  Mode number: a valid mode for {@link #current}
     * @return the LinearLayout for <tt>lapLayout</tt>,
     *   <tt>countdownLayout</tt> or <tt>stopwatchLayout</tt>
     */
    private LinearLayout getLayout(int index) {
    	switch(index) {
		case LAP:
			return (LinearLayout) findViewById(R.id.lapLayout);
			
		case COUNTDOWN:
			return (LinearLayout) findViewById(R.id.countDownLayout);
			
		case STOP:
			return (LinearLayout) findViewById(R.id.stopwatchLayout);
			
		}
    	return null;
    }

	/**
     * Set the mode to {@link #STOP}, and set layout to that normal layout.
     */
	private void stopwatch() {
		current = STOP;
		onCreate(new Bundle()); //set layout to the normal Layout
		clock.reset(STOP, 0, 0, 0); //inform clock class to stop time
	}

	/**
	 * Save our state before an Android pause or stop.
	 * @see #onRestoreInstanceState(Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		clock.fillSaveState(outState);
	}

	/**
	 * Restore our state after an Android pause or stop.
	 * Happens here (and not <tt>onCreate</tt>) to ensure the
	 * initialization is complete before this method is called.
	 * @see #onSaveInstanceState(Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle inState) {
		if (inState == null)
			return;
		final int newCurrent = inState.getInt("clockAnstopCurrent", STOP);
		setCurrentMode(newCurrent);
		clock.restoreFromSaveState(inState);
	}

	/**
	 * Restore our state after an Android exit and subsequent {@link #onCreate(Bundle)}.
	 * @param settings {@link PreferenceManager#getDefaultSharedPreferences(Context)}
	 */
	private void onRestoreInstanceState(SharedPreferences settings) {
		if ( ! settings.getBoolean("anstop_in_use", false) )
			return;
		final int newCurrent = settings.getInt("anstop_state_current", STOP);
		setCurrentMode(newCurrent);
		clock.restoreFromSaveState(settings);
	}

	/**
	 * Stop the clock thread.
	 *<P>
	 * If not {@link #isFinishing()}, relies on a separate call to
	 * {@link #onSaveInstanceState(Bundle)} to save current state.
	 *<P>
	 * If {@link #isFinishing()}, saves our current state to {@link SharedPreferences}. 
	 */
	@Override
	public void onPause()
	{
		super.onPause();
		if (! isFinishing())
		{
			clock.onAppPause();
		} else {
			clock.fillSaveState
				(PreferenceManager.getDefaultSharedPreferences(mContext));
		}

		if (dbHelper != null)
		{
			dbHelper.close();  // prevent leaks
			dbHelper = null;
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if (! clock.isStarted)
			return;

		clock.onAppResume();
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	//Save Send & Load
    	saveMenuItem = menu.add(MENU_SAVE, SAVE_ITEM, 0, R.string.save).setIcon(android.R.drawable.ic_menu_save);
    	menu.add(MENU_SEND, SEND_ITEM, 0, R.string.send).setIcon(android.R.drawable.ic_menu_send);
    	menu.add(MENU_LOAD, LOAD_ITEM, 0, R.string.load).setIcon(android.R.drawable.ic_menu_upload);
    	
    	//Mode Submenu
    	SubMenu modeMenu = menu.addSubMenu(R.string.mode).setIcon(android.R.drawable.ic_menu_more);
    	modeMenu_itemStop = modeMenu.add(MENU_MODE_GROUP, MODE_STOP, 0, R.string.stop);
    	modeMenu_itemLap = modeMenu.add(MENU_MODE_GROUP, MODE_LAP, 0, R.string.lap_mode);
    	modeMenu_itemCountdown = modeMenu.add(MENU_MODE_GROUP, MODE_COUNTDOWN, 0, R.string.countdown);
    	modeMenu.setGroupCheckable(MENU_MODE_GROUP, true, true);
    	updateModeMenuFromCurrent();
    	modeMenuItem = modeMenu.getItem();
    	
    	if(clock.isStarted) {
    		modeMenuItem.setEnabled(false);
    		saveMenuItem.setEnabled(false);
    	}

    	//Settings Menu
    	menu.add(MENU_SETTINGS, SETTINGS_ITEM, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
    	
    	//about
    	menu.add(MENU_ABOUT, ABOUT_ITEM, 0, R.string.about).setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }

    /**
     * Set the proper check in the mode menu,
     * based on the {@link #current} stopwatch mode. 
     */
	private void updateModeMenuFromCurrent() {
		switch (current)
	    	{
    		case STOP:
    			if(modeMenu_itemStop != null)
    				modeMenu_itemStop.setChecked(true);  break;
	    	case LAP:
	    		if(modeMenu_itemStop != null)
	    			modeMenu_itemLap.setChecked(true);  break;
	    	case COUNTDOWN:
	    		if(modeMenu_itemStop != null)
	    			modeMenu_itemCountdown.setChecked(true);  break;
	    	}
	}
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent i = new Intent();
    	
    	switch (item.getItemId()) {
        case SETTINGS_ITEM:
        	i.setClass(this, SettingsActivity.class);
        	startActivityForResult(i, SETTINGS_ACTIVITY);
        	return true;

        case MODE_STOP:
        	stopwatch();
        	updateModeMenuFromCurrent();
            return true;
            
        case MODE_COUNTDOWN:
        	countdown(); //set the layout to the countdown Layout
        	updateModeMenuFromCurrent();
        	return true;

        case MODE_LAP:
        	lap();
        	updateModeMenuFromCurrent();
        	return true;
        	
        case ABOUT_ITEM:
        	showDialog(ABOUT_DIALOG);
        	return true;
        	
        case SAVE_ITEM:
        	showDialog(SAVE_DIALOG);
        	return true;
        	
        case SEND_ITEM:
	        startSendMailIntent
	            (this, getResources().getString(R.string.app_name) + ": " + currentModeAsString(), createBodyFromCurrent());
        	return true;

        case LOAD_ITEM:
        	i.setClass(this, LoadActivity.class);
        	startActivity(i);
        	return true;
        	
        
        }
        return false;
    }

    /**
     * Start the appropriate intent for the user to send an E-Mail or SMS
     * with these contents.
     * @param caller  The calling activity; used to launch Send intent
     * @param title Subject line for e-mail; if user has only SMS configured, this will be unused. 
     * @param body  Body of e-mail
     */
	public static void startSendMailIntent(Activity caller, final String title, final String body) {
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		String[] empty_recipients = new String[]{ "" };
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, empty_recipients);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
		emailIntent.setType("text/plain");
		// use Chooser, in case multiple apps are installed
		caller.startActivity(Intent.createChooser(emailIntent, caller.getResources().getString(R.string.send)));  
	}

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
        case ABOUT_DIALOG:
        	AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
        	aboutBuilder.setMessage(R.string.about_dialog)
        	       .setCancelable(true)
        	.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
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
        	
        	saveBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
		    			
        				String body = createBodyFromCurrent();
        				if (dbHelper == null)
        				{
        					dbHelper = new AnstopDbAdapter(Anstop.this);
        					dbHelper.open();
        				}        		        
		    			dbHelper.createNew(input.getText().toString(), body);
		    			Toast toast = Toast.makeText(getApplicationContext(), R.string.saved_succes, Toast.LENGTH_SHORT);
		    			toast.show();
        			}

        		});
        	
        	saveBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
	        			dialog.dismiss();
        			}
        		});
        	saveBuilder.setCancelable(false);
        	dialog = saveBuilder.create();
        	break;

        default: dialog = null;
		}
		
		
        return dialog;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	readSettings(false); // because only settingsactivity is started for
    	// result, we can launch that without checking the parameters.
    }

    /**
     * Given the {@link #current current mode}, the name of that mode from resources.
     * @return mode name, or if unknown mode, "(unknown)".
     */
    private String currentModeAsString() {
    	int modus;
    	switch(current) {
		case LAP:
			modus = R.string.lap_mode;  break;
		case COUNTDOWN:
			modus = R.string.countdown;  break;
		case STOP:
			modus = R.string.stop;  break;
    	default:
    		return "(unknown)";
    	}
    	return mContext.getResources().getString(modus);
    }

    /**
     * Construct a string with the current mode, time, and laps (if applicable).
     */
	private String createBodyFromCurrent() {
		String body;
		switch(current) {
		case LAP:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.lap_mode) + "\n" 
				+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString() + "\n" + lapView.getText().toString();
			break;
		case COUNTDOWN:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.countdown) + "\n" 
				+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString() + "\n" + mContext.getResources().getString(R.string.start_time)
				+ "\n" + hourSpinner.getSelectedItemPosition() + " "
				+ mContext.getResources().getString(R.string.hour) + "\n"
				+ clock.nf.format(secSpinner.getSelectedItemPosition()) + ":" 
				+ clock.nf.format(minSpinner.getSelectedItemPosition()) + ".0"
				+ "\n" + startTimeView.getText().toString();
			break;
		case STOP:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.stop) + "\n" 
				+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString()
				+ "\n" + startTimeView.getText().toString();
			break;
		default:
			body = "ModeError";
			break;
		}
		return body;
	}

	/**
	 * Build {@link #fmt_dow_meddate_time}.
	 * @param ctx calling context
	 * @return a StringBuffer usable in {@link DateFormat#format(CharSequence, long)}
	 */
	private static StringBuffer buildDateFormatDOWmedium(Context ctx)
	{
		StringBuffer fmt_dow_meddate = new StringBuffer();
		final char da = DateFormat.DAY;
		fmt_dow_meddate.append(da);
		fmt_dow_meddate.append(da);
		fmt_dow_meddate.append(da);
		fmt_dow_meddate.append(da);
		fmt_dow_meddate.append(' ');

		// year-month-date array will be 3 chars: yMd, Mdy, etc
		final char[] ymd_order = DateFormat.getDateFormatOrder(ctx);
		for (char c : ymd_order)
		{
			fmt_dow_meddate.append(c);
			fmt_dow_meddate.append(c);
			if (c == DateFormat.YEAR)
			{
				fmt_dow_meddate.append(c);
				fmt_dow_meddate.append(c);
			}
			else if (c == DateFormat.MONTH)
				fmt_dow_meddate.append(c);
			if (c != ymd_order[2])
				fmt_dow_meddate.append(' ');
		}
		fmt_dow_meddate.append(' ');

		// now hh:mm:ss[ am/pm]
		final boolean is24 = DateFormat.is24HourFormat(ctx);
		final char hh = is24 ? DateFormat.HOUR_OF_DAY : DateFormat.HOUR;
		fmt_dow_meddate.append(hh);
		if (is24)
			fmt_dow_meddate.append(hh);
		fmt_dow_meddate.append(':');
		fmt_dow_meddate.append(DateFormat.MINUTE);
		fmt_dow_meddate.append(DateFormat.MINUTE);
		fmt_dow_meddate.append(':');
		fmt_dow_meddate.append(DateFormat.SECONDS);
		fmt_dow_meddate.append(DateFormat.SECONDS);
		if (! is24)
		{
			fmt_dow_meddate.append(' ');
			fmt_dow_meddate.append(DateFormat.AM_PM);
			fmt_dow_meddate.append(DateFormat.AM_PM);
		}
		return fmt_dow_meddate;
	}

	/**
	 * For countdown mode, handle a click of the Refresh button.
	 * If not started, set the clock and the displayed Hour/Min/Seconds from
	 * the spinners.  Otherwise show a toast (cannot refresh during count).
	 *
	 * @param onlyIfZero  If true, only set the clock to the input data if the clock is currently 0:0:0:0.
	 */
	private void clickRefreshCountdownTime(final boolean onlyIfZero)
	{
		if(!clock.isStarted) {

			if (onlyIfZero && 
				((clock.hour != 0) || (clock.min != 0) || (clock.sec != 0) || (clock.dsec != 0)))
			{
				return;  // <---  Early return: Not 0:0:0:0 ---
			}

			//set the Views to the input data
			dsecondsView.setText("0");
			
			//looking for the selected Item position (is the same as the Item itself)
			//using the NumberFormat from class clock to format 
			final int s = secSpinner.getSelectedItemPosition(),
			          m = minSpinner.getSelectedItemPosition(),
			          h = hourSpinner.getSelectedItemPosition();
			clock.reset(-1, h, m, s);
			secondsView.setText(clock.nf.format(s));
			minView.setText(clock.nf.format(m));
			hourView.setText(Integer.toString(h));
	
			startTimeView.setText("");
			wroteStartTime = false;
		}
		else {
			//Show error when currently counting
			Toast toast = Toast.makeText(mContext, R.string.refresh_during_count, Toast.LENGTH_SHORT);
			toast.show();
		}
	}
	
	/**
	 * Reset the clock and hh/mm/ss views.
	 * Clear <tt>"anstop_in_use"</tt> flag in shared preferences.
	 *<P>
	 * If isStarted, do nothing.
	 * If wasStarted, call this only after the confirmation alert.
	 */
	private void resetClockAndViews()
	{
		if(clock.isStarted)
			return;

		clock.reset(-1, 0, 0, 0);

		//reset all Views to zero
		dsecondsView.setText("0");
		secondsView.setText("00");
		minView.setText("00");
		hourView.setText("0");
		if(lapView != null)
			lapView.setText(R.string.laps);
		if(startTimeView != null)
			startTimeView.setText("");
		wroteStartTime = false;		

		// Check for an old anstop_in_use flag from previous runs
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		if(settings.getBoolean("anstop_in_use", false))
		{
			Editor outPref = settings.edit();
			outPref.putBoolean("anstop_in_use", false);
			outPref.commit();
		}
	}

    private class startButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		
    		// If starting to count in countdown mode, but 'refresh' hasn't
    		// been clicked, the clock shows 0:0:0 and nothing will happen.
    		// Refresh automatically.
    		if ((current == COUNTDOWN) && (! clock.isStarted) && (! clock.wasStarted))
    		{
    			clickRefreshCountdownTime(true);
    		}

    		// If starting to count in countdown mode, but the clock has 0:0:0,
    		// nothing will happen.  Let the user know.
    		if ((current == COUNTDOWN) && (! clock.isStarted) &&     		
				(clock.hour == 0) && (clock.min == 0) && (clock.sec == 0) && (clock.dsec == 0))
    		{
    			final int resId;
    			if (clock.wasStarted)
    				resId = R.string.countdown_completed_please_refresh;
    			else
    				resId = R.string.countdown_please_set_hms;
    			Toast.makeText(Anstop.this, resId, Toast.LENGTH_SHORT).show();

    			return; // <--- Early return: Countdown already 0:0:0:0 ---
    		}

    		clock.count();  // start or stop counting
    		
    		if(modeMenuItem != null) {
    			modeMenuItem.setEnabled(!clock.isStarted);
    			saveMenuItem.setEnabled(!clock.isStarted);
    		}
    		
    		
    		if(vib != null)
    			vib.vibrate(50);
    		
    		if (clock.isStarted && ! wroteStartTime)
    		{
				if (fmt_dow_meddate_time == null)
					fmt_dow_meddate_time = buildDateFormatDOWmedium(Anstop.this);

				StringBuffer sb = new StringBuffer();
				sb.append(getResources().getText(R.string.started_at));
				sb.append(" ");
				final long sttime = clock.getStartTimeActual();
				sb.append(DateFormat.format(fmt_dow_meddate_time, sttime));

				if (lapView != null)
    			{
    				sb.append("\n\n");
    				sb.append(lapView.getText());
    				lapView.setText(sb);
    			}
				else if (startTimeView != null)
    			{
					startTimeView.setText(sb);	
    			}
    			wroteStartTime = true;
    		}
        	
        }
    }
    
    private class resetButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		
    		if(!clock.isStarted) {
    			if (!clock.wasStarted) {
    				resetClockAndViews();
    			} else {
    				AlertDialog.Builder alert = new AlertDialog.Builder(Anstop.this);
    				alert.setTitle(R.string.confirm);
    				alert.setMessage(R.string.confirm_reset_message);

    				alert.setPositiveButton(R.string.reset, new DialogInterface.OnClickListener() {
    					public void onClick(DialogInterface dialog, int whichButton) {
    						resetClockAndViews();
    					}
    				});

    				alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
    					public void onClick(DialogInterface dialog, int whichButton) { }
    				});

    				alert.show();
    			}
    		}    		
    		else {
    			//Show error when currently counting
    			Toast toast = Toast.makeText(mContext, R.string.reset_during_count, Toast.LENGTH_SHORT);
    			toast.show();
    		}
    	}
    }
    
    private class refreshButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		if(clock.isStarted || !clock.wasStarted) {
    			clickRefreshCountdownTime(false);
    		} else {
    			AlertDialog.Builder alert = new AlertDialog.Builder(Anstop.this);
    			alert.setTitle(R.string.confirm);
    			alert.setMessage(R.string.confirm_refresh_message);

    			alert.setPositiveButton(R.string.refresh, new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int whichButton) {
    					clickRefreshCountdownTime(false);
    				}
    			});

    			alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int whichButton) { }
    			});

    			alert.show();
    		}    		
    	}
    }
    
    private class lapButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
        	lapView.append("\n" + (clock.laps++) + ". " + clock.getCurrentValue());

        	if(vib != null)
        		vib.vibrate(50);

        	// Scroll to bottom of lap times
        	lapScroll.post(new Runnable() {
        	    public void run() {
        	    	lapScroll.fullScroll(ScrollView.FOCUS_DOWN);
        	    }
        	});
    	}
    }

	public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
		if(clock.isStarted)
			return;
		List<Prediction> predictions = gestureLibrary.recognize(gesture);
	    for(Prediction prediction : predictions) {
	    	if(prediction.score > 1.0) {
	    		if(prediction.name.equals("SwipeRight")) {
	    			if(current == 2)
	    				current = 0;
	    			else
	    				current += 1;
	    			animateSwitch(false);
	    		}
	    		if(prediction.name.equals("SwipeLeft")) {
	    			if(current == 0)
	    				current = 2;
	    			else
	    				current -= 1;
	    			animateSwitch(true);
	    		}
	    	}
	    }
	}    

    private void animateSwitch(final boolean toRight) {
    	
    	Animation animation = AnimationUtils.makeOutAnimation(this, toRight);
    	
    	int modeBefore = -1;
    	if(toRight) {
    		if(current == 2)
    			modeBefore = 0;
    		else
    			modeBefore = current + 1;
    	}
    	else {
    		if(current == 0)
    			modeBefore = 2;
    		else
    			modeBefore = current - 1;
    	}
    	
    	LinearLayout layout = getLayout(modeBefore);
		animation.setAnimationListener(new AnimationListener() {
			//@Override
			public void onAnimationStart(Animation animation) {
			}
			//@Override
			public void onAnimationRepeat(Animation animation) {
			}
			//@Override
			public void onAnimationEnd(Animation animation) {
				switch(current) {
				case LAP:
					lap();
					break;
				case COUNTDOWN:
					countdown();
					break;
				case STOP:
					stopwatch();
					break;
				}
				updateModeMenuFromCurrent();
				Animation inAnim = AnimationUtils.makeInAnimation(Anstop.this, toRight); 
				getLayout(current).startAnimation(inAnim);
			}
		});
		layout.startAnimation(animation);
		
    }
    
}