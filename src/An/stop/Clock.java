/***************************************************************************
 *   Copyright (C) 2009-2011 by mj   									   *
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


import java.text.DateFormat;
import java.text.NumberFormat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Timer object and thread.
 *<P>
 * Has two modes ({@link Anstop#STOP_LAP} and {@link Anstop#COUNTDOWN}); clock's mode field is {@link #v}.
 * Has accessible fields for the current {@link #hour}, {@link #min}, {@link #sec}, {@link #dsec}.
 * Has a link to the {@link #parent} Anstop, and will sometimes read or set parent's text field contents.
 *<P>
 * Because of device power saving, there are methods to adjust the clock
 * when our app is paused/resumed: {@link #onAppPause()}, {@link #onAppResume()}.
 * Otherwise the counting would become inaccurate.
 *<P>
 * Has three states:
 *<UL>
 * <LI> Reset: This is the initial state.
 *        In STOP mode, the hour, minute, second are 0.
 *        In COUNTDOWN mode they're (h, m, s), copied from spinners when
 *        the user hits the "refresh" button.
 * <LI> Started: The clock is running.
 * <LI> Stopped: The clock is not currently running, but it's changed from
 *        the initial values.  That is, the clock is paused, and the user
 *        can start it to continue counting.
 *</UL>
 * You can examine the current state by reading {@link #isStarted} and {@link #wasStarted}.
 * To reset the clock again, and/or change the mode, call {@link #reset(int, int, int, int)}.
 *<P>
 * When running, a thread either counts up ({@link #threadS}) or down ({@link #threadC}),
 * firing every 100ms.  The {@link #parent}'s hour:minute:second.dsec displays are updated
 * through {@link #hourh} and the rest of the handlers here.
 *<P>
 * To lap, call {@link #lap(StringBuffer)}.  Note that persisting the lap data arrays
 * at Activity.onStop must be done in {@link Anstop}, not here.
 * {@link #fillSaveState(Bundle)} stores the lap data arrays, but there's no corresponding
 * method to save long arrays to {@link SharedPreferences}.
 *<P>
 * Lap display formatting is done through flags such as {@link #LAP_FMT_FLAG_DELTA}
 * and the nested class {@link Clock.LapFormatter}.
 */
public class Clock {

	// Note: Currently, code and settings.xml both assume that
	//   the default format has LAP_FMT_FLAG_ELAPSED and no others,
	//   unless the user changes that preference.

	/** Lap time format flag: Elapsed. <tt>h mm:ss:d</tt> */
	public static final int LAP_FMT_FLAG_ELAPSED = 1;

	/** Lap time format flag: Delta. <tt>(+h mm:ss:d)</tt> */
	public static final int LAP_FMT_FLAG_DELTA = 2;

	/** Lap time format flag: System time. <tt>@hh:mm</tt> */
	public static final int LAP_FMT_FLAG_SYSTIME = 4;

	/**
	 * Counting mode. Two possibilities:
	 *<UL>
	 *<LI> {@link Anstop#STOP_LAP} (0), counting up from 0
	 *<LI> {@link Anstop#COUNTDOWN} (1), counting down from a time set by the user
	 *</UL>
	 * @see #getMode()
	 * @see #changeMode(int)
	 * @see #reset(int, int, int, int)
	 */
	private int v;

	/**
	 * Lap formatting flags and fields.
	 * Read-only from {@link Anstop} class.
	 * The active format flags are {@link Clock.LapFormatter#lapFormatFlags lapf.lapFormatFlags};
	 * the default is {@link #LAP_FMT_FLAG_ELAPSED} only.
	 * To change, call {@link #setLapFormat(int, DateFormat)}.
	 */
	public LapFormatter lapf;

	/** is the clock currently running? */
	public boolean isStarted = false;

	/** has the clock ran since its last reset? This is not an 'isPaused' flag, because
	 *  it's set true when the counting begins and {@link #isStarted} is set true.
	 *<P>
	 *  <tt>wasStarted</tt> is set true by {@link #count()}, and false by {@link #reset(int, int, int, int)}.
	 */
	public boolean wasStarted = false;

	clockThread threadS;
	countDownThread threadC;
	Anstop parent;
	dsechandler dsech;
	sechandler sech;
	minhandler minh;
	hourhandler hourh;
	
	int dsec = 0;
	int sec = 0;
	int min = 0;
	int hour = 0;

	/**
	 * For lap mode, the current lap number, or 1 if not lap mode.
	 * If <tt>laps</tt> &gt; 1, at least 1 lap has been recorded
	 * in {@link #lap_elapsed} and {@link #lap_systime}.
	 */
	int laps = 1;

	/**
	 * Elapsed time (milliseconds) of each lap, if lap mode.
	 * Read-only outside this class, please.
	 * The highest occupied index is ({@link #laps} - 2).
	 * If this array is about to be filled, {@link #lap(StringBuffer)} will extend it.
	 *<P>
	 * Not persisted in {@link #fillSaveState(Bundle)} or {@link #fillSaveState(SharedPreferences)}.
	 * Instead, current laps must be stored in the database.
	 * @see #lap_systime
	 */
	long[] lap_elapsed = new long[64];

	/**
	 * System time (milliseconds) of each lap, if lap mode,
	 * from {@link System#currentTimeMillis()}.
	 * Read-only outside this class, please.
	 * The highest occupied index is ({@link #laps} - 2).
	 * If this array is about to be filled, {@link #lap(StringBuffer)} will extend it.
	 *<P>
	 * Not persisted in {@link #fillSaveState(Bundle)} or {@link #fillSaveState(SharedPreferences)}.
	 * Instead, current laps must be stored in the database.
	 * @see #lap_elapsed
	 */
	long[] lap_systime = new long[64];
	
