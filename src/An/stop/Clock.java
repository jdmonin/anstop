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


import java.text.NumberFormat;

import android.os.Handler;
import android.os.Message;

public class Clock {
	
	private static final int STOP = 0;
	//private static final int COUNTDOWN = 1;
	
	public int v;
	
	public boolean isStarted = false;
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
		
	}
	
	
	public void count() {
		
		if(!isStarted) {
			
			dsec = Integer.valueOf(parent.dsecondsView.getText().toString());
			sec  = Integer.valueOf(parent.secondsView.getText().toString());
			min  = Integer.valueOf(parent.minView.getText().toString());
			hour = Integer.valueOf(parent.hourView.getText().toString());
			
			isStarted = true;
			if(v == STOP) {
				threadS = new clockThread();
				threadS.start();
			}
				
			else {
				threadC = new countDownThread();
				threadC.start();
			}
			
			
		}
		else {
			isStarted = false;
			
			if(v == STOP) {
				if(threadS.isAlive())
					threadS.interrupt();
			}
				
			else {
				if(threadC.isAlive())
					threadC.interrupt();
			}
		}
			
	}
	
	private class clockThread extends Thread {		
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
	
	private class countDownThread extends Thread {
		@Override
		public void run() {
			
			
			if(hour == 0 && min == 0 && sec == 0 && dsec == 0) {
				isStarted = false;
				parent.modeMenuItem.setEnabled(false);
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
					parent.modeMenuItem.setEnabled(false);
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
