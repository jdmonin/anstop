/***************************************************************************
 *   Copyright (C) 2009-2012 by mj <fakeacc.mj@gmail.com>, 				   *
 *   							Jeremy Monin <jeremy@nand.net>             *
 *                                                          			   *
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


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Timer object and thread.
 *<P>
 * Has two modes ({@link #MODE_STOPWATCH} and {@link #MODE_COUNTDOWN}); clock's mode field is {@link #mode}.
 * Access to the fields via {@link #getValues()}
 * The Handler {@link #callback} will be notified about changes.
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
 * You can examine the current state by reading {@link #isActive} and {@link #wasStarted}.
 * To reset the clock again, and/or change the mode, call {@link #reset()} or 
 * {@link #setCountdown(int, int, int)}.
 *<P>
 * When running, a thread ({@link #clockThread}) either counts up ({@link #stopwatchRunnable})
 * or down ({@link #countdownRunnable}), firing every 100ms. The {@link #callback} handler will be
 * notified. See {@link #Clock(int, Handler)} for details about how.
 *<P>
 * To lap, call {@link #lap(StringBuffer)}.  Note that persisting the lap data arrays
 * at Activity.onStop must be done in {@link AnstopActivity}, not here.
 * {@link #fillSaveState(Bundle)} stores the lap data arrays, but there's no corresponding
 * method to save long arrays to {@link SharedPreferences}.
 *<P>
 * Lap display formatting is done through flags such as {@link #LAP_FMT_FLAG_DELTA}
 * and the nested class {@link Clock.LapFormatter}.
 */
public class Clock {