	/**
	 * For countdown mode, the initial seconds, minutes, hours,
	 * as set by {@link #reset(int, int, int, int)},
	 * stored as a total number of seconds.
	 * Used by {@link #adjClockOnAppResume(boolean, long)}.
	 */
	private int countdnTotalSeconds = 0;  

	/**
	 * If running, the actual start time, and the adjusted start time after pauses.
	 * (If there are no pauses, they are identical.  Otherwise, the difference
	 * is the amount of paused time.)
	 *<P>
	 * When counting up, the amount of time on the clock
	 * is the current time minus <tt>clockStartTimeAdj</tt>
	 *<P>
	 * Taken from {@link System#currentTimeMillis()}.
	 * @since 1.3
	 */
	private long startTimeActual, startTimeAdj;

	/**
	 * If {@link #wasStarted}, and ! {@link #isStarted}, the
	 * current time when the clock was paused by calling {@link #count()}
	 * (taken from {@link System#currentTimeMillis()}).
	 * Otherwise -1.
	 */
	private long stopTime;

	/**
	 * Time when {@link android.app.Activity#onPause() Activity.onPause()} was called, or <tt>-1L</tt>.
	 * Used by {@link #onAppPause()}, {@link #onAppResume()}.
	 */
	private long appPauseTime;

	/**
	 * Time when {@link #restoreFromSaveState(Bundle)} was called, or <tt>-1L</tt>.
	 * Used by {@link #onAppResume()}, to prevent 2 adjustments after a restore.
	 */
	private long appStateRestoreTime;
	
	
	public Clock(Anstop parent) {
		this.parent = parent;
		lapf = new LapFormatter();

		dsech = new dsechandler();
		sech = new sechandler();
		minh = new minhandler();
		hourh = new hourhandler();

		// these are also set in reset(), along with other state fields
		appPauseTime = -1L;
		appStateRestoreTime = -1L;
		stopTime = -1L;
		startTimeActual = -1L;
		startTimeAdj = -1L;
	}
	
	/**
	 * Save the clock's current state to a bundle.
	 * For use with {@link Activity#onSaveInstanceState(Bundle)}.
	 *<UL>
	 * <LI> clockActive  1 or 0
	 * <LI> clockWasActive  1 or 0
	 * <LI> clockAnstopCurrent  mode (clock.v; was anstop.current before those were combined)
	 * <LI> clockAnstopWroteStart  anstop.wroteStartTime flag: boolean
	 * <LI> clockDigits  if clockActive: array hours, minutes, seconds, dsec
	 * <LI> clockComment   comment text, if any (String)
	 * <LI> clockLapCount  lap count, including current lap (starts at 1, not 0)
	 * <LI> clockLaps  lap text, if any (CharSequence here; String in {@link #fillSaveState(SharedPreferences)})
	 * <LI> clockLapsElapsed  each lap's elapsed time ({@link #lap_elapsed}[]), if {@link #laps} &gt; 1
	 * <LI> clockLapsSystime  each lap's wall-clock time ({@link #lap_systime}[]), if {@link #laps} &gt; 1
	 * <LI> clockStateSaveTime current time when bundle saved, from {@link System#currentTimeMillis()}
	 * <LI> clockStartTimeActual  actual time when clock was started, from {@link System#currentTimeMillis()}
	 * <LI> clockStartTimeAdj  <tt>clockStartTimeActual</tt> adjusted forward to remove any
	 *         time spent paused.  When counting up, the amount of time on the clock
	 *         is the current time minus <tt>clockStartTimeAdj</tt>
	 * <LI> clockStopTime  time when clock was last stopped(paused)
	 * <LI> clockCountHour  In Countdown mode, the starting hour spinner; not set if hourSpinner null
	 * <LI> clockCountMin, clockCountSec  Countdown minutes, seconds; same situation as <tt>clockCountHour</tt>
	 *</UL>
	 * @param outState Bundle to save into
	 * @return true if clock was running, false otherwise
	 * @see #restoreFromSaveState(Bundle)
	 * @see #fillSaveState(SharedPreferences)
	 * @since 1.3
	 */
	public boolean fillSaveState(Bundle outState) {
		final long savedAtTime = System.currentTimeMillis();

		// Reminder: If you add a bundle key,
		// be sure to add it in both copies of
		// fillSaveState and of restoreFromSaveState.

		outState.putInt("clockAnstopCurrent", v);
		outState.putBoolean("clockAnstopWroteStart", parent.wroteStartTime);
		int[] hmsd = new int[]{ hour, min, sec, dsec };
		outState.putIntArray("clockDigits", hmsd);
		outState.putInt("clockActive", isStarted ? 1 : 0);
		outState.putInt("clockWasActive", wasStarted ? 1 : 0);
		outState.putLong("clockStateSaveTime", savedAtTime);
		if (! isStarted)
			outState.putLong("clockStopTime", stopTime);
		else
			outState.putLong("clockStopTime", -1L);
		if (parent.comment != null)
			outState.putString("clockComment", parent.comment);
		else
			outState.putString("clockComment", "");
		outState.putInt("clockLapCount", laps);
		if (parent.lapView != null)
			outState.putCharSequence("clockLaps", parent.laps);
		else
			outState.putCharSequence("clockLaps", "");
		if (laps > 1)
		{
			outState.putLongArray("clockLapsElapsed", lap_elapsed);
			outState.putLongArray("clockLapsSystime", lap_systime);
		}
		outState.putLong("clockStartTimeActual", startTimeActual);
		outState.putLong("clockStartTimeAdj", startTimeAdj);
		if (parent.hourSpinner != null)
		{
			// used by restoreFromSaveStateFields to calc countdnTotalSeconds
			outState.putInt("clockCountHour", parent.hourSpinner.getSelectedItemPosition());
			outState.putInt("clockCountMin", parent.minSpinner.getSelectedItemPosition());
			outState.putInt("clockCountSec", parent.secSpinner.getSelectedItemPosition());
		}

		return isStarted;
	}

