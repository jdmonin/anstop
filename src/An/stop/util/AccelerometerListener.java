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


package An.stop.util;


import java.util.List;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Helper class which indicates when the phone has been shaken.
 */
public class AccelerometerListener implements SensorEventListener {
	
	interface OnPhoneShakenListener {
		/**
		 * Called when the phone has been shaken.
		 */
		public void onShake();
	}
	
	private SensorManager sensorManager;
	private Sensor sensor;
	private long lastUpdate = -1;
	
	private float last_x, last_y, last_z;
	private static final int FORCE_THRESHOLD = 900;
	private final int DATA_X = SensorManager.DATA_X;
	private final int DATA_Y = SensorManager.DATA_Y;
	private final int DATA_Z = SensorManager.DATA_Z;
	private OnPhoneShakenListener listener;
	
	public AccelerometerListener(Context context, OnPhoneShakenListener l) {
		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		listener = l;
		List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (sensors.size() > 0) {
            sensor = sensors.get(0);
        }
	}
	public void start () {
		if (sensor != null)  {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
		}
	}
	
	public void stop () {
		sensorManager.unregisterListener(this);
	}
	
	public void onAccuracyChanged(Sensor s, int value) {
		
	}
	
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || event.values.length < 3)
		      return;
		
		long currentTime = System.currentTimeMillis();
		
		if ((currentTime - lastUpdate) > 100) {
			long diffTime = (currentTime - lastUpdate);
			lastUpdate = currentTime;
			
			float current_x = event.values[DATA_X];
			float current_y = event.values[DATA_Y];
			float current_z = event.values[DATA_Z];
			
			float currenForce = Math.abs(current_x + current_y + current_z - last_x - last_y - last_z) / diffTime * 10000;
			
			if (currenForce > FORCE_THRESHOLD)				
				listener.onShake(); // phone has been shaken
			
			last_x = current_x;
			last_y = current_y;
			last_z = current_z;

		}
	}

}