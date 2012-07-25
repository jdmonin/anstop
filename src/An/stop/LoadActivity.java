/***************************************************************************
 *   Copyright (C) 2009-2011 by mj                                         *
 *     fakeacc.mj@gmail.com                                                *
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


import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;


/**
 * Presents the list of previously saved times in the database,
 * with options to view, delete, export or send.
 * Clicking a list item takes you to {@link ShowTimesActivity}.
 */
public class LoadActivity extends ListActivity {
	
	private AnstopDbAdapter dbHelper;
	String body;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.load);
		dbHelper = new AnstopDbAdapter(this);
		dbHelper.open();
		fillData();
		registerForContextMenu(getListView());
			
		
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		dbHelper.close();  // prevent leaks
	}
	
	private void fillData() {
		Cursor c = dbHelper.fetchAll();
        startManagingCursor(c);
        
        String[] from = new String[]{AnstopDbAdapter.KEY_TITLE};
        int[] to = new int[]{R.id.text1};
        
        
        SimpleCursorAdapter times = 
        	    new SimpleCursorAdapter(this, R.layout.times_row, c, from, to);
        setListAdapter(times);
	}
	
	 @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		 super.onCreateContextMenu(menu, v, menuInfo);
		 MenuInflater inflater = getMenuInflater();
		 inflater.inflate(R.menu.load_context_menu, menu);
	 }
	
	@Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Intent i = new Intent(this, ShowTimesActivity.class);
        i.putExtra(AnstopDbAdapter.KEY_ROWID, id);
        startActivity(i);
    }
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		switch(item.getItemId()) {
    	case R.id.context_menu_delete:
	        dbHelper.delete(info.id);
	        fillData();
	        return true;
	        
    	case R.id.context_menu_export:
    		
    		Toast toast;
    		ExportHelper exHlp = new ExportHelper(this);
	        
			
			if(exHlp.write(info.id))
				toast = Toast.makeText(this, R.string.export_succes, Toast.LENGTH_SHORT);
			else
				toast = Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT);
    		
			toast.show();
    		
    		return true;

    	case R.id.context_menu_send:
	    	{
	    		final String[] columns = dbHelper.getRowAndFormat(info.id);
	    		if (columns != null)
	    	        Anstop.startSendMailIntent
		            (this, getResources().getString(R.string.app_name) + ": " + columns[0], columns[1]);
	    	}
	    	return true;
		}
		return super.onContextItemSelected(item);
	}
	
	/*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
        return true;
    }*/
	
	/*@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case EXPORT_ALL_ITEM:
			Cursor c = dbHelper.fetchAll();
			
			Toast toast;
			
			ExportHelper exHlp = new ExportHelper(this);
			if(exHlp.write(c))
				toast = Toast.makeText(this, R.string.export_succes, Toast.LENGTH_SHORT);
			else
				toast = Toast.makeText(this, R.string.export_fail, Toast.LENGTH_SHORT);
			
			toast.show();
			
			return true;
		}
		
		return false;
	}*/

}