	/**
	 * Save the clock's current state to {@link SharedPreferences} fields.
	 * Same contents as {@link #fillSaveState(Bundle)} except that
	 * the key names have "anstop_state_" as a prefix.
	 * Also sets boolean <tt>"anstop_in_use"</tt> to the value returned by {@link #isInUse()}.
	 *
	 * @param outState SharedPreferences to save into
	 * @return true if clock was running, false otherwise
	 * @see #restoreFromSaveState(SharedPreferences)
	 */
	public boolean fillSaveState(SharedPreferences outState) {
		final boolean notInUse = ! isInUse();
		if (notInUse && ! outState.getBoolean("anstop_in_use", false))
		{
			return false;  // <--- Early return: No change to preferences ---
		}

		SharedPreferences.Editor outPref = outState.edit();
		final long savedAtTime = System.currentTimeMillis();

		if (notInUse)
		{
			outPref.putBoolean("anstop_in_use", false);			
		} else {
			outPref.putBoolean("anstop_in_use", true);

			// Reminder: If you add a bundle key,
			// be sure to add it in both copies of
			// fillSaveState and of restoreFromSaveState.

			outPref.putInt("anstop_state_current", v);
			outPref.putBoolean("anstop_state_wroteStart", parent.wroteStartTime);
			outPref.putInt("anstop_state_clockDigits_h", hour);
			outPref.putInt("anstop_state_clockDigits_m", min);
			outPref.putInt("anstop_state_clockDigits_s", sec);
			outPref.putInt("anstop_state_clockDigits_d", dsec);
			outPref.putBoolean("anstop_state_clockActive", isStarted);
			outPref.putBoolean("anstop_state_clockWasActive", wasStarted);
			outPref.putLong("anstop_state_clockStateSaveTime", savedAtTime);
			if (! isStarted)
				outPref.putLong("anstop_state_clockStopTime", stopTime);
			else
				outPref.putLong("anstop_state_clockStopTime", -1L);
			if (parent.comment != null)
				outPref.putString("anstop_state_clockComment", parent.comment);
			else
				outPref.putString("anstop_state_clockComment", "");
			outPref.putInt("anstop_state_clockLapCount", laps);
			if (parent.lapView != null)
				outPref.putString("anstop_state_clockLaps", parent.laps.toString());
			else
				outPref.putString("anstop_state_clockLaps", "");
			outPref.putLong("anstop_state_clockStartTimeActual", startTimeActual);
			outPref.putLong("anstop_state_clockStartTimeAdj", startTimeAdj);
			if (parent.hourSpinner != null)
			{
				// used by restoreFromSaveStateFields to calc countdnTotalSeconds
				outPref.putInt("anstop_state_clockCountHour", parent.hourSpinner.getSelectedItemPosition());
				outPref.putInt("anstop_state_clockCountMin", parent.minSpinner.getSelectedItemPosition());
				outPref.putInt("anstop_state_clockCountSec", parent.secSpinner.getSelectedItemPosition());
			} else {
				outPref.putInt("anstop_state_clockCountHour", 0);
				outPref.putInt("anstop_state_clockCountMin", 0);
				outPref.putInt("anstop_state_clockCountSec", 0);
			}
		}
		outPref.commit();

		return isStarted;
	}

	/**
	 * Record the time when {@link android.app.Activity#onPause() Activity.onPause()} was called.
	 * @see #onAppResume()
	 * @since 1.3
	 */
	public void onAppPause()
	{
		appPauseTime = System.currentTimeMillis();
		if((threadS != null) && threadS.isAlive())
		{
			threadS.interrupt();
			threadS = null;
		}
		if((threadC != null) && threadC.isAlive())
		{
			threadC.interrupt();
			threadC = null;
		}
	}

	/**
	 * Adjust the clock when the app is resumed.
	 * Also adjust the clock-display fields.
	 * @see #onAppPause()
	 * @since 1.3
	 */
	public void onAppResume()
	{
		if (! isStarted)
			return;

		if (appPauseTime > appStateRestoreTime)
			adjClockOnAppResume(false, System.currentTimeMillis());

		if(v == Anstop.STOP_LAP) {
			if((threadS != null) && threadS.isAlive())
				threadS.interrupt();
			threadS = new clockThread();
			threadS.start();
		}
		else {
			// Anstop.COUNTDOWN
			if((threadC != null) && threadC.isAlive())
				threadC.interrupt();
			threadC = new countDownThread();
			threadC.start();
		}
	}

