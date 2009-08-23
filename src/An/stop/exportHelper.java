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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;

public class exportHelper {
	
	private FileOutputStream out;
	Context mContext;
	
	public exportHelper(Context context) {
		this.mContext = context;
	}
	
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
	
	public boolean write(long rowId) {
		anstopDbAdapter dbHelper = new anstopDbAdapter(mContext);
		dbHelper.open();
		
		Cursor time = dbHelper.fetch(rowId);
		
		boolean val = write(time.getString(time.getColumnIndexOrThrow(anstopDbAdapter.KEY_TITLE)), 
				time.getString(time.getColumnIndexOrThrow(anstopDbAdapter.KEY_BODY)));
		
		dbHelper.close();
		
		return val;
		
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
