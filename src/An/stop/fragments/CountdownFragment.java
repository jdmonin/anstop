package An.stop.fragments;

import An.stop.Clock;
import An.stop.R;


public class CountdownFragment extends ClockFragment {

	public CountdownFragment() {
		super(R.layout.countdown, Clock.MODE_STOPWATCH);
	}
	
	protected void reset() {
	}
}