	/**
	 * Restore our state (start time millis, etc) and keep going.
	 *<P>
	 * Must call AFTER the GUI elements (parent.dsecondsView, etc) exist.
	 * Thus you must read <tt>clockAnstopCurrent</tt> from the bundle yourself,
	 * and set the GUI mode accordingly, before calling this method.
	 * Once the GUI is set, call {@link #changeMode(int)} to reset clock fields
	 * and then call this method.
	 *<P>
	 * Will call count() if clockStarted == 1 in the bundle, unless we've counted down to 0:0:0.
	 * For the bundle contents, see {@link #fillSaveState(Bundle)}.
	 * @param inState  bundle containing our state
	 * @return true if clock was running when saved, false otherwise
	 * @see #restoreFromSaveState(SharedPreferences)
	 * @since 1.3
	 */
	public boolean restoreFromSaveState(Bundle inState) {
		long restoredAtTime = System.currentTimeMillis();
		appStateRestoreTime = restoredAtTime;
		if ((inState == null) || ! inState.containsKey("clockActive"))
			return false;

		// set v to ensure consistent state; should be set already
		// by changeMode before this method was called.
		v = inState.getInt("clockAnstopCurrent");

		// read the counting fields
		{
			final int[] hmsd = inState.getIntArray("clockDigits");
			hour = hmsd[0];
			min  = hmsd[1];
			sec  = hmsd[2];
			dsec = hmsd[3];
		}

		final boolean bundleClockActive = (1 == inState.getInt("clockActive"));
		wasStarted = (1 == inState.getInt("clockWasActive"));
		final long savedAtTime = inState.getLong("clockStateSaveTime", restoredAtTime);
		startTimeActual = inState.getLong("clockStartTimeActual", savedAtTime);
		startTimeAdj = inState.getLong("clockStartTimeAdj", startTimeActual);
		stopTime = inState.getLong("clockStopTime", -1L);
		parent.wroteStartTime = inState.getBoolean("clockAnstopWroteStart", false);
		parent.comment = inState.getString("clockComment");
		if ((parent.comment != null) && (parent.comment.length() == 0))
			parent.comment = null;
		laps = inState.getInt("clockLapCount", 1);
		if (parent.lapView != null)
		{
			parent.laps = new StringBuilder();
			CharSequence laptext = inState.getCharSequence("clockLaps");
			if (laptext != null)
				parent.laps.append(laptext);
		}
		if (laps > 1)
		{
			lap_elapsed = inState.getLongArray("clockLapsElapsed");
			lap_systime = inState.getLongArray("clockLapsSystime");
		}

		if ((parent.comment != null) || (parent.lapView != null))
			parent.updateStartTimeCommentLapsView(false);

		if (parent.hourSpinner != null)
		{
			final int
			  h = inState.getInt("clockCountHour"),
			  m = inState.getInt("clockCountMin"),
			  s = inState.getInt("clockCountSec");
			countdnTotalSeconds = ((h * 60) + m) * 60 + s;
			parent.hourSpinner.setSelection(h);
			parent.minSpinner.setSelection(m);
			parent.secSpinner.setSelection(s);
		} else {
			countdnTotalSeconds = 0;
		}

		if((threadS != null) && threadS.isAlive())
			threadS.interrupt();
		if((threadC != null) && threadC.isAlive())
			threadC.interrupt();

		// Adjust and continue the clock thread:
		// re-read current time for most accuracy
		if (bundleClockActive)
		{
			restoredAtTime = System.currentTimeMillis();
			appStateRestoreTime = restoredAtTime;
			adjClockOnAppResume(false, restoredAtTime);
		} else {
			adjClockOnAppResume(true, 0L);
		}

		isStarted = false;  // must be false before calling count()
		if (bundleClockActive)
		{
			// Read the values from text elements we've just set, and start it:
			// In countdown mode, will check if we're past 0:0:0 by now.
			count();
		}
		return isStarted;
	}

	/**
	 * Restore our state (start time millis, etc) and keep going.
	 * Unless the boolean preference <tt>"anstop_in_use"</tt> is true,
	 * nothing will be read, and false will be returned.
	 *<P>
	 * Must call AFTER the GUI elements (parent.dsecondsView, etc) exist.
	 * Thus you must read <tt>"anstop_state_current"</tt> from the preferences yourself,
	 * and set the GUI mode accordingly, before calling this method.
	 * Once the GUI is set, call {@link #changeMode(int)} to reset clock fields
	 * and then call this method.
	 *<P>
	 * Will call count() if <tt>anstop_state_clockStarted</tt> == 1 in the preferences,
	 * unless we've counted down to 0:0:0.
	 * For the preference contents, see {@link #fillSaveState(SharedPreferences)}.
	 *<P>
	 * <b>Reminder:</b> {@link #lap_elapsed} and {@link #lap_systime} aren't restored here,
	 * unlike {@link #restoreFromSaveState(Bundle)}, although the {@link #laps} counter is
	 * restored.  Please restore the lap data from the database after calling this method.
	 *
	 * @param inState  preferences containing our state
	 * @return true if clock was running when saved, false otherwise
	 * @see #restoreFromSaveState(Bundle)
	 */
	public boolean restoreFromSaveState(SharedPreferences inState) {
		long restoredAtTime = System.currentTimeMillis();
		if ((inState == null) || ! inState.getBoolean("anstop_in_use", false))
			return false;

		appStateRestoreTime = restoredAtTime;

		// set v to ensure consistent state; should be set already
		// by changeMode before this method was called.
		v = inState.getInt("anstop_state_current", Anstop.STOP_LAP);

		// read the counting fields
		{
			hour = inState.getInt("anstop_state_clockDigits_h", 0);
			min  = inState.getInt("anstop_state_clockDigits_m", 0);
			sec  = inState.getInt("anstop_state_clockDigits_s", 0);
			dsec = inState.getInt("anstop_state_clockDigits_d", 0);
		}

		final boolean bundleClockActive = inState.getBoolean("anstop_state_clockActive", false);
		wasStarted = inState.getBoolean("anstop_state_clockWasActive", false);
		final long savedAtTime = inState.getLong("anstop_state_clockStateSaveTime", restoredAtTime);
		startTimeActual = inState.getLong("anstop_state_clockStartTimeActual", savedAtTime);
		startTimeAdj = inState.getLong("anstop_state_clockStartTimeAdj", startTimeActual);
		stopTime = inState.getLong("anstop_state_clockStopTime", -1L);
		parent.wroteStartTime = inState.getBoolean("anstop_state_wroteStart", false);
		parent.comment = inState.getString("anstop_state_clockComment", null);
		if ((parent.comment != null) && (parent.comment.length() == 0))
			parent.comment = null;
		laps = inState.getInt("anstop_state_clockLapCount", 1);
		if (parent.lapView != null)
		{
			parent.laps = new StringBuilder();
			String laptext = inState.getString("anstop_state_clockLaps", "");
			if (laptext.length() > 0)
				parent.laps.append(laptext);
		}

		if ((parent.comment != null) || (parent.lapView != null))
			parent.updateStartTimeCommentLapsView(false);

		if (parent.hourSpinner != null)
		{
			final int
			  h = inState.getInt("anstop_state_clockCountHour", 0),
			  m = inState.getInt("anstop_state_clockCountMin", 0),
			  s = inState.getInt("anstop_state_clockCountSec", 0);
			countdnTotalSeconds = ((h * 60) + m) * 60 + s;
			parent.hourSpinner.setSelection(h);
			parent.minSpinner.setSelection(m);
			parent.secSpinner.setSelection(s);
		} else {
			countdnTotalSeconds = 0;
		}

		if((threadS != null) && threadS.isAlive())
			threadS.interrupt();
		if((threadC != null) && threadC.isAlive())
			threadC.interrupt();

		// Adjust and continue the clock thread:
		// re-read current time for most accuracy
		if (bundleClockActive)
		{
			restoredAtTime = System.currentTimeMillis();
			appStateRestoreTime = restoredAtTime;
			adjClockOnAppResume(false, restoredAtTime);
		} else {
			adjClockOnAppResume(true, 0L);
		}

		isStarted = false;  // must be false before calling count()
		if (bundleClockActive)
		{
			// Read the values from text elements we've just set, and start it:
			// In countdown mode, will check if we're past 0:0:0 by now.
			count();
		}
		return isStarted;
	}

