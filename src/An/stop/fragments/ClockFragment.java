package An.stop.fragments;

import An.stop.AnstopActivity;
import An.stop.Clock;
import An.stop.R;
import An.stop.util.AnstopDbAdapter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.DateFormat;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public abstract class ClockFragment extends Fragment {
	
	protected static final int VIEW_SIZE = 60;
	
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
	
	AnstopDbAdapter dbHelper;
	
	/**
	 * Date formatter for day of week + user's medium date format + hh:mm:ss;
	 * used in {@link #updateStartTimeCommentLapsView(boolean)} for "started at:".
	 */
	private StringBuffer fmt_dow_meddate_time;
	
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
				dbHelper = new AnstopDbAdapter(AnstopActivity.this);
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
			fmt_dow_meddate_time = buildDateFormatDOWmedium(AnstopActivity.this);

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

	protected class StartButtonListener implements OnClickListener {
    	
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
    			Toast.makeText(AnstopActivity.this, resId, Toast.LENGTH_SHORT).show();

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
	
	protected class ResetButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		
    		if(!clock.isStarted) {
    			if (!clock.wasStarted) {
    				resetClockAndViews();
    			} else {
    				AlertDialog.Builder alert = new AlertDialog.Builder(AnstopActivity.this);
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
    
	protected class RefreshButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		if(clock.isStarted || !clock.wasStarted) {
    			clickRefreshCountdownTime(false);
    		} else {
    			AlertDialog.Builder alert = new AlertDialog.Builder(AnstopActivity.this);
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
    
    protected class LapButtonListener implements OnClickListener {

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
        		dbHelper = new AnstopDbAdapter(AnstopActivity.this);
        		dbHelper.open();
        	}
        	dbHelper.createNewLap
        		(0, clock.lap_elapsed[clock.laps - 2], clock.lap_systime[clock.laps - 2]);
    	}
    }
}
