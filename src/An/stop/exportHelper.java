/***************************************************************************
 *   Copyright (C) 2009 by mj   										   *
 *   fakeacc.mj@gmail.com  												   *
 *   Portions of this file Copyright (C) 2010 Jeremy Monin jeremy@nand.net *
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Environment;

public class exportHelper {
	
	private FileOutputStream out;
	Context mContext;
	
	public exportHelper(Context context) {
		this.mContext = context;
	}

	/**
	 * Create and write a file on external storage (SD-Card). 
	 * @param title file's title; filename will be this + ".txt"
	 * @param body  Contents of file
	 * @return Success or failure
	 */
	public boolean write(String title, String body) {
		
		String storageState = Environment.getExternalStorageState();
		if (storageState.contains("mounted")) {
			File f = Environment.getExternalStorageDirectory();
			File j = new File(f, title + ".txt");
			
			try {
				out = new FileOutputStream(j);
			} catch (FileNotFoundException e) {
				return false;
			}
			
			try {
				out.write(body.getBytes());
			} catch (IOException e) {
				return false;
			}
			
			return true;
		}
		
		return false;
		
		
	}

	/**
	 * Export a saved record to disk.
	 * @param rowId The record's _id
	 * @return Success or failure
	 */
	public boolean write(long rowId) {
		String[] columns = getRow(rowId);
		if (columns == null)
			return false;
		boolean val = write(columns[0], columns[1]);
		return val;
		
	}

	/**
	 * Get this rowId's title and body from the database. 
	 * @param rowId The _id of the record to retrieve
	 * @return String[] with [0]=title, [1]=body, or null if not found.
	 */
	public String[] getRow(long rowId) {
		anstopDbAdapter dbHelper = null;
		String[] columns = null;
		try {
			dbHelper = new anstopDbAdapter(mContext);
			dbHelper.open();
			
			Cursor time = dbHelper.fetch(rowId);
			columns = new String[2];
			columns[0] = time.getString(time.getColumnIndexOrThrow(anstopDbAdapter.KEY_TITLE));
			columns[1] = time.getString(time.getColumnIndexOrThrow(anstopDbAdapter.KEY_BODY));
			
			dbHelper.close();
		} catch (SQLException e) {
			if (dbHelper != null)
				dbHelper.close();
		}
		return columns;
	}
	
	public boolean write(Cursor c) {
		c.moveToFirst();
		boolean val = true;
		
		for(int i = 0; i < c.getCount(); i++) {
			if(!write(c.getColumnName(i), c.getString(i)))
				val = false; //if one entry fails we will return false
							 //but we keep trying to write the other files
		}
		
		
			
		return val;
		
		
		
	}

}
