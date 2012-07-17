/***************************************************************************
 *   Copyright (C) 2009-2011 by mj										   *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2010-2012 Jeremy Monin            *
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
 * Uses either of 2 layouts, depending on the {@link Clock#getMode() current mode}:
 * <tt>main</tt> or <tt>countdown</tt>.
 * Main implements {@link #STOP_LAP} which includes Lap mode.
 *<P>
 * Many fields' visibility are non-private for use by
 * {@link Clock#fillSaveState(Bundle)} and {@link Clock#restoreFromSaveState(Bundle)}.
 */
public class Anstop extends Activity implements OnGesturePerformedListener {


	private static final int MENU_MODE_GROUP = 0;
	/** Menu item to choose {@link #STOP_LAP} mode */
	private static final int MODE_STOP = 1;
	// private static final int MODE_LAP = 2;  // removed in r47
	/** Menu item to choose {@link #COUNTDOWN} mode */
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

	/** Stopwatch/lap mode (and layout), for {@link Clock#getMode()} */
	public static final int STOP_LAP = 0;  // STOP,LAP combined after v1.4 (see svn r47)

	/** Countdown mode (and layout), for {@link Clock#getMode()} */
	public static final int COUNTDOWN = 1;

	/** Lap mode (and layout), for {@link Clock#getMode(int)} */
	private static final int OBSOL_LAP = 2;  // STOP,LAP combined after v1.4 (see svn r47)

	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;
	/** Dialog to set the optional {@link #comment} */
	private static final int COMMENT_DIALOG = 2;
	
	private static final int SETTINGS_ACTIVITY = 0;
	
	private static final int VIEW_SIZE = 60;

	// Reminder: If you add or change fields, be sure to update
	// Clock.fillSaveState and Clock.restoreFromSaveStateFields

	/**
	 * If true, we already wrote the start date/time into {@link #lapView}
	 * or {@link #startTimeView}, by calling {@link #updateStartTimeCommentLapsView(boolean)}.
	 */
	boolean wroteStartTime;

	/**
	 * Date formatter for day of week + user's medium date format + hh:mm:ss;
	 * used in {@link #updateStartTimeCommentLapsView(boolean)} for "started at:".
	 */
	private StringBuffer fmt_dow_meddate_time;

	Clock clock;
	/** Lap data for {@link #lapView}. */
	StringBuilder laps;
	/**
	 * Optional comment, or null.
	 * In the layout, Start time, <tt>comment</tt>, and {@link #laps}
	 * are all shown in startTimeView or LapView.
	 * @see #updateStartTimeCommentLapsView(boolean)
	 */
	String comment;
	/** start/stop (resume/pause) */
	Button startButton;
	/** in stopwatch/lap mode, reset the count */
	Button resetButton;
	/** in countdown mode, sets the hour/minute/second views to the input spinners' current data */
	Button refreshButton;
	Button lapButton;
	TextView dsecondsView;
	TextView secondsView;
	TextView minView;
	TextView hourView;
	/** shows start time and {@link #comment} in the countdown layout, which doesn't contain {@link #lapView} */
	TextView startTimeView;
	/** shows start time, {@link #comment}, and {@link #laps}. When <tt>lapView</tt> is non-null, {@link #startTimeView} is null */
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

	/**
	 * Edit text for {@link #comment} in {@link #COMMENT_DIALOG}.
	 * Null until {@link #onCreateDialog(int)} is called.
	 * Updated in {@link #onPrepareDialog(int, Dialog)}.
	 */
	private transient EditText commentEdit;

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
	private MenuItem modeMenu_itemCountdown;

	/**
	 * Called when the activity is first created.
	 * Assumes {@link #STOP_LAP} mode and sets that layout.
	 * Preferences are read, which calls setCurrentMode.
	 * Also called later, to set the mode/layout back to {@link #STOP_LAP}.
	 */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Set the default shared pref values, especially for checkboxes.
        // See android bug http://code.google.com/p/android/issues/detail?id=6641
    	PreferenceManager.setDefaultValues(this, R.xml.settings, true);

