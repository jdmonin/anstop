package An.stop.fragments;

import An.stop.Clock;
import An.stop.R;



public class StopwatchFragment extends ClockFragment {

	public StopwatchFragment() {
		super(R.layout.stopwatch, Clock.MODE_STOPWATCH);
	}
	
	protected void reset() {
		clock.reset();
		hoursView.setText("0");
		minutesView.setText("00");
		secondsView.setText("00");
		deciSecondsView.setText("0");
	}
}
