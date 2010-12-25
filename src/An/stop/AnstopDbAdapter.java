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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class AnstopDbAdapter {
	
    private static final int DATABASE_VERSION = 2;
    
    private static final String DATABASE_NAME = "Anstop_db";
    private static final String DATABASE_TABLE = "times";
    
    public static final String KEY_TITLE = "title";
    public static final String KEY_BODY = "body";
    public static final String KEY_ROWID = "_id";


    private SQLiteDatabase mDb;
    private DataBaseHelper dbHelper;
    private Context mContext;
    
    private static class DataBaseHelper extends SQLiteOpenHelper {

        DataBaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL("create table times (_id integer primary key autoincrement, "
                    + "title text not null, body text not null);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS times");
            onCreate(db);
        }
    }
    
    AnstopDbAdapter(Context context) {
    	this.mContext = context;
    }
    
    public AnstopDbAdapter open() throws SQLException {
    		dbHelper = new DataBaseHelper(mContext);
        	mDb = dbHelper.getWritableDatabase();
        	return this;
        }
    
    public void close() {
    		dbHelper.close();
    	}
    
    public long createNew(String title, String body) {
    	ContentValues cl = new ContentValues();
    	cl.put(KEY_TITLE, title);
    	cl.put(KEY_BODY, body);
    	
    	return mDb.insert(DATABASE_TABLE, null, cl);
    	
    	
    }
    
    public boolean delete(long rowId) {

        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
    }
    
    public Cursor fetchAll() {
    	
    	return mDb.query(DATABASE_TABLE, new String[] {KEY_ROWID, KEY_TITLE,
                    KEY_BODY}, null, null, null, null, null);
    	
    }
    
    public Cursor fetch(long rowId) throws SQLException {

        Cursor mCursor =

                mDb.query(true, DATABASE_TABLE, new String[] {KEY_ROWID,
                		KEY_TITLE, KEY_BODY}, KEY_ROWID + "=" + rowId, null,
                        null, null, null, null);
        if (mCursor != null) {
            mCursor.moveToFirst();
        }
        return mCursor;

    }
    
}