	/**
	 * Adjust the clock fields ({@link #hour}, {@link #min}, etc)
	 * and the display fields ({@link Anstop#hourView}, etc)
	 * based on the application being paused for a period of time.
	 *<P>
	 * If <tt>adjDisplayOnly</tt> is false, do not call unless {@link #isStarted}.
	 *<P>
	 * Used with {@link #onAppResume()} and {@link #restoreFromSaveState(Bundle)}.
	 *
	 * @param adjDisplayOnly  If true, update the display fields based on
	 *    the current hour, min, sec, dsec internal field values,
	 *    instead of adjusting those internal values.
	 *    <tt>savedAtTime</tt>, <tt>resumedAtTime</tt> are ignored.
	 * @param resumedAtTime  Time when the app was resumed, from {@link System#currentTimeMillis()}
	 */
	private void adjClockOnAppResume
	    (final boolean adjDisplayOnly, final long resumedAtTime)
	{
		if (! adjDisplayOnly)
		{
			long ttotal;

			// based on our mode, adjust dsec, sec, min, hour:
			switch (v)
			{
			case Anstop.STOP_LAP:
				ttotal = resumedAtTime - startTimeAdj;
				break;
	
			default:  // Anstop.COUNTDOWN
				ttotal = (countdnTotalSeconds * 1000L)
				    - (resumedAtTime - startTimeAdj);
				if (ttotal < 0)
					ttotal = 0;  // don't go past end of countdown
			}

			dsec = ((int) (ttotal % 1000L)) / 100;
			ttotal /= 1000L;
			sec = (int) (ttotal % 60L);
			ttotal /= 60L;
			min = (int) (ttotal % 60L);
			ttotal /= 60L;
			hour = (int) ttotal;
		}

		if (parent.dsecondsView != null)
			parent.dsecondsView.setText(Integer.toString(dsec));
		if (parent.secondsView != null)
			parent.secondsView.setText(lapf.nf.format(sec));
		if (parent.minView != null)
			parent.minView.setText(lapf.nf.format(min));
		if (parent.hourView != null)
			parent.hourView.setText(Integer.toString(hour));
	}

