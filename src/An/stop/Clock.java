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


import java.text.NumberFormat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

/**
 * Timer object and thread.
 *<P>
 * Has two modes (STOP and COUNTDOWN); its mode field is {@link #v}.
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
 * Keeping track of laps is done in {@link Anstop}, not in this object.
 */
public class Clock {

	/**
	 * Counting mode. Two possibilities:
	 *<UL>
	 *<LI> {@link Anstop#STOP_LAP} (0), counting up from 0
	 *<LI> {@link Anstop#COUNTDOWN} (1), counting down from a time set by the user
	 *</UL>
	 */
	private int v;

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
	 */
	int laps = 1;

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
	
	public NumberFormat nf;
	
	
	public Clock(Anstop parent) {
		this.parent = parent;
		nf = NumberFormat.getInstance();
		
		nf.setMinimumIntegerDigits(2);  // The minimum Digits required is 2
		nf.setMaximumIntegerDigits(2); // The maximum Digits required is 2


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
	 * <LI> clockV    mode (clock.v)
	 * <LI> clockAnstopCurrent  mode (anstop.current)
	 * <LI> clockAnstopWroteStart  anstop.wroteStartTime flag: boolean
	 * <LI> clockDigits  if clockActive: array hours, minutes, seconds, dsec
	 * <LI> clockLapCount  lap count, including current lap (starts at 1, not 0)
	 * <LI> clockLaps  lap text, if any (CharSequence)
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

		outState.putInt("clockV", v);
		outState.putInt("clockAnstopCurrent", parent.getCurrentMode());
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
		outState.putInt("clockLapCount", laps);
		if (parent.lapView != null)
			outState.putCharSequence("clockLaps", parent.lapView.getText());
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

			outPref.putInt("anstop_state_clockV", v);
			outPref.putInt("anstop_state_current", parent.getCurrentMode());
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
			outPref.putInt("anstop_state_clockLapCount", laps);
			if (parent.lapView != null)
				outPref.putString("anstop_state_clockLaps", parent.lapView.getText().toString());
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

		v = inState.getInt("clockV");

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
		laps = inState.getInt("clockLapCount", 1);
		if (parent.lapView != null)
		{
			CharSequence laptext = inState.getCharSequence("clockLaps");
			if (laptext != null)
				parent.lapView.setText(laptext);
			else
				parent.lapView.setText("");
		}
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
	 *<P>
	 * Will call count() if <tt>anstop_state_clockStarted</tt> == 1 in the preferences,
	 * unless we've counted down to 0:0:0.
	 * For the preference contents, see {@link #fillSaveState(SharedPreferences)}.
	 * @param inState  preferences containing our state
	 * @return true if clock was running when saved, false otherwise
	 * @see #restoreFromSaveState(Bundle)
	 */
	public boolean restoreFromSaveState(SharedPreferences inState) {
		long restoredAtTime = System.currentTimeMillis();
		if ((inState == null) || ! inState.getBoolean("anstop_in_use", false))
			return false;

		appStateRestoreTime = restoredAtTime;
		v = inState.getInt("anstop_state_clockV", Anstop.STOP_LAP);

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
		laps = inState.getInt("anstop_state_clockLapCount", 1);
		if (parent.lapView != null)
		{
			String laptext = inState.getString("anstop_state_clockLaps", "");
			if (laptext.length() > 0)
				parent.lapView.setText(laptext);
			else
				parent.lapView.setText("");
		}
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
			parent.secondsView.setText(nf.format(sec));
		if (parent.minView != null)
			parent.minView.setText(nf.format(min));
		if (parent.hourView != null)
			parent.hourView.setText(Integer.toString(hour));
	}

	/**
	 * Get the current value of this timer.
	 * @return a stringbuffer of the form "#h mm:ss:d"
	 * @since 1.xx
	 */
	public StringBuffer getCurrentValue()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(hour);
		sb.append("h ");
		sb.append(nf.format(min));
		sb.append(':');
		sb.append(nf.format(sec));
		sb.append(':');
		sb.append(dsec);
		return sb;
	}

	/**
	 * Get the actual start time.
	 * @return Start time, of the form used by {@link System#currentTimeMillis()}.
	 */
	public long getStartTimeActual() { return startTimeActual; }

	/**
	 * Is the clock active or in use?
	 * @return true if <tt>isStarted</tt> or if hours, minutes, seconds or dsec are not 0.
	 */
	public boolean isInUse() { return isStarted || (hour > 0) || (min > 0) || (sec > 0) || (dsec > 0); }

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
			parent.secondsView.setText("" + nf.format(sec));
		}
	}
	
	private class minhandler extends Handler {
		@Override
		public void handleMessage (Message msg) {
			parent.minView.setText("" + nf.format(min));
		}
	}
	
	private class hourhandler extends Handler {
		@Override
		public void handleMessage (Message msg) {
			parent.hourView.setText("" + hour);
		}
	}
	
		
	
}
