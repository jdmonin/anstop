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


package An.stop;

import An.stop.fragments.CountdownFragment;
import An.stop.fragments.StopwatchFragment;
import An.stop.util.Util;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Anstop's main activity, showing the current getClock(), lap times, etc.
 *<P>
 * Uses either of 2 layouts, depending on the {@link getClock()#getMode() current mode}:
 * <tt>main</tt> or <tt>countdown</tt>.
 * Main implements {@link #STOP_LAP} which includes Lap mode.
 *<P>
 * Many fields' visibility are non-private for use by
 * {@link getClock()#fillSaveState(Bundle)} and {@link getClock()#restoreFromSaveState(Bundle)}.
 */
public class AnstopActivity extends FragmentActivity {
	
	private static final int ABOUT_DIALOG = 0;
	private static final int SAVE_DIALOG = 1;
	
	private static final int SETTINGS_REQUEST_CODE = 0; 
	
	ViewPager viewPager;

	/**
	 * Called when the activity is first created.
	 * Assumes {@link #STOP_LAP} mode and sets that layout.
	 * Preferences are read, which calls setCurrentMode.
	 * Also called later, to set the mode/layout back to {@link #STOP_LAP}.
	 */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        viewPager = (ViewPager) findViewById(R.id.view_pager);
        
        viewPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager()) {
        	
        	private final Fragment[] fragments = {new StopwatchFragment(), new CountdownFragment()};
        	
			@Override
			public int getCount() {
				return fragments.length;
			}
			
			@Override
			public Fragment getItem(int pos) {
				return fragments[pos];
			}
		});
    }

    /**
     * Read our settings, at startup or after calling the SettingsActivity.
     *<P>
     * Does not check <tt>"anstop_in_use"</tt> or read the instance state
     * when the application is finishing; for that, see {@link getClock()#restoreFromSaveState(SharedPreferences)}.
     *
     * @param isStartup Are we just starting now, not already running?
     */
    private void readSettings() {

    	
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent i = new Intent();

    	switch (item.getItemId()) {
    	case R.id.menu_settings:
    		i.setClass(this, SettingsActivity.class);
    		startActivityForResult(i, SETTINGS_REQUEST_CODE);
    		// on result, will call readSettings(false).
    		return true;

    	case R.id.menu_item_stop:
    		viewPager.setCurrentItem(0);
    		return true;

    	case R.id.menu_item_countdown:
    		viewPager.setCurrentItem(1);
    		return true;

    	case R.id.menu_about:
    		showDialog(ABOUT_DIALOG);
    		return true;

    	case R.id.menu_save:
    		showDialog(SAVE_DIALOG);
    		return true;

    	case R.id.menu_send:
    		//TODO
    		Util.startSendMailIntent
    		(this, getResources().getString(R.string.app_name) + ": " , ""); //currentModeAsString(), createBodyFromCurrent());
    		return true;

    	case R.id.menu_load:
    		i.setClass(this, LoadActivity.class);
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
		    			
        				// TODO save!
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
    	readSettings(); // because only settingsactivity is started for
    	// result, we can launch that without checking the parameters.
    }
}