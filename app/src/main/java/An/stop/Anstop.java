/***************************************************************************
 *   Copyright (C) 2009-2011 by mj										   *
 *    fakeacc.mj@gmail.com  										       *
 *   Portions of this file Copyright (C) 2010-2012,2014-2016 Jeremy Monin  *
 *    jeremy@nand.net                                                      *
 *   Portions of this file Copyright (C) 2018 Yeshe Santos García          *
 *    civyshk@gmail.com                                                    *
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
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
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
 *<P>
 * There is a developer flag {@link #DEBUG_LOG_ENABLED} that, when set, adds a Debug Log
 * which can be viewed on the device as Anstop runs, without connecting to a computer.
 */
public class Anstop extends Activity implements OnGesturePerformedListener {


	/**
	 * If true, show Debug Log in menu; see {@link #addDebugLog(CharSequence)}.
	 * The log is meant to be viewed on the device as Anstop runs, without connecting to a computer.
	 * For non-development releases, be sure to set this false.
	 */
	private static final boolean DEBUG_LOG_ENABLED = true;

	/** Stopwatch/lap mode (and layout), for {@link Clock#getMode()} */
	public static final int STOP_LAP = 0;  // STOP,LAP combined after v1.4 (see svn r47)

	/** Countdown mode (and layout), for {@link Clock#getMode()} */
	public static final int COUNTDOWN = 1;

	/** Lap mode (and layout), for {@link Clock#getMode()} */
	private static final int OBSOL_LAP = 2;  // STOP,LAP combined after v1.4 (see svn r47)

	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;
	/** Dialog to set the optional {@link #comment} */
	private static final int COMMENT_DIALOG = 2;
	/** Dialog to show scrolling debug log, if {@link #DEBUG_LOG_ENABLED}. */
	private static final int DEBUG_LOG_DIALOG = 3;
	
	private static final int SETTINGS_ACTIVITY = 0;
	
//	private static final int VIEW_SIZE = 60;

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

	/** Date formatter for just hh:mm:ss; used in {@link #addDebugLog(CharSequence)}. */
	private StringBuffer fmt_debuglog_time;

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
	/** start/stop (resume/pause). This button is used in both layouts (Stopwatch and Countdown). */
	Button startButton;
	/** in stopwatch/lap mode, reset the count */
	Button resetButton;
	/** in countdown mode, sets the hour/minute/second views to the input spinners' current data */
	Button refreshButton;
	Button lapButton;
	TextView dsecondsView;
	TextView secondsView;
	TextView minView;
	/**
	 * The hours field and its label, shown or hidden in
	 * {@link #updateHourVisibility() and {@link Clock.hourhandler} as needed.
	 */
	TextView hourView, hourLabelView;

	/** shows start time and {@link #comment} in the countdown layout, which doesn't contain {@link #lapView} */
	TextView startTimeView;
	/** shows start time, {@link #comment}, and {@link #laps}. When <tt>lapView</tt> is non-null, {@link #startTimeView} is null */
	TextView lapView;
	/** scrollview containing {@link #lapView} */
	ScrollView lapScroll;

	/** {@link #COUNTDOWN} spinner to select starting seconds */
	NumberPicker secSpinner;
	/** {@link #COUNTDOWN} spinner to select starting minutes */
	NumberPicker minSpinner;
	/** {@link #COUNTDOWN} spinner to select starting hours */
	NumberPicker hourSpinner;

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

	/**
	 * Not null if Anstop should vibrate (quick pulse) with button presses.
	 * ({@code vibrate} preference setting)
	 *<P>
	 * Not used by other vibration preferences such as {@code vibrate_countdown_0}.
	 */
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

	private StringBuilder debugLog = new StringBuilder();

	/**
	 * Optional notes from Debug Log dialog.
	 * @since 1.6
	 */
	private StringBuilder debugNotes = new StringBuilder();

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
        addDebugLog("onCreate");

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
        {
        	addDebugLog("onCreate: not initial startup");
        	return;  // <--- Early return: Already did startup once ---
        }

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
	addDebugLog("onCreate: after onRestore, mode==" + clock.getMode() + ", isStarted==" + clock.isStarted);
        
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

