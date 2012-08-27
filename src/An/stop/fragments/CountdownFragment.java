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

package An.stop.fragments;

import An.stop.Clock;
import An.stop.R;
import An.stop.widgets.TimePicker;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment for the countdown mode. Uses the countdown layout.
 * @see R#layout#countdown
 * @see Clock#MODE_COUNTDOWN
 */
public class CountdownFragment extends ClockFragment {
	
	private static final String TAG = "CD_FRAG";
	
	private static final String SEC_PICKER = "sec_picker";
	private static final String MIN_PICKER = "min_picker";
	private static final String HOURS_PICKER = "hours_picker";
	
	private TimePicker secondsPicker;
	private TimePicker minutesPicker;
	private TimePicker hoursPicker;

	public CountdownFragment() {
		super(R.layout.countdown, Clock.MODE_COUNTDOWN);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		
		secondsPicker = (TimePicker) view.findViewById(R.id.second_picker);
		minutesPicker = (TimePicker) view.findViewById(R.id.minute_picker);
		hoursPicker = (TimePicker) view.findViewById(R.id.hour_picker);
		
		return view;
	}
	
	@Override
	protected void saveState() {
		super.saveState();
		
		// we also save the current picker values
		SharedPreferences.Editor editor = getActivity().getSharedPreferences(TAG, Activity.MODE_PRIVATE).edit();
		editor.putInt(SEC_PICKER, secondsPicker.getValue());
		editor.putInt(MIN_PICKER, minutesPicker.getValue());
		editor.putInt(HOURS_PICKER, hoursPicker.getValue());
		
		if(!editor.commit())
			Log.e(TAG, "could not save state!");
	}
	
	@Override
	protected void restoreState() {
		super.restoreState();
		SharedPreferences prefs = getActivity().getSharedPreferences(TAG, Activity.MODE_PRIVATE);
		
		secondsPicker.setValue(prefs.getInt(SEC_PICKER, 0));
		minutesPicker.setValue(prefs.getInt(MIN_PICKER, 0));
		hoursPicker.setValue(prefs.getInt(HOURS_PICKER, 0));
	}

	@Override
	protected void count() {
		// check if the values are okay
		// that means any value must be greater zero
		for(int val : clock.getValues()) {
			if(val != 0) {
				super.count();
				return;
			}
		}
	}

	@Override
	protected void reset() {
		int seconds = secondsPicker.getValue();
		int minutes = minutesPicker.getValue();
		int hours = hoursPicker.getValue();
		
		clock.setCountdown(hours, minutes, seconds);
		
		setSeconds(seconds);
		setMinutes(minutes);
		setHours(hours);
		setDeciSeconds(0);
	}
}
