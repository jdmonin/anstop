package An.stop.fragments;

import An.stop.R;
import An.stop.util.AnstopDbAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;


public class StopwatchFragment extends ClockFragment {
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.countdown, container, false);
		stopwatch(view);
        return view;
    }
	
	/**
     * Set the layout to {@link #STOP_LAP}, and reset clock to 0:0:0.0.
     * inform clock class to count laps now (same clock-action as STOP).
     */
    public void stopwatch(View v) {
        comment = null;
        if (laps == null)
        	laps = new StringBuilder();
        else if (laps.length() > 0)
        {
        	laps.delete(0, laps.length());
        	if (dbHelper == null)
        	{
        		dbHelper = new AnstopDbAdapter(getActivity());
        		dbHelper.open();
        	}
        	dbHelper.deleteTemporaryLaps();
        }
        laps.append(getResources().getString(R.string.laps));
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) v.findViewById(R.id.dseconds_view);
        secondsView = (TextView) v.findViewById(R.id.seconds_view); 
        minView = (TextView) v.findViewById(R.id.min_view);
        hourView = (TextView) v.findViewById(R.id.hour_view);
        
        //set the size
        TextView sepView = (TextView) v.findViewById(R.id.sep_view1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) v.findViewById(R.id.sep_view2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);

        startTimeView = null;
        lapView = (TextView) v.findViewById(R.id.lap_view);
        lapView.setTextSize(VIEW_SIZE - 30);
        setupCommentLongPress(lapView);
        wroteStartTime = false;

        lapButton = (Button) v.findViewById(R.id.lap_button);
        lapButton.setOnClickListener(new LapButtonListener());
        
        startButton = (Button) v.findViewById(R.id.start_button);
        startButton.setOnClickListener(new StartButtonListener());
        
        resetButton = (Button) v.findViewById(R.id.reset_button);
        resetButton.setOnClickListener(new ResetButtonListener());

        lapScroll = (ScrollView) v.findViewById(R.id.lap_scroll_view);

        // inform clock of the new mode
        clock.changeMode(STOP_LAP);
    }
}
