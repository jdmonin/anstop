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


import java.util.List;


import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AccelerometerListener implements SensorEventListener {
	
	private SensorManager sensorManager;
	private List<Sensor> sensors;
	private Sensor sensor;
	private long lastUpdate = -1;
	private long currentTime = -1;
	
	private float last_x, last_y, last_z;
	private float current_x, current_y, current_z, currenForce;
	private static final int FORCE_THRESHOLD = 900;
	private final int DATA_X = SensorManager.DATA_X;
	private final int DATA_Y = SensorManager.DATA_Y;
	private final int DATA_Z = SensorManager.DATA_Z;
	private Clock clock;
	
	public AccelerometerListener(Activity parent, Clock clock) {
		SensorManager sensorManager = (SensorManager) parent.getSystemService(Context.SENSOR_SERVICE);
		this.sensorManager = sensorManager;
		this.sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
		this.clock = clock;
		if (sensors.size() > 0) {
            sensor = sensors.get(0);
        }
	}
	public void start () {
		if (sensor!=null)  {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
		}
	}
	
	public void stop () {
		sensorManager.unregisterListener(this);
	}
	
	public void onAccuracyChanged(Sensor s, int valu) {
		
		//nothing to do yet
	}
	
	
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || event.values.length < 3)
		      return;
		
		currentTime = System.currentTimeMillis();
		
		if ((currentTime - lastUpdate) > 100) {
			long diffTime = (currentTime - lastUpdate);
			lastUpdate = currentTime;
			
			current_x = event.values[DATA_X];
			current_y = event.values[DATA_Y];
			current_z = event.values[DATA_Z];
			
			currenForce = Math.abs(current_x+current_y+current_z - last_x - last_y - last_z) / diffTime * 10000;
			
			if (currenForce > FORCE_THRESHOLD)				
				clock.count(); //phone has been shaken
			
			last_x = current_x;
			last_y = current_y;
			last_z = current_z;

		}
	}

}