	/**
	 * Get the current value of this timer.
	 * @return a stringbuffer of the form "#h mm:ss:d"
	 * @since 1.3
	 * @see #getCurrentValueMillis()
	 */
	public StringBuffer getCurrentValue()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(hour);
		sb.append("h ");
		sb.append(lapf.nf.format(min));
		sb.append(':');
		sb.append(lapf.nf.format(sec));
		sb.append(':');
		sb.append(dsec);
		return sb;
	}

	/**
	 * Get the current value of this timer, in milliseconds.
	 * @param sb  Optional StringBuffer, or null;
	 *    current value in the format "#h mm:ss:d" will be appended to sb
	 *    depending on {@link #lapFormatFlags}.
	 *    if {@link #lapFormatFlags} includes {@link #LAP_FMT_FLAG_DELTA},
	 *    and {@link #laps} &gt; 1, then {@link #lap_elapsed}[{@link #laps} - 2]
	 *    must be accurate to calculate the delta.
	 * @param withLap  If true, sb will have the lap number too;
	 *    sb's appended format will be "lap. #h mm:ss:d"
	 * @return the number of milliseconds representing the timer's
	 *    current hours, minutes, seconds, and dsec
	 * @since 1.5
	 * @see #getCurrentValue()
	 */
	public long getCurrentValueMillis(StringBuilder sb, final boolean withLap)
	{
		// copy fields first, in case they're about to increment in the other thread
		final int ds = dsec, s = sec, m = min, h = hour;
		final long elapsedMillis =
			(((h * 60 + m) * 60 + s)
			 * 10 + ds) * 100L;

		lapf.formatTimeLap
			(sb, withLap, h, m, s, ds,
			 laps, elapsedMillis, System.currentTimeMillis(),
			 lap_elapsed);
		return elapsedMillis;
	}

	/**
	 * Write all laps into <tt>sb</tt> using the current format flags.
	 * If {@link #laps} is 1, do nothing.
	 * @param sb  StringBuffer to write into; not null
	 */
	public void formatTimeAllLaps(StringBuilder sb)
		throws IllegalArgumentException
	{
		lapf.formatTimeAllLaps(sb, laps, lap_elapsed, lap_systime);
	}

	/**
	 * Get the actual start time.
	 * @return Start time, of the form used by {@link System#currentTimeMillis()},
	 *   or -1L if never started.
	 */
	public long getStartTimeActual() { return startTimeActual; }

	/**
	 * Get the actual stop time, if any.
	 * @return  If {@link #wasStarted}, and ! {@link #isStarted}, the
	 * current time when the clock was paused by calling {@link #count()}
	 * (taken from {@link System#currentTimeMillis()}).
	 * Otherwise -1.
	 */
	public long getStopTime() { return stopTime; }

	/**
	 * Is the clock active or in use?
	 * @return true if <tt>isStarted</tt> or if hours, minutes, seconds or dsec are not 0.
	 */
	public boolean isInUse() { return isStarted || (hour > 0) || (min > 0) || (sec > 0) || (dsec > 0); }

	/**
	 * Get the clock's current counting mode.
	 * @return  the mode; {@link Anstop#STOP_LAP} or {@link Anstop#COUNTDOWN}
	 * @see #changeMode(int)
	 * @see #reset(int, int, int, int)
	 */
	public int getMode() { return v; }

	/**
	 * Set the lap format flags.
	 * @param newFormatFlags  Collection of flags, such as {@link #LAP_FMT_FLAG_DELTA}; not 0
	 * @param formatForSysTime Short time format in case {@link #LAP_FMT_FLAG_SYSTIME} is used; not null.
	 *    Value should be <tt>android.text.format.DateFormat.getTimeFormat(getApplicationContext())</tt>.
	 *    Note that <tt>getTimeFormat</tt> gives hours and minutes, it has no standard way to include the seconds;
	 *    if more precision is needed, the user can get it from elapsed or delta seconds.
	 * @throws IllegalArgumentException if flags &lt;= 0, or <tt>formatForSysTime == null</tt> 
	 */
	public void setLapFormat(final int newFormatFlags, final DateFormat formatForSysTime)
		throws IllegalArgumentException
	{
		lapf.setLapFormat(newFormatFlags, formatForSysTime);
	}

	/**
	 * Take a lap now.  Optionally append the current lap time to a buffer.
	 * @param sb  Null or a buffer to which the lap info
	 *    will be appended, in the format "lap. #h mm:ss:d"
	 *    depending on {@link #lapFormatFlags}.
	 * @return the lap number; the first lap number is 1.
	 * @since 1.5
	 */
	public int lap(StringBuilder sb)
	{
		final long lapNow = System.currentTimeMillis(),
		           lapElapsed = getCurrentValueMillis(sb, true);  // appends sb
		final int lapnum = laps++;
		final int i = lapnum - 1;
		if (i >= lap_systime.length)
		{
			// copy the lap arrays
			final int L = lap_systime.length,
			          Lnew = L + 64;
			long[] systi = new long[Lnew],
			       elaps = new long[Lnew];
			System.arraycopy(lap_systime, 0, systi, 0, L);
			System.arraycopy(lap_elapsed, 0, elaps, 0, L);
			lap_systime = systi;
			lap_elapsed = elaps;
		}
		lap_systime[i] = lapNow;
		lap_elapsed[i] = lapElapsed;

		return lapnum;
	}

	/**
	 * Reset the clock while stopped, and maybe change modes.  {@link #isStarted} must be false.
	 * If <tt>newMode</tt> is {@link Anstop#STOP_LAP}, the clock will be reset to 0,
	 * and <tt>h</tt>, <tt>m</tt>, <tt>s</tt> are ignored.
	 *
	 * @param newMode  new mode to set, or -1 to leave as is
	 * @param h  for countdown mode, hour to reset the clock to
	 * @param m  minute to reset the clock to
	 * @param s  second to reset the clock to
	 * @return true if was reset, false if was not reset because {@link #isStarted} is true.
	 * @see #changeMode(int)
	 */
	public boolean reset(final int newMode, final int h, final int m, final int s)
	{
		if (isStarted)
			return false;

		if (newMode != -1)
			v = newMode;

		wasStarted = false;
		appPauseTime = -1L;
		appStateRestoreTime = -1L;
		stopTime = -1L;
		startTimeActual = -1L;
		startTimeAdj = -1L;

		laps = 1;
		if (v == Anstop.STOP_LAP)
		{
			hour = 0;
			min = 0;
			sec = 0;
		} else {  // COUNTDOWN
			hour = h;
			min = m;
			sec = s;
			countdnTotalSeconds = ((h * 60) + m) * 60 + s;
		}
		dsec = 0;

		return true;
	}

	/**
	 * Change the current mode.
	 *<P>
	 * If the current mode is already <tt>newMode</tt>, change it anyway;
	 * calls <tt>reset</tt> to update all fields.
	 * @see #reset(int, int, int, int)
	 * @param newMode  The new mode; {@link Anstop#STOP_LAP} or {@link Anstop#COUNTDOWN}
	 */
	public void changeMode(final int newMode)
	{
		reset(newMode, 0, 0, 0);
	}

	/**
	 * Start or stop(pause) counting.
	 *<P>
	 * For <tt>COUNTDOWN</tt> mode, you must first call {@link #reset(int, int, int, int)}
	 * or set the {@link #hour}, {@link #min}, {@link #sec} fields.
	 *<P>
	 * If <tt>wasStarted</tt>, and if <tt>stopTime</tt> != -1,
	 * will update <tt>startTimeAdj</tt>
	 * (based on current time and <tt>stopTime</tt>)
	 * before starting the counting thread.
	 * This assumes that the counting was recently paused by the
	 * user, and is starting up again.
	 */
	public void count() {
		final long now = System.currentTimeMillis();

		if(!isStarted) {

			if (! wasStarted)
			{
				startTimeActual = now;
				startTimeAdj = startTimeActual;
			}
			else if (stopTime != -1L)
			{
				startTimeAdj += (now - stopTime);
			}

			isStarted = true;
			wasStarted = true;
			if(v == Anstop.STOP_LAP) {
				if((threadS != null) && threadS.isAlive())
					threadS.interrupt();
				threadS = new clockThread();
				threadS.start();
			}
				
			else {
				// Anstop.COUNTDOWN
				if((threadC != null) && threadC.isAlive())
					threadC.interrupt();
				if ((dsec > 0) || (sec > 0) || (min > 0) || (hour > 0))
				{
					threadC = new countDownThread();
					threadC.start();
				} else {
					isStarted = false;
				}
			}
			
			
		}
		else {
			isStarted = false;
			stopTime = now;
			
			if(v == Anstop.STOP_LAP) {
				if(threadS.isAlive())
					threadS.interrupt();
			}
				
			else {
				// Anstop.COUNTDOWN
				if(threadC.isAlive())
					threadC.interrupt();
			}
		}
			
	}
	
	/**
	 * Lap mode, count up from 0.
	 * @see countDownThread
	 */
	private class clockThread extends Thread {		
		public clockThread() { setDaemon(true); }

		@Override
		public void run() {
			
			while(true) {
				dsec++;
				
				if(dsec == 10) {
					sec++;
					dsec = 0;
					sech.sendEmptyMessage(MAX_PRIORITY);
				
					if(sec == 60) {
						min++;
						sec = 0;
						minh.sendEmptyMessage(MAX_PRIORITY);
						
						
						if(min == 60) {
							hour++;
							min = 0;
							hourh.sendEmptyMessage(MAX_PRIORITY);
							minh.sendEmptyMessage(MAX_PRIORITY);
						}
					}
				}
				
				
				dsech.sendEmptyMessage(MAX_PRIORITY);
				
				try {
					sleep(100);
				}
				catch ( InterruptedException e) {
					return;
				}
			}
			
			
		}
		
	}
	
	/**
	 * Countdown mode, count down to 0.
	 * Before starting this thread, set non-zero h:m:s.d by calling {@link Clock#reset(int, int, int, int)}.
	 * Otherwise the thread immediately stops at 00:00:00.0.
	 * @see clockThread
	 */
	private class countDownThread extends Thread {
		public countDownThread() { setDaemon(true); }

		@Override
		public void run() {
			
			
			if(hour == 0 && min == 0 && sec == 0 && dsec == 0) {
				isStarted = false;
				if (parent.modeMenuItem != null)
				{
					parent.modeMenuItem.setEnabled(true);
					parent.saveMenuItem.setEnabled(true);
				}
				return;
			}
			
			while(true) {
				

				
				if(dsec == 0) {
						
					
					if(sec == 0) {
						if(min == 0) {
							if(hour != 0) {
								hour--;
								min = 60;
								hourh.sendEmptyMessage(MAX_PRIORITY);
								minh.sendEmptyMessage(MAX_PRIORITY);
								
							}
						}
						
						if(min != 0) {
							min--;
							sec = 60;
							minh.sendEmptyMessage(MAX_PRIORITY);
						}
						
					}					
					
					if(sec != 0) {
						sec--;
						dsec = 10;
						sech.sendEmptyMessage(MAX_PRIORITY);
					}
										
				}
				dsec--;
				
				
				
				dsech.sendEmptyMessage(MAX_PRIORITY);
				
				try {
					sleep(100);
				}
				catch ( InterruptedException e) {
					return;
				}
				
				
				if(hour == 0 && min == 0 && sec == 0 && dsec == 0) {
					isStarted = false;
					if (parent.modeMenuItem != null)
					{
						parent.modeMenuItem.setEnabled(true);
						parent.saveMenuItem.setEnabled(true);
					}
					return;
				}
					
			}
			
			
		}
		
	}
	
	private class dsechandler extends Handler {
		@Override
		public void handleMessage (Message msg) {
			parent.dsecondsView.setText("" + dsec);
		}
	}
	
	private class sechandler extends Handler {
		@Override
		public void handleMessage (Message msg) {
			parent.secondsView.setText("" + lapf.nf.format(sec));
		}
	}
	
	private class minhandler extends Handler {
		@Override
		public void handleMessage (Message msg) {
			parent.minView.setText("" + lapf.nf.format(min));
		}
	}
	
	private class hourhandler extends Handler {
		@Override
		public void handleMessage (Message msg) {
			parent.hourView.setText("" + hour);
		}
	}
	
	/**
	 * Flags, settings, and methods to format laps.
	 * Each {@link Clock} has one <tt>LapFormatter</tt>.
	 * Used by {@link Clock#lap(StringBuilder)}
	 * and by {@link AnstopDbAdapter#getRowAndFormat(long)}.
	 * The currently active flags are {@link #lapFormatFlags}.
	 * By default, the formatter flags are {@link Clock#LAP_FMT_FLAG_ELAPSED} only.
	 */
	public static class LapFormatter {

		/**
		 * Any lap format flags, such as {@link Clock#LAP_FMT_FLAG_SYSTIME}, currently
		 * active; the default is {@link Clock#LAP_FMT_FLAG_ELAPSED} only.
		 * Read-only from {@link Anstop} class.
		 * To change, call {@link #setLapFormat(int, DateFormat)}.
		 *<P>
		 * <b>Note:</b> Currently, code and settings.xml both assume that
		 * the default format has LAP_FMT_FLAG_ELAPSED and no others,
		 * unless the user changes that preference.
		 */
		public int lapFormatFlags = LAP_FMT_FLAG_ELAPSED;

		/**
		 * Time-of-day format used in {@link Clock#getCurrentValueMillis(StringBuffer, boolean)}
		 * for lap format, when {@link Clock#LAP_FMT_FLAG_SYSTIME} is used.
		 *<P>
		 * This is null initially; {@link Clock#LAP_FMT_FLAG_ELAPSED} doesn't need it.
		 * If {@link #setLapFormat(int, DateFormat)} changes {@link #lapFormatFlags} to
		 * anything else, it's a required parameter, so it would be non-null when needed.
		 */
		private java.text.DateFormat lapFormatTimeOfDay;

		/**
		 * 2-digit number format for minutes and seconds;
		 * <tt>nf.{@link NumberFormat#format(long) format}(3)</tt> gives "03".
		 */
		public NumberFormat nf;

		/**
		 * Create a LapFormatter with the default flags.
		 * ({@link Clock#LAP_FMT_FLAG_ELAPSED} only)
		 */
		public LapFormatter() {
			nf = NumberFormat.getInstance();
			nf.setMinimumIntegerDigits(2);  // The minimum Digits required is 2
			nf.setMaximumIntegerDigits(2); // The maximum Digits required is 2			
		}

		/**
		 * Set the lap format flags.
		 * @param newFormatFlags  Collection of flags, such as {@link Clock#LAP_FMT_FLAG_DELTA}; not 0
		 * @param formatForSysTime Short time format in case {@link Clock#LAP_FMT_FLAG_SYSTIME} is used; not null.
		 *    Value should be <tt>android.text.format.DateFormat.getTimeFormat(getApplicationContext())</tt>.
		 *    Note that <tt>getTimeFormat</tt> gives hours and minutes, it has no standard way to include the seconds;
		 *    if more precision is needed, the user can get it from elapsed or delta seconds.
		 * @throws IllegalArgumentException if flags &lt;= 0, or <tt>formatForSysTime == null</tt> 
		 */
		public void setLapFormat(final int newFormatFlags, final DateFormat formatForSysTime)
			throws IllegalArgumentException
		{
			if ((newFormatFlags <= 0) || (formatForSysTime == null))
				throw new IllegalArgumentException();
			lapFormatFlags = newFormatFlags;
			lapFormatTimeOfDay = formatForSysTime;
		}

		/**
		 * Format one lap's time, appending it to a buffer.
		 * @param sb   Buffer to append to; if null, do nothing
		 * @param withLap  If true, append <tt>lapNum</tt>
		 * @param h        Elapsed hours if known, or -1 to calculate from <tt>elapsedMillis</tt>;
		 *                 used with flag {@link Clock#LAP_FMT_FLAG_ELAPSED}
		 * @param m   Elapsed minutes if known; if <tt>h</tt> is -1, will calculate from <tt>elapsedMillis</tt>
		 * @param s   Elapsed seconds if known; if <tt>h</tt> is -1, will calculate from <tt>elapsedMillis</tt>
		 * @param ds  Elapsed deciseconds if known; if <tt>h</tt> is -1, will calculate from <tt>elapsedMillis</tt>
		 * @param lapNum   Lap number
		 * @param elapsedMillis  This lap's milliseconds representing h, m, s, ds
		 * @param systimeMillis  This lap's system time; {@link System#currentTimeMillis()}
		 * @param lap_elapsed  Array of previous laps' elapsed times;
		 *    used with flag {@link Clock#LAP_FMT_FLAG_DELTA}
		 */
		public void formatTimeLap(StringBuilder sb, final boolean withLap,
			int h, int m, int s, int ds,
			final int lapNum, final long elapsedMillis, final long systimeMillis,
			final long[] lap_elapsed)
		{
			if (sb == null)
				return;

			boolean sbNeedsSpace = false;  // true if appended anything that needs a space afterwards

			if (withLap)
			{
				sb.append(lapNum);
				sb.append(". ");
			}
			if (0 != (lapFormatFlags & LAP_FMT_FLAG_ELAPSED))
			{
				if (h == -1)
				{
					long elapsed = elapsedMillis / 100L;
					ds = (int) (elapsed % 10);
					elapsed /= 10L;
					s = (int) (elapsed % 60);
					elapsed /= 60L;
					m = (int) (elapsed % 60);
					elapsed /= 60L;
					h = (int) elapsed;
				}
				sb.append(h);
				sb.append("h ");
				sb.append(nf.format(m));
				sb.append(':');
				sb.append(nf.format(s));
				sb.append(':');
				sb.append(ds);
				sbNeedsSpace = true;
			}

			if (0 != (lapFormatFlags & LAP_FMT_FLAG_DELTA))
			{
				final long prevLap = (lapNum > 1) ? lap_elapsed[lapNum - 2] : 0;
				long lapDelta = (elapsedMillis - prevLap) / 100;  // dsec, not msec
				final int dds = (int) (lapDelta % 10);
				lapDelta /= 10;
				final int dsec = (int) (lapDelta % 60);
				lapDelta /= 60;
				final int dm = (int) (lapDelta % 60);
				lapDelta /= 60;

				if (sbNeedsSpace)
					sb.append(' ');
				sb.append("(+");
				sb.append(lapDelta);
				sb.append("h ");
				sb.append(nf.format(dm));
				sb.append(':');
				sb.append(nf.format(dsec));
				sb.append(':');
				sb.append(dds);
				sb.append(')');
				sbNeedsSpace = true;
			}

			if ((0 != (lapFormatFlags & LAP_FMT_FLAG_SYSTIME))
				&& (lapFormatTimeOfDay != null))
			{
				if (sbNeedsSpace)
					sb.append(' ');
				sb.append('@');
				sb.append(lapFormatTimeOfDay.format(systimeMillis));
			}
		}

		/**
		 * Write all laps into <tt>sb</tt> using the current format flags.
		 * If <tt>laps</tt> is 1, do nothing.
		 * @param sb  StringBuffer to write into; not null
		 * @param laps  Lap count + 1, same as {@link Clock#laps} field
		 * @param lap_elapsed  Elapsed times, same format as {@link Clock#lap_elapsed}
		 * @param lap_systime  System times, same format as {@link Clock#lap_systime}
		 * @throws IllegalArgumentException if <tt>sb</tt> null
		 */
		public void formatTimeAllLaps
			(StringBuilder sb, final int laps, final long[] lap_elapsed, final long[] lap_systime)
			throws IllegalArgumentException
		{
			if (sb == null)
				throw new IllegalArgumentException();

			for (int i = 1; i < laps; ++i)
			{
				if (i > 1)
					sb.append('\n');
				formatTimeLap(sb, true, -1, 0, 0, 0, i,
					lap_elapsed[i-1], lap_systime[i-1], lap_elapsed);
			}
		}

	}
	
}
