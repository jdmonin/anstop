/***************************************************************************
 *   Copyright (C) 2009-2010 by mj                                         *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2010,2012 Jeremy Monin            *
 *     jeremy@nand.net                                                     *
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
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Shows a previously saved time in the database,
 * with options to delete, export or send.
 * Called from {@link LoadActivity} list item clicks.
 */
public class ShowTimesActivity extends Activity {
	
	private static final int VIEW_SIZE = 30;
	
	private static final int MENU_DELETE = 12;
	private static final int DELETE_ITEM = 13;
	
	private static final int MENU_EXPORT = 14;
	private static final int EXPORT_ITEM = 15;
	
	private static final int MENU_SEND = 16;
	private static final int SEND_ITEM = 17;

	private AnstopDbAdapter dbHelper;
	private Long mRowId;
	TextView titleView;
	TextView bodyView;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.show_times);
		dbHelper = new AnstopDbAdapter(this);
		dbHelper.open();
		
		bodyView = (TextView) findViewById(R.id.bodyView);
		titleView = (TextView) findViewById(R.id.titleView);
		mRowId = savedInstanceState != null ? savedInstanceState.getLong(AnstopDbAdapter.KEY_ROWID) 
				: null;
		
		if (mRowId == null) {
			Bundle extras = getIntent().getExtras();            
			mRowId = extras != null ? extras.getLong(AnstopDbAdapter.KEY_ROWID) 
					: null;
		}
		
		Cursor time = dbHelper.fetch(mRowId);
		startManagingCursor(time);
		
		titleView.setText(time.getString(time.getColumnIndexOrThrow(AnstopDbAdapter.KEY_TITLE)));
		final int col_body = time.getColumnIndexOrThrow(AnstopDbAdapter.KEY_BODY),
		          col_mode = time.getColumnIndex(AnstopDbAdapter.FIELD_TIMES_MODE);
		if (time.isNull(col_mode))
		{
			// Simple: no mode
			bodyView.setText(time.getString(col_body));
		} else {
			// Mode, laps, start time are separate fields. Col_body contains the comment only.
			// getRowAndFormat combines and formats those db fields into one body string.
			final String[] formatted = dbHelper.getRowAndFormat(mRowId);
			bodyView.setText(formatted[1]);
		}

		dbHelper.close();
		
		titleView.setTextSize(VIEW_SIZE);
		bodyView.setTextSize(VIEW_SIZE - 10);
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	menu.add(MENU_EXPORT, EXPORT_ITEM, 0, R.string.export).setIcon(android.R.drawable.ic_menu_share);
    	menu.add(MENU_SEND, SEND_ITEM, 0, R.string.send).setIcon(android.R.drawable.ic_menu_send);
    	menu.add(MENU_DELETE, DELETE_ITEM, 0, R.string.delete).setIcon(android.R.drawable.ic_menu_delete);
    	
        return true;
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case DELETE_ITEM:
			dbHelper.open();
	        dbHelper.delete((long)mRowId);
	        dbHelper.close();
	        finish();
	        return true;
	        
		case EXPORT_ITEM:
			ExportHelper exHlp = new ExportHelper(this);
			
			Toast toast;
			
			if(exHlp.write(titleView.getText().toString(), bodyView.getText().toString()))
				toast = Toast.makeText(this, R.string.export_succes, Toast.LENGTH_SHORT);
			else
				toast = Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT);
			
			toast.show();
			
			
			return true;

		case SEND_ITEM:
	        Anstop.startSendMailIntent
	        	(this, getResources().getString(R.string.app_name) + ": " + titleView.getText().toString(), bodyView.getText().toString());
	    	return true;
		}
		
		return false;
	}
	
	@Override
	public void onSaveInstanceState (Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(AnstopDbAdapter.KEY_ROWID, mRowId);
	}
	

}