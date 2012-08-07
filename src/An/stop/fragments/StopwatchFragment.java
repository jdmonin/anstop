package An.stop.fragments;

import An.stop.Clock;
import An.stop.R;



public class StopwatchFragment extends ClockFragment {

	public StopwatchFragment() {
		super(R.layout.stopwatch, Clock.MODE_STOPWATCH);
	}
	
	@Override
	protected void reset() {
		clock.reset();
		setDeciSeconds(0);
		setSeconds(0);
		setMinutes(0);
		setHours(0);
	}
}