	private Runnable stopwatchRunnable = new Runnable() {
		
		public void run() {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
			Message message;
			
			while(!clockThread.isInterrupted()) {
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					break;
				}
				
				deciSeconds++;
				
				if(deciSeconds == 9) {
					deciSeconds = 0;
					seconds++;
					
					if(seconds == 60) {
						seconds = 0;
						minutes++;
						
						if(minutes == 60) {
							minutes = 0;
							hours++;
							
							message = Message.obtain();
							message.arg1 = UPDATE_HOURS;
							message.arg2 = hours;
							callback.sendMessage(message);
						}
						
						message = Message.obtain();
						message.arg1 = UPDATE_MINUTES;
						message.arg2 = minutes;
						callback.sendMessage(message);
					}
					
					message = Message.obtain();
					message.arg1 = UPDATE_SECONDS;
					message.arg2 = seconds;
					callback.sendMessage(message);
				}
				
				message = Message.obtain();
				message.arg1 = UPDATE_DECI_SECONDS;
				message.arg2 = deciSeconds;
				callback.sendMessage(message);
			}
			
			Log.d(TAG, "returning from stopwatch thread");
		}
		
	};
	
	private Runnable countdownRunnable = new Runnable() {

		public void run() {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
			Message message;
			
			while(!clockThread.isInterrupted()) {
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					break;
				}
				
				
				if(deciSeconds == 0) {
					deciSeconds = 9;
					
					if(seconds == 0) {
						if(minutes == 0) {
							if(hours == 0) {
								// we are finished
								break;
							} else {
								hours--;
								minutes = 59;
								seconds = 59;
								
								message = Message.obtain();
								message.arg1 = UPDATE_HOURS;
								message.arg2 = hours;
								callback.sendMessage(message);
								
								message = Message.obtain();
								message.arg1 = UPDATE_MINUTES;
								message.arg2 = minutes;
								callback.sendMessage(message);
								
								message = Message.obtain();
								message.arg1 = UPDATE_SECONDS;
								message.arg2 = seconds;
								callback.sendMessage(message);
							}
						} else {
							minutes--;
							seconds = 59;
							
							message = Message.obtain();
							message.arg1 = UPDATE_MINUTES;
							message.arg2 = minutes;
							callback.sendMessage(message);
							
							message = Message.obtain();
							message.arg1 = UPDATE_SECONDS;
							message.arg2 = seconds;
							callback.sendMessage(message);
						}
					} else {
						seconds--;
						
						message = Message.obtain();
						message.arg1 = UPDATE_SECONDS;
						message.arg2 = seconds;
						callback.sendMessage(message);
						
						message = Message.obtain();
						message.arg1 = UPDATE_DECI_SECONDS;
						message.arg2 = deciSeconds;
						callback.sendMessage(message);
					}
					
				} else {
					deciSeconds--;
					message = Message.obtain();
					message.arg1 = UPDATE_DECI_SECONDS;
					message.arg2 = deciSeconds;
					callback.sendMessage(message);
				}
			}
			
			Log.d(TAG, "returning from countdown thread");
		}
		
	};
	
	private static final String TAG = "Clock";
	
	public static final int MODE_STOPWATCH = 0;
	public static final int MODE_COUNTDOWN = 1;
	
	public static final int UPDATE_DECI_SECONDS = 0;
	public static final int UPDATE_SECONDS = 1;
	public static final int UPDATE_MINUTES = 2;
	public static final int UPDATE_HOURS = 3;
	
	private static final String HOURS = "hours";
	private static final String MINUTES = "minutes";
	private static final String SECONDS = "seconds";
	private static final String DECI_SECONDS = "deci_seconds";
	private static final String ACTIVE = "is_active";
	private static final String PAUSED = "paused_at";
	
	private String clockName;
	
	private int mode;
	private Handler callback;
	
	private Thread clockThread;
	private int hours;
	private int minutes;
	private int seconds;
	private int deciSeconds;
	
	
	/**
	 * Constructs a new Clock Object. The mode indicates how the clock 
	 * should count. If there is an update, the background Thread will
	 * send a Message to the handler. The first Argument of the Message
	 * <code>(msg.arg1)</code> will be what should be updated (eg. 
	 * {@link #UPDATE_DECI_SECONDS}), the second to which value.
	 * @param mode {@link #MODE_STOPWATCH} or {@link #MODE_COUNTDOWN}
	 * @param callback Handler to update UI
	 * @see #UPDATE_DECI_SECONDS
	 * @see #UPDATE_SECONDS
	 * @see #UPDATE_MINUTES
	 * @see #UPDATE_HOURS
	 */
	public Clock(int mode, Handler callback) {
		this.mode = mode;
		this.callback = callback;
		
		clockName = "clock_" + mode;
		
		if(mode != MODE_STOPWATCH && mode != MODE_COUNTDOWN)
			throw new IllegalArgumentException("mode has illegal value!");
	}
	
	/**
	 * Starts counting in a background thread
	 */
	public void count() {
		if(isActive()) return;
		
		switch(mode) {
		case MODE_STOPWATCH:
			clockThread = new Thread(stopwatchRunnable);
			break;
		case MODE_COUNTDOWN:
			clockThread = new Thread(countdownRunnable);
			break;
		}
		
		clockThread.start();
	}
	
	/**
	 * Interrupts the counting Thread.
	 */
	public void stop() {
		if(isActive())
			clockThread.interrupt();
	}
	
	/**
	 * Indicates if the Clock is currently counting and a Background Thread
	 * is running.
	 * @return true if active
	 */
	public boolean isActive() {
		return clockThread != null && clockThread.isAlive();
	}
	
	/**
	 * Resets hours, minutes, seconds, and deci seconds to zero.
	 * Only resets, if not running (ie. {@link #isActive()} returns 
	 * <code>false</code>)
	 */
	public void reset() {
		if(isActive()) return;
		
		hours = minutes = seconds = deciSeconds = 0;
	}
	
	/**
	 * Sets the values from which should be counted down.
	 * @param hours
	 * @param minutes
	 * @param seconds
	 */
	public void setCountdown(int hours, int minutes, int seconds) {
		if(mode != MODE_COUNTDOWN)
			throw new IllegalArgumentException("mode is not set to countdown!");
		
		this.hours = hours;
		this.minutes = minutes;
		this.seconds = seconds;
		deciSeconds = 0;
	}
	
	/**
	 * Returns the current time values. The first value are the hours,
	 * the second the minutes, third the seconds and the last the deci
	 * seconds.
	 * <p>
	 * That means, for example you can get the current seconds with 
	 * <code>getValues()[2]</code>
	 * <p>
	 * Attention: Not thread safe!
	 * @return array representing the current values.
	 */
	public int[] getValues() {
		return new int[] { hours, minutes, seconds, deciSeconds };
	}

	/**
	 * Saves the state to the Preferences. Stops the clock.
	 */
	public void saveState(Context context) {
		SharedPreferences.Editor editor = context.getSharedPreferences(clockName, Activity.MODE_PRIVATE).edit();
		
		editor.putBoolean(ACTIVE, isActive());

		// stop the clock now
		stop();
		editor.putLong(PAUSED, System.currentTimeMillis());
		
		editor.putInt(DECI_SECONDS, deciSeconds);
		editor.putInt(SECONDS, seconds);
		editor.putInt(MINUTES, minutes);
		editor.putInt(HOURS, hours);
		
		if(!editor.commit())
			Log.e(TAG, "could not save state!");
	}
	
	/**
	 * Restores the state from the Preferences. Starts the clock if was active.
	 */
	public void restoreState(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(clockName, Activity.MODE_PRIVATE);
		
		boolean wasActive = prefs.getBoolean(ACTIVE, false);
		long pausedTime = prefs.getLong(PAUSED, 0);
		
		if(pausedTime == 0) {
			Log.i(TAG, "paused time = 0");
			return;
		}
		
		long diffTime = System.currentTimeMillis() - pausedTime;
		
		deciSeconds = (int) (diffTime / 100) % 10;
		seconds = (int) (diffTime / 1000) % 60 ;
		minutes = (int) ((diffTime / (1000 * 60)) % 60);
		hours   = (int) (diffTime / (1000 * 60 * 60));
		
		
		if(!wasActive) {
			// we are not active, so we just use the saved values
			deciSeconds = prefs.getInt(DECI_SECONDS, 0);
			seconds = prefs.getInt(SECONDS, 0);
			minutes = prefs.getInt(MINUTES, 0);
			hours = prefs.getInt(HOURS, 0);
		} else {
			if(mode == MODE_STOPWATCH) {
				// just add the values
				deciSeconds += prefs.getInt(DECI_SECONDS, 0);
				seconds += prefs.getInt(SECONDS, 0);
				minutes += prefs.getInt(MINUTES, 0);
				hours += prefs.getInt(HOURS, 0);
			} else {
				// just subtract the values
				deciSeconds = prefs.getInt(DECI_SECONDS, 0) - deciSeconds;
				seconds = prefs.getInt(SECONDS, 0) - seconds;
				minutes = prefs.getInt(MINUTES, 0) - minutes;
				hours = prefs.getInt(HOURS, 0) - hours;
			}
		}
		
		// start clock again if needed
		if(wasActive)
			count();
	}
}
