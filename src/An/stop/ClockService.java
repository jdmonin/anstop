/***************************************************************************
 *   Copyright (C) 2009 by mj   										   *
 *   fakeacc.mj@gmail.com  												   *
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

import java.util.Timer;
import java.util.TimerTask;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

public class ClockService extends Service {

	private static final int STOP = 0;
	// private static final int COUNTDOWN = 1;

	private int curMode = 0;

	public boolean isStarted = false;
	Timer timer = new Timer();

	int dsec = 0;
	int sec = 0;
	int min = 0;
	int hour = 0;

	private NotificationManager nManager;
	
	private final RemoteCallbackList<IClockCounterCallback> callbacks = new RemoteCallbackList<IClockCounterCallback>();
	
	private final IClockCounter.Stub binder = new IClockCounter.Stub() {
		
		public void unregisterCallback(IClockCounterCallback cb)
				throws RemoteException {
			callbacks.register(cb);
		}
		
		public void setMode(int mode) throws RemoteException {
			curMode = mode;
		}
		
		public void registerCallback(IClockCounterCallback cb)
				throws RemoteException {
			callbacks.unregister(cb);
		}
		
		public int getMode() throws RemoteException {
			return curMode;
		}

		public boolean isStarted() throws RemoteException {
			return isStarted;
		}
	};

	
	@Override
	public void onCreate() {
		super.onCreate();
		
		nManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		callbacks.kill();
		timer.cancel();
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		count();
	}
	
	public void count() {
				
		if (curMode == STOP) {
			timer.scheduleAtFixedRate(new TimerTask() {

				public void run() {
					ClockCounter();
				}
			}, 0, 100);
		}

		else {
			timer.scheduleAtFixedRate(new TimerTask() {

				public void run() {
					CountdownCounter();
				}
			}, 0, 100);
		}

	}

	private void ClockCounter() {

		dsec++;

		if (dsec == 10) {
			sec++;
			dsec = 0;
			// sech.sendEmptyMessage(Thread.MAX_PRIORITY);

			if (sec == 60) {
				min++;
				sec = 0;
				// minh.sendEmptyMessage(Thread.MAX_PRIORITY);

				if (min == 60) {
					hour++;
					min = 0;
					// hourh.sendEmptyMessage(Thread.MAX_PRIORITY);
					// minh.sendEmptyMessage(Thread.MAX_PRIORITY);
				}
			}
		}

		// dsech.sendEmptyMessage(Thread.MAX_PRIORITY);
	}

	private void CountdownCounter() {

		if (hour == 0 && min == 0 && sec == 0 && dsec == 0) {
			isStarted = false;
			return;
		}

		if (dsec == 0) {

			if (sec == 0) {
				if (min == 0) {
					if (hour != 0) {
						hour--;
						min = 60;
						// hourh.sendEmptyMessage(Thread.MAX_PRIORITY);
						// minh.sendEmptyMessage(Thread.MAX_PRIORITY);

					}
				}

				if (min != 0) {
					min--;
					sec = 60;
					// minh.sendEmptyMessage(Thread.MAX_PRIORITY);
				}

			}

			if (sec != 0) {
				sec--;
				dsec = 10;
				// sech.sendEmptyMessage(Thread.MAX_PRIORITY);
			}

		}
		dsec--;

		// dsech.sendEmptyMessage(Thread.MAX_PRIORITY);

		if (hour == 0 && min == 0 && sec == 0 && dsec == 0) {
			isStarted = false;
			return;
		}

	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

}