        //set the clock object
        final boolean isInitialStartup = (clock == null);
        if (isInitialStartup)
        	clock = new Clock(this);

        // set Views Buttons and Listeners for the new Layout;
        // set current and clock to STOP_LAP mode at 00:00:00.0
        stopwatch();

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

        // Lap Display Format setting
        try
        {
        	// read the flags, possibly just changed by user; default is LAP_FMT_FLAG_ELAPSED only
        	int settingLap = readLapFormatPrefFlags(settings);
        	if (settingLap == 0)
        	{
        		// Should not happen, but if it does, correct it to default
        		settings.edit().putBoolean("lap_format_elapsed", true).commit();
        		settingLap = Clock.LAP_FMT_FLAG_ELAPSED;
        	}
        	if (settingLap != clock.lapf.lapFormatFlags)
        	{
        		clock.setLapFormat
        			(settingLap, DateFormat.getTimeFormat(getApplicationContext()));
        		updateStartTimeCommentLapsView(true);
        	}
        } catch (Throwable e) {}

        if(!isStartup) return; // app was started before, user changed settings
        
        // "mode" setting: Clock mode at startup; an int saved as string.
        try
        {
	        int settingMode = Integer.parseInt(settings.getString("mode", "0")); // 0 == STOP_LAP
	        if (settingMode == OBSOL_LAP)
	        {
	        	settingMode = STOP_LAP;
	        	Editor outPref = settings.edit();
	        	outPref.putString("mode", "0");
	        	outPref.commit();
	        }
	        setCurrentMode(settingMode);
        } catch (NumberFormatException e) {}
        
