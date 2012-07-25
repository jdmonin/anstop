package An.stop.fragments;

import An.stop.R;
import An.stop.util.AnstopDbAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class CountdownFragment extends ClockFragment {
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.countdown, container, false);
		countdown(view);
        return view;
    }
	
	/**
     * Set the layout to {@link #COUNTDOWN}, and
     * inform clock class to count down now.
     */
    public void countdown(View v) {
        comment = null;
        if ((laps != null) && (laps.length() > 0))
        {
        	laps.delete(0, laps.length());
        	if (dbHelper == null)
        	{
        		dbHelper = new AnstopDbAdapter(getActivity());
        		dbHelper.open();
        	}
        	dbHelper.deleteTemporaryLaps();
        }
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) v.findViewById(R.id.dseconds_view);
        secondsView = (TextView) v.findViewById(R.id.seconds_view); 
        minView = (TextView) v.findViewById(R.id.min_view);
        hourView = (TextView) v.findViewById(R.id.hour_view);
        startTimeView = (TextView) v.findViewById(R.id.countdown_start_time_view);
        setupCommentLongPress(startTimeView);
        lapView = null;
        lapScroll = null;
        if (startTimeView.length() == 0)
        	wroteStartTime = false;

        //set the size
        TextView sepView = (TextView) v.findViewById(R.id.sep_view1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) v.findViewById(R.id.sep_view2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);
        startTimeView.setTextSize(VIEW_SIZE - 30);
        
        //adding spinners
        secSpinner = (Spinner) v.findViewById(R.id.sec_spinner);
        minSpinner = (Spinner) v.findViewById(R.id.min_spinner);
        hourSpinner = (Spinner) v.findViewById(R.id.hour_spinner);
        
        //creating Adapter for Spinners
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.num, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        //set the adapter to the spinners
        secSpinner.setAdapter(adapter);
        minSpinner.setAdapter(adapter);
        hourSpinner.setAdapter(adapter);
        
        //set onlicklisteners
        startButton = (Button) v.findViewById(R.id.start_button);
        startButton.setOnClickListener(new StartButtonListener());
        
        refreshButton  = (Button) v.findViewById(R.id.refresh_button);
        refreshButton.setOnClickListener(new RefreshButtonListener());

        // inform clock class to count down now
        clock.changeMode(COUNTDOWN);
    }
}
