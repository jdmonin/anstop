package An.stop.fragments;

import An.stop.Clock;
import An.stop.R;
import An.stop.widgets.TimePicker;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class CountdownFragment extends ClockFragment {
	
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
	protected void count() {
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
	}
}