        if(settings.getBoolean("first_start", true)) {
        	// Show once: "Tip: You can swipe left or right, to change the mode!"
        	Toast.makeText(getApplicationContext(), R.string.first_start, Toast.LENGTH_LONG).show();
        	Editor outPref = settings.edit();
        	outPref.putBoolean("first_start", false);
        	outPref.commit();
        }
    }

    /**
     * Read the boolean lap format flags from shared preferences,
     * and add them together in the format used by
     * {@link Clock#setLapFormat(int, java.text.DateFormat)}.
     *<UL>
     *<LI> <tt>lap_format_elapsed</tt> -&gt; {@link Clock#LAP_FMT_FLAG_ELAPSED}
     *<LI> <tT>lap_format_delta</tt> -&gt; {@link Clock#LAP_FMT_FLAG_DELTA}
     *<LI> <tt>lap_format_systime</tt> -&gt; {@link Clock#LAP_FMT_FLAG_SYSTIME}
     *</UL>
     * @param settings  Shared preferences, from
     *    {@link PreferenceManager#getDefaultSharedPreferences(Context)}
     * @return Lap format flags, or 0 if none are set in <tt>settings</tt>.
     *    If this method returns 0, assume the default of
     *    {@link Clock#LAP_FMT_FLAG_ELAPSED}.
     */
	public static int readLapFormatPrefFlags(SharedPreferences settings) {
		final boolean lapFmtElapsed = settings.getBoolean("lap_format_elapsed", true),
		              lapFmtDelta   = settings.getBoolean("lap_format_delta", false),
		              lapFmtSystime = settings.getBoolean("lap_format_systime", false);
		int settingLap = 0;
		if (lapFmtElapsed) settingLap += Clock.LAP_FMT_FLAG_ELAPSED;
		if (lapFmtDelta)   settingLap += Clock.LAP_FMT_FLAG_DELTA;
		if (lapFmtSystime) settingLap += Clock.LAP_FMT_FLAG_SYSTIME;
		return settingLap;
	}

    /**
     * Set the current mode, if the clock isn't currently started,
     * and load the right Layout for it.
     *<P>
     * Does nothing if {@link Clock#isStarted}, or if current mode is already newCurrent.
     *
     * @param newCurrent {@link #STOP_LAP} or {@link #COUNTDOWN}
     */
	private void setCurrentMode(final int newCurrent)
	{
		if (clock.isStarted || (clock.getMode() == newCurrent)) 
			return;

		switch (newCurrent)
		{
		case STOP_LAP:
			stopwatch();
			break;

		case COUNTDOWN:
			countdown();
			break;

		case OBSOL_LAP: // STOP,LAP combined after v1.4 (see svn r47)
			stopwatch();
			break;
		}
	}

    /**
     * Set the layout to {@link #COUNTDOWN}, and
     * inform clock class to count down now.
     */
    public void countdown() {
        comment = null;
        if ((laps != null) && (laps.length() > 0))
        {
        	laps.delete(0, laps.length());
        	if (dbHelper == null)
        	{
        		dbHelper = new AnstopDbAdapter(Anstop.this);
        		dbHelper.open();
        	}
        	dbHelper.deleteTemporaryLaps();
        }

    	//set the Layout to the countdown layout
    	setContentView(R.layout.countdown);
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        startTimeView = (TextView) findViewById(R.id.countdown_startTimeView);
        setupCommentLongPress(startTimeView);
        lapView = null;
        lapScroll = null;
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

        // inform clock class to count down now
        clock.changeMode(COUNTDOWN);

        setupGesture();
    }

    /**
     * Set the layout to {@link #STOP_LAP}, and reset clock to 0:0:0.0.
     * inform clock class to count laps now (same clock-action as STOP).
     */
    public void stopwatch() {
        comment = null;
        if (laps == null)
        	laps = new StringBuilder();
        else if (laps.length() > 0)
        {
        	laps.delete(0, laps.length());
        	if (dbHelper == null)
        	{
        		dbHelper = new AnstopDbAdapter(Anstop.this);
        		dbHelper.open();
        	}
        	dbHelper.deleteTemporaryLaps();
        }
        laps.append(getResources().getString(R.string.laps));

    	//set the Layout to the stopwatch/lap-mode layout
    	setContentView(R.layout.main);
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        
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
        setupCommentLongPress(lapView);
        wroteStartTime = false;

        lapButton = (Button) findViewById(R.id.lapButton);
        lapButton.setOnClickListener(new lapButtonListener());
        
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new resetButtonListener());

        lapScroll = (ScrollView) findViewById(R.id.lapScrollView);

        // inform clock of the new mode
        clock.changeMode(STOP_LAP);

        setupGesture();
    }

    /**
     * Set up swipe gesture, based on the current mode and layout.
     */
    private void setupGesture() {
		ViewGroup layout = getLayout(clock.getMode());  // layout for entire activity
    	gestureOverlay = new GestureOverlayView(this);
		ViewGroup mainViewGroup = (ViewGroup) findViewById(R.id.mainLayout);

		// remove before add, in case layout is ScrollView and can have only 1 child:
		((ViewGroup)mainViewGroup.getParent()).removeView(mainViewGroup);  // avoid "specified child already has a parent"
		layout.removeAllViews();  // avoid "ScrollView can host only one direct child"
		layout.addView(gestureOverlay, new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT, 1));

		gestureOverlay.addView(mainViewGroup);
		gestureOverlay.addOnGesturePerformedListener(this);
		gestureOverlay.setGestureVisible(false);
	}

	/**
	 * Set up long-press on the read-only start time / lap textview
	 * to allow editing the optional {@link #comment}.
	 * @param tv  Either {@link #lapView} or {@link #startTimeView}
	 */
	private void setupCommentLongPress(TextView tv) {
		tv.setOnLongClickListener(new View.OnLongClickListener() {			
			public boolean onLongClick(View v) {
				showDialog(COMMENT_DIALOG);
				return true;
			}
		});		
	}

    /**
     * Get the activity layout corresponding to a counting mode.
     * <tt>mainLayout</tt> is contained inside this one.
     * @param index  Mode number: a valid mode for {@link Clock#getMode()}
     * @return the layout (LinearLayout or ScrollView) for
     *   <tt>countdownLayout</tt> or <tt>stopwatchLayout</tt>
     */
    private ViewGroup getLayout(final int index) {
    	switch(index) {
		case COUNTDOWN:
			return (ViewGroup) findViewById(R.id.countDownLayout);
			
		case STOP_LAP:
			return (ViewGroup) findViewById(R.id.stopwatchLayout);
			
		}
    	return null;
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
		int newCurrent = inState.getInt("clockAnstopCurrent", STOP_LAP);
		if (newCurrent == OBSOL_LAP)
			newCurrent = STOP_LAP;
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
		int newCurrent = settings.getInt("anstop_state_current", STOP_LAP);
		if (newCurrent == OBSOL_LAP)
			newCurrent = STOP_LAP;
		setCurrentMode(newCurrent);
		clock.restoreFromSaveState(settings);
		if (clock.laps > 1)
		{
			if (clock.lap_elapsed.length <= clock.laps)
			{
				clock.lap_elapsed = new long[clock.laps + 32];
				clock.lap_systime = new long[clock.laps + 32];
			}
			if (dbHelper == null)
			{
				dbHelper = new AnstopDbAdapter(Anstop.this);
				dbHelper.open();
			}
			dbHelper.fetchAllLaps(0, clock.lap_elapsed, clock.lap_systime);
		}
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
     * based on the {@link Clock#getMode() current mode}.
     */
	private void updateModeMenuFromCurrent() {
		switch (clock.getMode())
	    	{
    		case STOP_LAP:
    			if(modeMenu_itemStop != null)
    				modeMenu_itemStop.setChecked(true);  break;
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
        	  // on result, will call readSettings(false).
        	return true;

        case MODE_STOP:
        	changeModeOrPopupConfirm(true, clock.getMode(), STOP_LAP);
            return true;
            
        case MODE_COUNTDOWN:
        	changeModeOrPopupConfirm(false, clock.getMode(), COUNTDOWN);
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
		    			
        				if (dbHelper == null)
        				{
        					dbHelper = new AnstopDbAdapter(Anstop.this);
        					dbHelper.open();
        				}
        				final long id = dbHelper.createNew
        					(input.getText().toString().trim(), comment,
        					 clock.getMode(), clock.getStartTimeActual(), clock.getStopTime(),
        					 clock.getCurrentValueMillis(null, false));
		    			if (clock.laps > 1)
		    				dbHelper.createNewLaps(id, clock.laps - 1, clock.lap_elapsed, clock.lap_systime);

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

        case COMMENT_DIALOG:
        	AlertDialog.Builder commentBuilder = new AlertDialog.Builder(this);
        	commentBuilder.setTitle(R.string.comment);
        	if (commentEdit == null)
        		commentEdit = new EditText(this);
        	final EditText inputComm = commentEdit;
        	// commentEdit contents are set from comment in onPrepareDialog
        	commentBuilder.setView(inputComm);

        	commentBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
	    				comment = inputComm.getText().toString().trim();
	    				if (comment.length() == 0)
	    					comment = null;
	    				updateStartTimeCommentLapsView(false);
        			}
        		});

        	commentBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) { }
        		});
        	dialog = commentBuilder.create();
        	break;

        default: dialog = null;
		}
		
		
        return dialog;
    }

    /**
     * Update {@link #commentEdit} before showing {@link #COMMENT_DIALOG}.
     */
    protected void onPrepareDialog(final int id, Dialog dialog)
    {
    	if ((id != COMMENT_DIALOG) || (commentEdit == null))
    	{
    		super.onPrepareDialog(id, dialog);
    		return;
    	}

    	if (comment != null)
    		commentEdit.setText(comment);
    	else
    		commentEdit.setText("");
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	readSettings(false); // because only settingsactivity is started for
    	// result, we can launch that without checking the parameters.
    }

    /**
     * Given the {@link Clock#getMode() current mode}, the name of that mode from resources.
     * @return mode name, or if unknown mode, "(unknown)".
     */
    private String currentModeAsString() {
    	int modus;
    	switch(clock.getMode()) {
		case COUNTDOWN:
			modus = R.string.countdown;  break;
		case STOP_LAP:
			modus = R.string.stop;  break;
    	default:
    		return "(unknown)";
    	}
    	return mContext.getResources().getString(modus);
    }

    /**
     * Construct a string with the current mode, time,
     * {@link #comment}, and {@link #laps} (if applicable).
     */
	private String createBodyFromCurrent() {
		// Start time, comment, and laps are all
		// within startTimeView's or LapView's text.
		// The same formatting is used in updateStartTimeCommentLapsView
		// and AnstopDbAdapter.getRowAndFormat. If you change this, change those to match.
		// Code is not shared because this method doesn't need to re-read
		// the laps or reformat them.

		String body;
		switch(clock.getMode()) {
		case COUNTDOWN:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.countdown) + "\n" 
				+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString() + "\n" + mContext.getResources().getString(R.string.start_time)
				+ "\n" + hourSpinner.getSelectedItemPosition() + " "
				+ mContext.getResources().getString(R.string.hour) + "\n"
				+ clock.lapf.nf.format(secSpinner.getSelectedItemPosition()) + ":" 
				+ clock.lapf.nf.format(minSpinner.getSelectedItemPosition()) + ".0"
				+ "\n" + startTimeView.getText().toString();
			break;
		case STOP_LAP:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.stop) + "\n" 
				+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString()
				+ "\n" + lapView.getText().toString();
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
	static StringBuffer buildDateFormatDOWmedium(Context ctx)
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
	 * @param onlyIfZero  If true, set the clock to the input data only if the clock is currently 0:0:0:0.
	 * @see #resetClockAndViews()
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
			secondsView.setText(clock.lapf.nf.format(s));
			minView.setText(clock.lapf.nf.format(m));
			hourView.setText(Integer.toString(h));
	
			wroteStartTime = false;
			if (comment == null)
				startTimeView.setText("");
			else
				startTimeView.setText(comment);
		}
		else {
			//Show error when currently counting
			Toast toast = Toast.makeText(mContext, R.string.refresh_during_count, Toast.LENGTH_SHORT);
			toast.show();
		}
	}
	
	/**
	 * For stopwatch/lap mode,
	 * Reset the clock and hh/mm/ss views.
	 * Clear <tt>"anstop_in_use"</tt> flag in shared preferences.
	 *<P>
	 * If isStarted, do nothing.
	 * If wasStarted, call this only after the confirmation alert.
	 *
	 * @see #clickRefreshCountdownTime(boolean)
	 */
	private void resetClockAndViews()
	{
		if(clock.isStarted)
			return;

		final boolean anyLaps = (clock.laps > 1);
		clock.reset(-1, 0, 0, 0);

		//reset all Views to zero
		dsecondsView.setText("0");
		secondsView.setText("00");
		minView.setText("00");
		hourView.setText("0");
		if((laps != null) && (laps.length() > 0))
			laps.delete(0, laps.length());
		if(lapView != null)
		{
			final String lapsHeader = getResources().getString(R.string.laps); 
			if (laps == null)
				laps = new StringBuilder();
			laps.append(lapsHeader);
			lapView.setText(lapsHeader);
		}
		if(startTimeView != null)
			startTimeView.setText("");
		wroteStartTime = false;
		comment = null;
		if (anyLaps)
		{
			if (dbHelper == null)
			{
				dbHelper = new AnstopDbAdapter(Anstop.this);
				dbHelper.open();
			}
			dbHelper.deleteTemporaryLaps();
		}

		// Check for an old anstop_in_use flag from previous runs
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		if(settings.getBoolean("anstop_in_use", false))
		{
			Editor outPref = settings.edit();
			outPref.putBoolean("anstop_in_use", false);
			outPref.commit();
		}
	}

	/**
	 * Format and write the start time, {@link #comment},
	 * and {@link #laps} (if applicable) displayed
	 * in {@link #startTimeView} or {@link #lapView}.
	 *<P>
	 * The same formatting is used in {@link AnstopDbAdapter#getRowAndFormat(long)}.
	 * If you change this method, change that one to match.
	 *<P>
	 * {@link #createBodyFromCurrent()} also uses the same format;
	 * code is not shared because that method doesn't need to re-read
	 * the laps or reformat them.
	 *
	 * @param lapFormatChanged  True if the lap format flags have changed
	 */
	void updateStartTimeCommentLapsView(final boolean lapFormatChanged) {
		if (fmt_dow_meddate_time == null)
			fmt_dow_meddate_time = buildDateFormatDOWmedium(Anstop.this);

		StringBuffer sb = new StringBuffer();

		final long sttime = clock.getStartTimeActual();
		if (sttime != -1L)
		{
			sb.append(getResources().getText(R.string.started_at));
			sb.append(" ");
			sb.append(DateFormat.format(fmt_dow_meddate_time, sttime));
		}

		if ((comment != null) && (comment.length() > 0))
		{
			if (sb.length() > 0)
				sb.append("\n\n");
			sb.append(comment);
		}

		if (lapView != null)
		{
			if (lapFormatChanged)
			{
				laps.delete(0, laps.length());  // clear previous contents
				clock.formatTimeAllLaps(laps);
			}
			if (sb.length() > 0)
				sb.append("\n\n");
			sb.append(laps);
			lapView.setText(sb);
		}
		else if (startTimeView != null)
		{
			startTimeView.setText(sb);	
		}
	}

    private class startButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		final int currentMode = clock.getMode();
    		
    		// If starting to count in countdown mode, but 'refresh' hasn't
    		// been clicked, the clock shows 0:0:0 and nothing will happen.
    		// Refresh automatically.
    		if ((currentMode == COUNTDOWN) && (! clock.isStarted) && (! clock.wasStarted))
    		{
    			clickRefreshCountdownTime(true);
    		}

    		// If starting to count in countdown mode, but the clock has 0:0:0,
    		// nothing will happen.  Let the user know.
    		if ((currentMode == COUNTDOWN) && (! clock.isStarted) &&     		
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
    			updateStartTimeCommentLapsView(false);
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

    	/** lap time for {@link #onClick()}; is empty between uses */
    	private StringBuilder sb = new StringBuilder();

    	/**
    	 * Lap button clicked; get clock time from
    	 * {@link Clock#lap(StringBuffer)},
    	 * append it to {@link #laps} and {@link #lapView}.
    	 */
    	public void onClick(View v) {
    		sb.append("\n");
    		clock.lap(sb);  // format: "lap. #h mm:ss:d"
        	laps.append(sb);
        	lapView.append(sb);

        	if(vib != null)
        		vib.vibrate(50);

        	// clear sb for the next onClick
        	sb.delete(0, sb.length());

        	// Scroll to bottom of lap times
        	lapScroll.post(new Runnable() {
        	    public void run() {
        	    	lapScroll.fullScroll(ScrollView.FOCUS_DOWN);
        	    }
        	});

        	// Record new lap in the db
        	if (dbHelper == null)
        	{
        		dbHelper = new AnstopDbAdapter(Anstop.this);
        		dbHelper.open();
        	}
        	dbHelper.createNewLap
        		(0, clock.lap_elapsed[clock.laps - 2], clock.lap_systime[clock.laps - 2]);
    	}
    }

	/**
	 * When the user swipes left or right, change to the previous/next mode
	 * only if the clock isn't running.
	 */
	public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
		if(clock.isStarted)
			return;

		List<Prediction> predictions = gestureLibrary.recognize(gesture);
	    for(Prediction prediction : predictions) {
	    	if(prediction.score > 1.0) {
	    		if(prediction.name.equals("SwipeRight")) {
	    			final int cprev = clock.getMode();
	    			final int newMode;
	    			if(cprev == 1)
	    				newMode = 0;
	    			else
	    				newMode = cprev + 1;
	    			changeModeOrPopupConfirm(false, cprev, newMode);
	    		}
	    		if(prediction.name.equals("SwipeLeft")) {
	    			final int cprev = clock.getMode();
	    			final int newMode;
	    			if(cprev == 0)
	    				newMode = 1;
	    			else
	    				newMode = cprev - 1;
	    			changeModeOrPopupConfirm(true, cprev, newMode);
	    		}
	    	}
	    }
	}    

	/**
	 * Either change the mode immediately, or bring up a popup dialog to
	 * have the user confirm the mode change because clock.wasStarted or
	 * a comment was typed.
	 * Calls {@link #popupConfirmChangeMode(boolean, int, int)}.
	 *<P>
	 * Assumes clock is not currently running, or mode change menu items
	 * would be disabled and swipes ignored.
	 *
	 * @param animateToLeft  True if decrementing the mode, false if incrementing 
	 * @param currMode  The current mode; passed to {@link #animateSwitch(boolean, int)}
	 * @param newMode  The new mode if confirmed; passed to {@link Clock#changeMode(int)}
	 */
	private void changeModeOrPopupConfirm
		(final boolean animateToLeft, final int currMode, final int newMode)
	{
		if (clock.wasStarted
			|| ((comment != null) && (comment.length() > 0)))
		{
			popupConfirmChangeMode(animateToLeft, currMode, newMode);
		} else {
			clock.changeMode(newMode);
			if (currMode != newMode)
			{
				animateSwitch(animateToLeft, currMode);
			} else {
				if (newMode == STOP_LAP)
					stopwatch();
				else
					countdown();
			}
		}
	}

	/**
	 * Show a popup dialog to have the user confirm swiping to a different mode,
	 * which will clear the current laps and comment (if any).
	 *<P>
	 * If the mode change is confirmed: Will call {@link #animateSwitch(boolean, int)}
	 * only if the modes are different, otherwise will reset the layout without animation.
	 *<P>
	 * Call this method only if we need to ask, because clock.wasStarted or a comment was typed.
	 * @param toRight  True if the old mode is exiting to the right, and the new mode coming in from the left;
	 *           passed to {@link #animateSwitch(boolean, int)}.
	 *           Opposite direction from the swipe direction;
	 *           incrementing the mode passes <tt>false</tt> here.
	 * @param currMode  The current mode; passed to {@link #animateSwitch(boolean, int)}
	 * @param newMode  The new mode if confirmed; passed to {@link Clock#changeMode(int)}
	 */
	private void popupConfirmChangeMode
		(final boolean toRight, final int currMode, final int newMode)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(Anstop.this);
		alert.setTitle(R.string.confirm);
		alert.setMessage(R.string.confirm_change_mode_message);

		alert.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				clock.changeMode(newMode);
				if (currMode != newMode)
				{
					animateSwitch(toRight, currMode);
				} else {
					if (newMode == STOP_LAP)
						stopwatch();
					else
						countdown();
				}
			}
		});

		alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) { }
		});

		alert.show();
	}

    /**
     * Animate changing the current mode after a SwipeRight or SwipeLeft gesture.
     * You must call {@link Clock#changeMode(int)} before calling.  Uses {@link AnimationUtils}.
     * Calls {@link #updateModeMenuFromCurrent()} for the new mode.
     *<P>
     * Note: This method's animations do not cause {@link #onPause()} or {@link #onResume()}.
     * 
     * @param toRight  True if the old mode is exiting to the right, and the new mode coming in from the left
     * @param modeBefore  The previous mode, before {@link Clock#changeMode(int)} was called
     * @see #onGesturePerformed(GestureOverlayView, Gesture)
     */
    private void animateSwitch(final boolean toRight, final int modeBefore) {
    	
    	Animation animation = AnimationUtils.makeOutAnimation(this, toRight);

    	ViewGroup layout = getLayout(modeBefore);
		animation.setAnimationListener(new AnimationListener() {
			//@Override
			public void onAnimationStart(Animation animation) {
			}
			//@Override
			public void onAnimationRepeat(Animation animation) {
			}
			//@Override
			public void onAnimationEnd(Animation animation) {
				switch(clock.getMode()) {
				case COUNTDOWN:
					countdown();
					break;
				case STOP_LAP:
					stopwatch();
					break;
				}
				updateModeMenuFromCurrent();
				Animation inAnim = AnimationUtils.makeInAnimation(Anstop.this, toRight); 
				getLayout(clock.getMode()).startAnimation(inAnim);
			}
		});
		layout.startAnimation(animation);
		
    }
    
}