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

import An.stop.util.AccelerometerListener;
import An.stop.util.AnstopDbAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Anstop's main activity, showing the current getClock(), lap times, etc.
 *<P>
 * Uses either of 2 layouts, depending on the {@link getClock()#getMode() current mode}:
 * <tt>main</tt> or <tt>countdown</tt>.
 * Main implements {@link #STOP_LAP} which includes Lap mode.
 *<P>
 * Many fields' visibility are non-private for use by
 * {@link getClock()#fillSaveState(Bundle)} and {@link getClock()#restoreFromSaveState(Bundle)}.
 */
public class AnstopActivity extends Activity {

	/** Stopwatch/lap mode (and layout), for {@link getClock()#getMode()} */
	public static final int STOP_LAP = 0;  // STOP,LAP combined after v1.4 (see svn r47)

	/** Countdown mode (and layout), for {@link getClock()#getMode()} */
	public static final int COUNTDOWN = 1;

	/** Lap mode (and layout), for {@link getClock()#getMode(int)} */
	private static final int OBSOL_LAP = 2;  // STOP,LAP combined after v1.4 (see svn r47)

	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;
	/** Dialog to set the optional {@link #comment} */
	private static final int COMMENT_DIALOG = 2;

	// Reminder: If you add or change fields, be sure to update
	// getClock().fillSaveState and getClock().restoreFromSaveStateFields

	/**
	 * If true, we already wrote the start date/time into {@link #lapView}
	 * or {@link #startTimeView}, by calling {@link #updateStartTimeCommentLapsView(boolean)}.
	 */
	boolean wroteStartTime;

	/**
	 * Edit text for {@link #comment} in {@link #COMMENT_DIALOG}.
	 * Null until {@link #onCreateDialog(int)} is called.
	 * Updated in {@link #onPrepareDialog(int, Dialog)}.
	 */
	private transient EditText commentEdit;

	Context mContext;
	Vibrator vib;
	
	AccelerometerListener al;
	
	/**
	 * DatabaseHelper, if opened.
	 * Usually null, unless we've saved a time to the database.
	 * Closed and set to null in {@link #onPause()}.
	 */
	AnstopDbAdapter dbHelper;

	/** Mode Menu's items, for {@link #updateModeMenuFromCurrent()} to indicate current mode. */
	private MenuItem modeMenu_itemStop;
	private MenuItem modeMenu_itemCountdown;
	
	private Clock getClock() {
		return null;
	}

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

        //set the getClock() object
        final boolean isInitialStartup = (getClock() == null);

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
        	&& savedInstanceState.containsKey("getClock()AnstopCurrent"))
        {
        	// bundle from onSaveInstanceState
        	onRestoreInstanceState(savedInstanceState);
        } else {
        	// prefs from onPause when isFinishing
        	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        	onRestoreInstanceState(settings);
        }
       
    }

    /**
     * Read our settings, at startup or after calling the SettingsActivity.
     *<P>
     * Does not check <tt>"anstop_in_use"</tt> or read the instance state
     * when the application is finishing; for that, see {@link getClock()#restoreFromSaveState(SharedPreferences)}.
     *
     * @param isStartup Are we just starting now, not already running?
     */
    private void readSettings(final boolean isStartup) {

    	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
    	if( settings.getBoolean("use_motion_sensor", false) ) {
        	al = new AccelerometerListener(this, getClock());
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
        		settingLap = getClock().LAP_FMT_FLAG_ELAPSED;
        	}
        	if (settingLap != getClock().lapf.lapFormatFlags)
        	{
        		getClock().setLapFormat
        			(settingLap, DateFormat.getTimeFormat(getApplicationContext()));
        		updateStartTimeCommentLapsView(true);
        	}
        } catch (Throwable e) {}

        if(!isStartup) return; // app was started before, user changed settings
        
        // "mode" setting: getClock() mode at startup; an int saved as string.
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
     * {@link getClock()#setLapFormat(int, java.text.DateFormat)}.
     *<UL>
     *<LI> <tt>lap_format_elapsed</tt> -&gt; {@link getClock()#LAP_FMT_FLAG_ELAPSED}
     *<LI> <tT>lap_format_delta</tt> -&gt; {@link getClock()#LAP_FMT_FLAG_DELTA}
     *<LI> <tt>lap_format_systime</tt> -&gt; {@link getClock()#LAP_FMT_FLAG_SYSTIME}
     *</UL>
     * @param settings  Shared preferences, from
     *    {@link PreferenceManager#getDefaultSharedPreferences(Context)}
     * @return Lap format flags, or 0 if none are set in <tt>settings</tt>.
     *    If this method returns 0, assume the default of
     *    {@link getClock()#LAP_FMT_FLAG_ELAPSED}.
     */
	public static int readLapFormatPrefFlags(SharedPreferences settings) {
		final boolean lapFmtElapsed = settings.getBoolean("lap_format_elapsed", true),
		              lapFmtDelta   = settings.getBoolean("lap_format_delta", false),
		              lapFmtSystime = settings.getBoolean("lap_format_systime", false);
		int settingLap = 0;
		if (lapFmtElapsed) settingLap += getClock().LAP_FMT_FLAG_ELAPSED;
		if (lapFmtDelta)   settingLap += getClock().LAP_FMT_FLAG_DELTA;
		if (lapFmtSystime) settingLap += getClock().LAP_FMT_FLAG_SYSTIME;
		return settingLap;
	}

    /**
     * Set the current mode, if the getClock() isn't currently started,
     * and load the right Layout for it.
     *<P>
     * Does nothing if {@link getClock()#isStarted}, or if current mode is already newCurrent.
     *
     * @param newCurrent {@link #STOP_LAP} or {@link #COUNTDOWN}
     */
	private void setCurrentMode(final int newCurrent)
	{
		if (getClock().isStarted || (getClock().getMode() == newCurrent)) 
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
	 * Save our state before an Android pause or stop.
	 * @see #onRestoreInstanceState(Bundle)
	 */
	@Override
	public void onSaveInstanceState(Bundle outState) {
		getClock().fillSaveState(outState);
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
		int newCurrent = inState.getInt("getClock()AnstopCurrent", STOP_LAP);
		if (newCurrent == OBSOL_LAP)
			newCurrent = STOP_LAP;
		setCurrentMode(newCurrent);
		getClock().restoreFromSaveState(inState);
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
		getClock().restoreFromSaveState(settings);
		if (getClock().laps > 1)
		{
			if (getClock().lap_elapsed.length <= getClock().laps)
			{
				getClock().lap_elapsed = new long[getClock().laps + 32];
				getClock().lap_systime = new long[getClock().laps + 32];
			}
			if (dbHelper == null)
			{
				dbHelper = new AnstopDbAdapter(AnstopActivity.this);
				dbHelper.open();
			}
			dbHelper.fetchAllLaps(0, getClock().lap_elapsed, getClock().lap_systime);
		}
	}

	/**
	 * Stop the getClock() thread.
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
			getClock().onAppPause();
		} else {
			getClock().fillSaveState
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
		if (! getClock().isStarted)
			return;

		getClock().onAppResume();
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Set the proper check in the mode menu,
     * based on the {@link getClock()#getMode() current mode}.
     */
	private void updateModeMenuFromCurrent() {
		switch (getClock().getMode())
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
        	startActivityForResult(i, R.id.menu_settings);
        	  // on result, will call readSettings(false).
        	return true;

        case R.id.menu_item_stop:
        	// TODO mae this via VIewPager
        	stopwatch();
            return true;
            
        case R.id.menu_item_countdown:
        	// TODO mae this via VIewPager
        	countdown();
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
        					dbHelper = new AnstopDbAdapter(AnstopActivity.this);
        					dbHelper.open();
        				}
        				final long id = dbHelper.createNew
        					(input.getText().toString().trim(), comment,
        					 getClock().getMode(), getClock().getStartTimeActual(), getClock().getStopTime(),
        					 getClock().getCurrentValueMillis(null, false));
		    			if (getClock().laps > 1)
		    				dbHelper.createNewLaps(id, getClock().laps - 1, getClock().lap_elapsed, getClock().lap_systime);

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
     * Given the {@link getClock()#getMode() current mode}, the name of that mode from resources.
     * @return mode name, or if unknown mode, "(unknown)".
     */
    private String currentModeAsString() {
    	int modus;
    	switch(getClock().getMode()) {
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
		switch(getClock().getMode()) {
		case COUNTDOWN:
			body = mContext.getResources().getString(R.string.mode_was) + " " 
				+ mContext.getResources().getString(R.string.countdown) + "\n" 
				+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
				+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
				+ ":" + dsecondsView.getText().toString() + "\n" + mContext.getResources().getString(R.string.start_time)
				+ "\n" + hourSpinner.getSelectedItemPosition() + " "
				+ mContext.getResources().getString(R.string.hour) + "\n"
				+ getClock().lapf.nf.format(secSpinner.getSelectedItemPosition()) + ":" 
				+ getClock().lapf.nf.format(minSpinner.getSelectedItemPosition()) + ".0"
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
	public static StringBuffer buildDateFormatDOWmedium(Context ctx)
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
}