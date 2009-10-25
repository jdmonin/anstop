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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class Anstop extends Activity {
    
	
	private static final int MENU_MODE_GROUP = 0;
	private static final int MODE_STOP = 1;
	private static final int MODE_LAP = 2;
	private static final int MODE_COUNTDOWN = 3;
	
	private static final int MENU_SETTINGS = 4;
	private static final int SETTINGS_ITEM = 5;
	
	private static final int MENU_ABOUT = 6;
	private static final int ABOUT_ITEM = 7;
	
	private static final int MENU_SAVE = 8;
	private static final int SAVE_ITEM = 9;
	
	private static final int MENU_LOAD = 10;
	private static final int LOAD_ITEM = 11;

	private static final int STOP = 0;
	private static final int COUNTDOWN = 1;
	private static final int LAP = 2;
	
	private int current;
	
	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;
	
	private static final int SETTINGS_ACTIVITY = 0;
	
	private static final int VIEW_SIZE = 60;
	
	private int laps = 1;
	
	Clock clock;
	Button startButton;
	Button resetButton;
	Button refreshButton;
	Button lapButton;
	TextView dsecondsView;
	TextView secondsView;
	TextView minView;
	TextView hourView;
	TextView lapView;
	Spinner secSpinner;
	Spinner minSpinner;
	Spinner hourSpinner;
	
	MenuItem modeMenuItem;
	
	Context mContext;
	Vibrator vib;
	
	AccelerometerListener al;
	
	anstopDbAdapter dbHelper;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        //set the View Objects
        dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        
        //set the size
        TextView sepView = (TextView) findViewById(R.id.sepView1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) findViewById(R.id.sepView2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);
        
        dbHelper = new anstopDbAdapter(this);
        dbHelper.open();
        
        //set the clock object
        clock = new Clock(this);
        
        //set Buttons and Listeners
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new resetButtonListener());
        

        mContext = getApplicationContext();
               
        //read Preferences
        readSettings();
        
        current = STOP;
        
    }
    
    private void readSettings() {
    	
    	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
    	if( settings.getBoolean("use_motion_sensor", false) ) {
        	al = new AccelerometerListener(this, clock);
        	al.start();
        }
        else {
        	if(al != null)
        		al.stop();
        }
        
        if( settings.getBoolean("vibrate", true) ) 
        	vib = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        else
        	vib = null;
    }
    
    public void countdown() {
    	
    	//set the Layout to the countdown layout
    	setContentView(R.layout.countdown);
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        
        //set the size
        TextView sepView = (TextView) findViewById(R.id.sepView1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) findViewById(R.id.sepView2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);
        
        //adding spinners
        secSpinner = (Spinner) findViewById(R.id.secSpinner);
        minSpinner = (Spinner) findViewById(R.id.minSpinner);
        hourSpinner = (Spinner) findViewById(R.id.hourSpinner);
        
        //creating Adapter for Spinners
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.num, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        //set the adapter to the spinners
        secSpinner.setAdapter(adapter);
        minSpinner.setAdapter(adapter);
        hourSpinner.setAdapter(adapter);
        
        //set onlicklisteners
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        refreshButton  = (Button) findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(new refreshButtonListener());
        
        current = COUNTDOWN;
        
    }
    
    public void lap() {
    	
    	//set the Layout to the countdown layout
    	setContentView(R.layout.lap);
    	
    	//set Views Buttons and Listeners for the new Layout
    	dsecondsView = (TextView) findViewById(R.id.dsecondsView);
        secondsView = (TextView) findViewById(R.id.secondsView); 
        minView = (TextView) findViewById(R.id.minView);
        hourView = (TextView) findViewById(R.id.hourView);
        
        //set the size
        TextView sepView = (TextView) findViewById(R.id.sepView1);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        sepView = (TextView) findViewById(R.id.sepView2);
        sepView.setTextSize(VIEW_SIZE - 10);
        
        
        dsecondsView.setTextSize(VIEW_SIZE);
        secondsView.setTextSize(VIEW_SIZE);
        minView.setTextSize(VIEW_SIZE);
        hourView.setTextSize(VIEW_SIZE - 30);
        
        lapView = (TextView) findViewById(R.id.lapView);
        lapView.setTextSize(VIEW_SIZE - 30);
        
        lapButton = (Button) findViewById(R.id.lapButton);
        lapButton.setOnClickListener(new lapButtonListener());
        
        startButton = (Button) findViewById(R.id.startButton);
        startButton.setOnClickListener(new startButtonListener());
        
        resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new resetButtonListener());
        
        current = LAP;
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	//Save & Load
    	menu.add(MENU_SAVE, SAVE_ITEM, 0, R.string.save).setIcon(android.R.drawable.ic_menu_save);
    	menu.add(MENU_LOAD, LOAD_ITEM, 0, R.string.load).setIcon(android.R.drawable.ic_menu_upload);
    	
    	//Mode Submenu
    	SubMenu modeMenu = menu.addSubMenu(R.string.mode).setIcon(android.R.drawable.ic_menu_more);
    	modeMenu.add(MENU_MODE_GROUP, MODE_STOP, 0, R.string.stop);
    	modeMenu.add(MENU_MODE_GROUP, MODE_LAP, 0, R.string.lap_mode);
    	modeMenu.add(MENU_MODE_GROUP, MODE_COUNTDOWN, 0, R.string.countdown);
    	modeMenu.setGroupCheckable(MENU_MODE_GROUP, true, true);
    	modeMenuItem = modeMenu.getItem();
    	
    	if(clock.isStarted)
    		modeMenuItem.setEnabled(false);

    	//Settings Menu
    	menu.add(MENU_SETTINGS, SETTINGS_ITEM, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
    	
    	//about
    	menu.add(MENU_ABOUT, ABOUT_ITEM, 0, R.string.about).setIcon(android.R.drawable.ic_menu_info_details);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent i = new Intent();
    	
        switch (item.getItemId()) {
        case SETTINGS_ITEM:
        	i.setClass(this, settingsActivity.class);
        	startActivityForResult(i, SETTINGS_ACTIVITY);
        	readSettings();
        	return true;
        case MODE_STOP:
        	onCreate(new Bundle()); //set layout to the normal Layout
        	clock.v = STOP; //inform clock class to stop time
            return true;
            
        case MODE_COUNTDOWN:
        	countdown(); //set the layout to the countdown Layout
        	clock.v = COUNTDOWN; //inform clock class to count down now
        	return true;
        case MODE_LAP:
        	lap();
        	clock.v = STOP; //lapmode is the same as stop
        	return true;
        	
        case ABOUT_ITEM:
        	showDialog(ABOUT_DIALOG);
        	return true;
        	
        case SAVE_ITEM:
        	showDialog(SAVE_DIALOG);
        	return true;
        	
        case LOAD_ITEM:
        	i.setClass(this, loadActivity.class);
        	startActivity(i);
        	return true;
        	
        
        }
        return false;
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
        case ABOUT_DIALOG:
        	AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(this);
        	aboutBuilder.setMessage(R.string.about_dialog)
        	       .setCancelable(true)
        	.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                     dialog.dismiss();
                }
            });

        	       
        	dialog = aboutBuilder.create();
        	break;
        	
               
        case SAVE_DIALOG:
        	AlertDialog.Builder saveBuilder = new AlertDialog.Builder(this);
        	saveBuilder.setTitle(R.string.save);
        	saveBuilder.setMessage(R.string.save_dialog);
        	final EditText input = new EditText(this);
        	saveBuilder.setView(input);
        	
        	saveBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
		    			
        				String body;
		            	switch(current) {
		    			case LAP:
		    				body = mContext.getResources().getString(R.string.mode_was) + " " 
		    					+ mContext.getResources().getString(R.string.lap_mode) + "\n" 
		    					+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
		    					+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
		    					+ ":" + dsecondsView.getText().toString() + "\n" + lapView.getText().toString();
		    				break;
		    			case COUNTDOWN:
		    				body = mContext.getResources().getString(R.string.mode_was) + " " 
		    					+ mContext.getResources().getString(R.string.countdown) + "\n" 
		    					+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
		    					+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
		    					+ ":" + dsecondsView.getText().toString() + "\n" + mContext.getResources().getString(R.string.start_time)
		    					+ "\n" + hourSpinner.getSelectedItemPosition() + " "
		    					+ mContext.getResources().getString(R.string.hour) + "\n"
		    					+ clock.nf.format(secSpinner.getSelectedItemPosition()) + ":" 
		    					+ clock.nf.format(minSpinner.getSelectedItemPosition()) + ".0";
		    				break;
		    			case STOP:
		    				body = mContext.getResources().getString(R.string.mode_was) + " " 
		    					+ mContext.getResources().getString(R.string.stop) + "\n" 
		    					+ hourView.getText().toString() + " " + mContext.getResources().getString(R.string.hour)
		    					+ "\n" + minView.getText().toString() + ":" + secondsView.getText().toString()
		    					+ ":" + dsecondsView.getText().toString();
		    				break;
		    			default:
		    				body = "ModeError";
		    				break;
		    			}
        			
        				
		    			dbHelper.createNew(input.getText().toString(), body);
		    			Toast toast = Toast.makeText(getApplicationContext(), R.string.saved_succes, Toast.LENGTH_SHORT);
		    			toast.show();
        			}
        		});
        	
        	saveBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
	        			dialog.dismiss();
        			}
        		});
        	saveBuilder.setCancelable(false);
        	dialog = saveBuilder.create();
        	break;
        default: dialog = null;
		}
		
		
        return dialog;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        	readSettings(); //only settingsactivty is started for result
        }



    
    
    private class startButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		
    		clock.count(); //start counting
    		
    		if(modeMenuItem != null)
    			modeMenuItem.setEnabled(!modeMenuItem.isEnabled());
    		
    		
    		if(vib != null)
    			vib.vibrate(50);
    		
    		
        	
        }
    }
    
    private class resetButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		
    		if(!clock.isStarted) {
    			//reset all Views to zero
    			dsecondsView.setText("0");
    			secondsView.setText("00");
    			minView.setText("00");
    			hourView.setText("0");
    			
    			laps = 1;
    			if(lapView != null)
    				lapView.setText(R.string.laps);
    		}    		
    		else {
    			//Show error when currently counting
    			Toast toast = Toast.makeText(mContext, R.string.reset_during_count, Toast.LENGTH_SHORT);
    			toast.show();
    		}
    	}
    }
    
    private class refreshButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
    		
    		if(!clock.isStarted) {
    			//set the Views to the input data
    			dsecondsView.setText("0");
    			
    			//looking for the selected Item position (is the same as the Item itself)
    			//using the NumberFormat from class clock to format 
    			secondsView.setText("" + clock.nf.format(secSpinner.getSelectedItemPosition()));
    			minView.setText("" + clock.nf.format(minSpinner.getSelectedItemPosition()));
    			hourView.setText("" + hourSpinner.getSelectedItemPosition());
    			
    			
    		}
    		else {
    			//Show error when currently counting
    			Toast toast = Toast.makeText(mContext, R.string.refresh_during_count, Toast.LENGTH_SHORT);
    			toast.show();
    		}
    	}
    }
    
    private class lapButtonListener implements OnClickListener {
    	
    	public void onClick(View v) {
        	lapView.append("\n" + (laps++) + ". " + hourView.getText() + "h " + minView.getText()
        			+ ":" + secondsView.getText() + ":" + dsecondsView.getText());
        	
        	if(vib != null)
        		vib.vibrate(50);
        }
    }

    
    
}