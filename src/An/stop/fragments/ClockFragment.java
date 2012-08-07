package An.stop.fragments;

import java.text.NumberFormat;

import An.stop.Clock;
import An.stop.R;
import An.stop.util.Util;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public abstract class ClockFragment extends Fragment {
	
	private Handler updateHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch(msg.arg1) {
			case Clock.UPDATE_DECI_SECONDS:
				deciSecondsView.setText("" + msg.arg2);
				break;
			case Clock.UPDATE_SECONDS:
				secondsView.setText(nf.format(msg.arg2));
				break;
			case Clock.UPDATE_MINUTES:
				minutesView.setText(nf.format(msg.arg2));
				break;
			case Clock.UPDATE_HOURS:
				hoursView.setText(nf.format(msg.arg2));
				break;
			}
		}
	};
	
	protected Clock clock;
	private int layoutId;
	
	private Button startButton;
	private Button resetButton;
	protected TextView hoursView;
	protected TextView minutesView;
	protected TextView secondsView;
	protected TextView deciSecondsView;
	
	private NumberFormat nf;
	
	ClockFragment(int layoutId, int mode) {
		clock = new Clock(mode, updateHandler);
		this.layoutId = layoutId;
		nf = Util.getTwoDigitFormat();
	}
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
		View view = inflater.inflate(layoutId, container, false);
		startButton = (Button) view.findViewById(R.id.start_button);
		resetButton = (Button) view.findViewById(R.id.reset_button);
		hoursView = (TextView) view.findViewById(R.id.hour_view);
		minutesView = (TextView) view.findViewById(R.id.min_view);
		secondsView = (TextView) view.findViewById(R.id.seconds_view);
		deciSecondsView = (TextView) view.findViewById(R.id.dseconds_view);
		
		startButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if(!clock.isActive())
					clock.count();
				else
					clock.stop();
			}
			
		});
		
		resetButton.setOnClickListener(new OnClickListener() {
			
			public void onClick(View v) {
				if(!clock.isActive())
					reset();
			}
		});
		
        return view;
    }
	
	/**
	 * Resets the current time. This can be to zero or maybe to the time
	 * which should be counted down!
	 */
	protected abstract void reset();
}