        // Hour Counter and Lap Display Format settings
        try
        {
        	final int settingHour =
        		Integer.parseInt(settings.getString("hour_format", "0"));  // default Clock.HOUR_FMT_HIDE_IF_0

        	// read the flags, possibly just changed by user; default is LAP_FMT_FLAG_ELAPSED only
        	int settingLap = readLapFormatPrefFlags(settings);
        	if (settingLap == 0)
        	{
        		// Should not happen, but if it does, correct it to default
        		settings.edit().putBoolean("lap_format_elapsed", true).commit();
        		settingLap = Clock.LAP_FMT_FLAG_ELAPSED;
        	}

		boolean needUpdate = false;
		if (isStartup || (settingHour != clock.lapf.hourFormat))
		{
			needUpdate = true;
			clock.setHourFormat(settingHour);
			updateHourVisibility();
		}
        	if (settingLap != clock.lapf.lapFormatFlags)
        	{
        		clock.setLapFormat
        			(settingLap, DateFormat.getTimeFormat(getApplicationContext()));
			needUpdate = true;
        	}

		if (needUpdate)
			updateStartTimeCommentLapsView(true);
        } catch (Throwable e) { e.printStackTrace(); }

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
	 * Show or hide {@link #hourView} and {@link #hourLabelView} if needed,
	 * based on hour != 0 and current {@link Clock.LapFormatter#hourFormat} setting.
	 * @since 1.6
	 */
	private void updateHourVisibility()
	{
		final boolean hourWantVisible = (clock.hour > 0)
			|| (clock.lapf.hourFormat == Clock.HOUR_FMT_ALWAYS_SHOW);
		final int hourVis = (hourWantVisible) ? View.VISIBLE : View.GONE;

		if (hourLabelView != null)
			hourLabelView.setVisibility(hourVis);
		if (hourView != null)
			hourView.setVisibility(hourVis);
	}

    /**
     * Set the current mode, if the clock isn't currently started,
     * and load the right Layout for it.
     * Calls {@link #stopwatch()} or {@link #countdown()}.
     *<P>
     * Does nothing if {@link Clock#isStarted}, or if current mode is already {@code newCurrent}.
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
        hourLabelView = (TextView) findViewById(R.id.hourLabelView);
        startTimeView = (TextView) findViewById(R.id.startTimeView);
        setupCommentLongPress(startTimeView);
        lapView = null;
        lapScroll = null;
        if (startTimeView.length() == 0)
        	wroteStartTime = false;

        //adding spinners
        secSpinner = (NumberPicker) findViewById(R.id.secPicker);
        minSpinner = (NumberPicker) findViewById(R.id.minPicker);
        hourSpinner = (NumberPicker) findViewById(R.id.houPicker);

        secSpinner.setMaxValue(59);
        minSpinner.setMaxValue(59);
        hourSpinner.setMaxValue(24*365);

        //set onlicklisteners
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        refreshButton  = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new refreshButtonListener());

	updateHourVisibility();

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
        hourLabelView = (TextView) findViewById(R.id.hourLabelView);

        startTimeView = (TextView) findViewById(R.id.startTimeView);;
        lapView = (TextView) findViewById(R.id.lapView);
        setupCommentLongPress(lapView);
        wroteStartTime = false;

        lapButton = (Button) findViewById(R.id.lapButton);
        lapButton.setOnClickListener(new LapButtonListener());
        
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new resetButtonListener());

        lapScroll = (ScrollView) findViewById(R.id.lapScrollView);

		updateHourVisibility();

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
		addDebugLog("onSaveInstanceState");
		clock.fillSaveState(outState);
	}

	/**
	 * Restore our state after an Android pause or stop.
	 * Happens here (and not <tt>onCreate</tt>) to ensure the
	 * initialization is complete before this method is called.
	 *<P>
	 * Also checks if SharedPreferences were saved more recently,
	 * if so will directly call {@link #onRestoreInstanceState(SharedPreferences)}.
	 *
	 * @see #onSaveInstanceState(Bundle)
	 */
	@Override
	public void onRestoreInstanceState(Bundle inState) {
		addDebugLog("onRestoreInstanceState(B); inState == " + inState);
		if (inState == null)
			return;

		final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		final long bundleSavedAt = inState.getLong("clockStateSaveTime"),
			spSavedAt = (settings != null) ? settings.getLong("anstop_state_clockStateSaveTime", -1L)
					: -1L;
		addDebugLog("onRestoreInstanceState(B); bundleSavedAt, spSavedAt == "
			+ bundleSavedAt + ", " + spSavedAt);

		if ((spSavedAt == -1) || (spSavedAt < bundleSavedAt))
		{
			int newCurrent = inState.getInt("clockAnstopCurrent", STOP_LAP);
			if (newCurrent == OBSOL_LAP)
				newCurrent = STOP_LAP;
			setCurrentMode(newCurrent);
			clock.restoreFromSaveState(inState);
		} else {
			onRestoreInstanceState(settings);
		}
	}

	/**
	 * Restore our state after an Android exit and subsequent {@link #onCreate(Bundle)}.
	 * @param settings {@link PreferenceManager#getDefaultSharedPreferences(Context)}
	 */
	private void onRestoreInstanceState(SharedPreferences settings) {
		addDebugLog("onRestoreInstanceState(SP); settings == " + settings);
		if (settings.contains("anstop_in_use"))
			addDebugLog("onRestoreInstanceState: anstop_in_use=="
				+ settings.getBoolean("anstop_in_use", false));
		else
			addDebugLog("onRestoreInstanceState: anstop_in_use (key not found)");

		// To be cautious, also check the two clockActive flags
		final boolean inUse = settings.getBoolean("anstop_in_use", false)
			|| settings.getBoolean("anstop_state_clockActive", false)
			|| settings.getBoolean("anstop_state_clockWasActive", false);

		// temporary code to log some contents if present; based on Clock.restoreSaveState
		{
			addDebugLog("anstop_state_current = " + settings.getInt("anstop_state_current", -1));
			addDebugLog("anstop_state_clockDigits (h,m,s,d) = "
				+ settings.getInt("anstop_state_clockDigits_h", -1)
				+ ", " + settings.getInt("anstop_state_clockDigits_m", -1)
				+ ", " + settings.getInt("anstop_state_clockDigits_s", -1)
				+ ", " + settings.getInt("anstop_state_clockDigits_d", -1));

			if (settings.contains("anstop_state_clockActive"))
				addDebugLog("anstop_state_clockActive = "
					+ settings.getBoolean("anstop_state_clockActive", false));
			else
				addDebugLog("anstop_state_clockActive (not found)");
			if (settings.contains("anstop_state_clockWasActive"))
				addDebugLog("anstop_state_clockWasActive = "
					+ settings.getBoolean("anstop_state_clockWasActive", false));
			else
				addDebugLog("anstop_state_clockWasActive (not found)");

			if (settings.contains("anstop_state_clockCountHour"))
				addDebugLog("anstop_state_clockCountHour,Min,Sec = "
					+ settings.getInt("anstop_state_clockCountHour", -1)
					+ ", " + settings.getInt("anstop_state_clockCountMin", -1)
					+ ", " + settings.getInt("anstop_state_clockCountSec", -1));
			else
				addDebugLog("anstop_state_clockCountHour (not found)");

			if (settings.contains("anstop_state_hourFormat"))
				addDebugLog("anstop_state_hourFormat = "
					+ settings.getInt("anstop_state_hourFormat", -1));
			else
				addDebugLog("anstop_state_hourFormat (not found)");

			addDebugLog("anstop_state_clockStateSaveTime = "
				+ settings.getLong("anstop_state_clockStateSaveTime", -1L));
			addDebugLog("current time (millis) = " + System.currentTimeMillis());
			addDebugLog("anstop_state_clockLapCount = "
				+ settings.getInt("anstop_state_clockLapCount", -1));

			String comment = settings.getString("anstop_state_clockComment", null);
			if ((comment != null) && (comment.length() >= 0))
				addDebugLog("comment: " + comment);
			else
				addDebugLog("comment (not found)");
		}

		if ( ! inUse )
		{
			addDebugLog("onRestoreInstanceState: returning since anstop_in_use==false");
			return;
		}

		int newCurrent = settings.getInt("anstop_state_current", STOP_LAP);
		if (newCurrent == OBSOL_LAP)
			newCurrent = STOP_LAP;
		setCurrentMode(newCurrent);
		clock.restoreFromSaveState(settings);
		updateHourVisibility();  // reads clock.hours just set by clock.restoreFromSaveState
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
		addDebugLog("onPause; isFinishing == " + isFinishing());
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
		addDebugLog("onResume; isStarted == " + clock.isStarted + ", wasStarted == " + clock.wasStarted);
		if (! clock.isStarted)
			return;

		clock.onAppResume();
	}

    /**
     * Add to or inflate the option menu for action bar or android 2.x menu key.
     * Inflates from {@code res/menu/anstop_menu.xml}.
     * Also sets {@link #saveMenuItem}, {@link #modeMenuItem}, {@link #modeMenu_itemStop},
     * and {@link #modeMenu_itemCountdown}.
     *<P>
     * Anstop doesn't need {@code onPrepareOptionsMenu(..)} because menu item state changes
     * are handled as they occur (start or stop clock, change mode, etc).
     *
     * @param menu  Add to or inflate into this menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

    	getMenuInflater().inflate(R.menu.anstop_menu, menu);

    	saveMenuItem = menu.findItem(R.id.menu_save);

    	//Mode Submenu
    	modeMenuItem = menu.findItem(R.id.menu_mode_submenu);
    	modeMenu_itemStop = menu.findItem(R.id.menu_mode_stop);
    	modeMenu_itemCountdown = menu.findItem(R.id.menu_mode_countdown);
    	updateModeMenuFromCurrent();

    	if(clock.isStarted) {
    		modeMenuItem.setEnabled(false);
    		saveMenuItem.setEnabled(false);
    	}

    	// debug
    	if (! DEBUG_LOG_ENABLED)
    		menu.removeItem(R.id.menu_debug_log);

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
        case R.id.menu_settings:
        	i.setClass(this, SettingsActivity.class);
        	startActivityForResult(i, SETTINGS_ACTIVITY);
        	  // on result, will call readSettings(false).
        	return true;

        case R.id.menu_mode_stop:
        	changeModeOrPopupConfirm(true, clock.getMode(), STOP_LAP);
            return true;
            
        case R.id.menu_mode_countdown:
        	changeModeOrPopupConfirm(false, clock.getMode(), COUNTDOWN);
        	return true;

        case R.id.menu_about:
        	showDialog(ABOUT_DIALOG);
        	return true;
        	
        case R.id.menu_save:
        	showDialog(SAVE_DIALOG);
        	return true;
        	
        case R.id.menu_send:
	        startSendMailIntent
	            (this, getResources().getString(R.string.app_name) + ": " + currentModeAsString(), createBodyFromCurrent());
        	return true;

        case R.id.menu_load:
        	i.setClass(this, LoadActivity.class);
        	startActivity(i);
        	return true;
        	
        
        case R.id.menu_debug_log:
        	showDialog(DEBUG_LOG_DIALOG);
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
		// dialog text includes clickable URLs

            final TextView tv_about_text = new TextView(this);
            final SpannableStringBuilder about_str =
                new SpannableStringBuilder(getText(R.string.about_dialog));
            Linkify.addLinks(about_str, Linkify.WEB_URLS);
            tv_about_text.setText(about_str);
            tv_about_text.setMovementMethod(LinkMovementMethod.getInstance());

                AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
            aboutBuilder.setView(tv_about_text)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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
        	
        	saveBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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

					// Back up current state with new comments to SharedPreferences
					clock.fillSaveState
						(PreferenceManager.getDefaultSharedPreferences(mContext));

        			}
        		});

        	commentBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) { }
        		});
        	dialog = commentBuilder.create();
        	break;

        case DEBUG_LOG_DIALOG:
        	AlertDialog.Builder dldBuilder = new AlertDialog.Builder(this);

            // Include debug log text and optional notes textfield
            View v = getLayoutInflater().inflate(R.layout.debug_log, null);
            final TextView tvText = (TextView) v.findViewById(R.id.debugLogText);
            if (tvText != null)
                tvText.setText((debugLog != null) ? debugLog : "(null)" );
            final EditText etNotes = (EditText) v.findViewById(R.id.debugLogNotes);
            if (etNotes != null)
                etNotes.setText(debugNotes);

            dldBuilder.setView(v)
        		.setTitle(R.string.debug_log)
        		.setCancelable(true)
        		.setNeutralButton(android.R.string.copy, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (debugLog == null)
						return;

					// use android.text.ClipboardManager for compat with API level < 11
					android.text.ClipboardManager clipboard =
						(android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
					if (clipboard != null)
					{
						CharSequence debugText = debugLog;
						if (etNotes != null)
						{
							debugNotes.setLength(0);

							CharSequence notes = etNotes.getText();
							if (notes.length() > 0)
							{
								debugNotes.append(notes);

								StringBuilder sb = new StringBuilder(debugText);
								sb.append("\n\n");
								sb.append(notes);
								debugText = sb;
							}
						}
						clipboard.setText(debugText);
						Toast.makeText
						    (mContext, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
					}
				}
			})
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                debugNotes.setLength(0);
                CharSequence notes = etNotes.getText();
                if (notes.length() > 0)
                    debugNotes.append(notes);

                    dialog.dismiss();
                }
            });

        	dialog = dldBuilder.create();
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
	addDebugLog("onActivityResult");
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

		final String hoursStr;
		if ((clock.hour != 0) || (clock.lapf.hourFormat == Clock.HOUR_FMT_ALWAYS_SHOW))
			hoursStr = "\n" + hourView.getText().toString() + " "
				+ mContext.getResources().getString(R.string.hour);
		else
			hoursStr = "";

		String body;
		switch(clock.getMode()) {
		case COUNTDOWN:
			int spinnerHrs = hourSpinner.getValue(),
			    spinnerMin = minSpinner.getValue();
			if (clock.lapf.hourFormat == Clock.HOUR_FMT_MINUTES_PAST_60) {
				spinnerMin += (60 * spinnerHrs);
				spinnerHrs = 0;
			}
			final String spinnerHoursStr;
			if ((spinnerHrs != 0) || (clock.lapf.hourFormat == Clock.HOUR_FMT_ALWAYS_SHOW))
				spinnerHoursStr = "\n" + spinnerHrs + " "
					+ mContext.getResources().getString(R.string.hour);
			else
				spinnerHoursStr = "";

			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.countdown)
				+ hoursStr
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString() + "\n" + mContext.getResources().getString(R.string.start_time)
				+ spinnerHoursStr + "\n"
				+ clock.lapf.nf.format(spinnerMin) + ":"
				+ clock.lapf.nf.format(secSpinner.getValue()) + ".0"
				+ "\n" + startTimeView.getText().toString();
			break;

		case STOP_LAP:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.stop)
				+ hoursStr
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString()
				+ "\n" + lapView.getText().toString();
			break;

		default:
			body = "ModeError";
		}

		return body;
	}

	/**
	 * Build {@link #fmt_dow_meddate_time} or {@link #fmt_debuglog_time}.
	 * @param ctx calling context
	 * @param timeOnly  If true, wants time of day only (12- or 24-hour per user preferences).
	 *     If false, also wants day of week, year, month, day (formatted for locale).
	 * @return a StringBuffer usable in {@link DateFormat#format(CharSequence, long)}
	 */
	static StringBuffer buildDateFormat(Context ctx, final boolean timeOnly)
	{
		StringBuffer fmt_dow_meddate = new StringBuffer();

		if (! timeOnly)
		{
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
		}

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
			final int s = secSpinner.getValue(),
			          m = minSpinner.getValue(),
			          h = hourSpinner.getValue();
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

		// Clear any old anstop_in_use flags from previous runs
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		if (settings.getBoolean("anstop_in_use", false)
		    || settings.getBoolean("anstop_state_clockActive", false)
		    || settings.getBoolean("anstop_state_clockWasActive", false))
		{
			Editor outPref = settings.edit();
			outPref.putBoolean("anstop_in_use", false);
			outPref.putBoolean("anstop_state_clockActive", false);
			outPref.putBoolean("anstop_state_clockWasActive", false);
			outPref.commit();
		}
	}

	/**
	 * Format and write the start time, {@link #comment},
	 * and {@link #laps} (if applicable) displayed
	 * in {@link #startTimeView} or {@link #lapView}.
	 * Sets {@link #wroteStartTime} flag.
	 *<P>
	 * The same formatting is used in {@link AnstopDbAdapter#getRowAndFormat(long)}.
	 * If you change this method, change that one to match.
	 *<P>
	 * {@link #createBodyFromCurrent()} also uses the same format;
	 * code is not shared because that method doesn't need to re-read
	 * the laps or reformat them.
	 *
	 * @param formatChanged  True if the hour counter format or lap format flags have changed
	 */
	void updateStartTimeCommentLapsView(final boolean formatChanged) {
		if (fmt_dow_meddate_time == null)
			fmt_dow_meddate_time = buildDateFormat(Anstop.this, false);

		StringBuffer sb = new StringBuffer();

		final long sttime = clock.getStartTimeActual();
		if (sttime != -1L)
		{
			sb.append(getResources().getText(R.string.started_at));
			sb.append(" ");
			sb.append(DateFormat.format(fmt_dow_meddate_time, sttime));

			wroteStartTime = true;
		}

		if ((comment != null) && (comment.length() > 0))
		{
			if (sb.length() > 0)
				sb.append("\n\n");
			sb.append(comment);
		}

		startTimeView.setText(sb);

		if (lapView != null)
		{
			if (formatChanged)
			{
				laps.delete(0, laps.length());  // clear previous contents
				clock.formatTimeAllLaps(laps);
			}
			lapView.setText(laps);
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
    		
		if (clock.isStarted)
		{
			if (! wroteStartTime)
				updateStartTimeCommentLapsView(false);
		}

		// Back up current state to SharedPreferences
		clock.fillSaveState
			(PreferenceManager.getDefaultSharedPreferences(mContext));
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
    
    private class LapButtonListener implements OnClickListener {

    	/** lap time for {@link #onClick(View)}; is empty between uses */
    	private StringBuilder sb = new StringBuilder();

    	/**
    	 * Lap button clicked; get clock time from
    	 * {@link Clock#lap(StringBuilder)},
    	 * append it to {@link #laps} and {@link #lapView}.
    	 */
    	public void onClick(View v) {
			final boolean wasStarted = clock.wasStarted;  // get value before clock.lap()

			sb.append("\n");
			clock.lap(sb);  // format: "lap. #h mm:ss:d"

			if (! (wasStarted || wroteStartTime || clock.isStarted))
			{
				if (laps == null)
					laps = new StringBuilder();
				if (laps.length() == 0)
					laps.append(getResources().getString(R.string.laps));

				updateStartTimeCommentLapsView(false);
			}
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

			// Back up current state with new lap to SharedPreferences
			clock.fillSaveState
				(PreferenceManager.getDefaultSharedPreferences(mContext));
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
	    		else if(prediction.name.equals("SwipeLeft")) {
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
    

    /**
     * Add this to the Debug Log, if {@link #DEBUG_LOG_ENABLED}.
     * @since 1.6
     */
    private void addDebugLog(final CharSequence msg)
    {
	if (! DEBUG_LOG_ENABLED)
		return;

	if (fmt_debuglog_time == null)
		fmt_debuglog_time = buildDateFormat(Anstop.this, true);

	debugLog.append(DateFormat.format(fmt_debuglog_time, System.currentTimeMillis()));
	debugLog.append(": ");
	debugLog.append(msg);
	debugLog.append("\n");
    }
}