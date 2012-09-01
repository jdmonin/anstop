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

package An.stop.util;

import java.text.NumberFormat;

import An.stop.R;
import android.app.Activity;
import android.content.Intent;

/**
 * Little helper class for various things.
 */
public class Util {
	
	/**
     * Start the appropriate intent for the user to send an E-Mail or SMS
     * with these contents.
     * @param caller  The calling activity; used to launch Send intent
     * @param title Subject line for e-mail; if user has only SMS configured, this will be unused. 
     * @param body  Body of e-mail
     */
    public static void startSendMailIntent(Activity caller, final String title, final String body) {
            Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
            String[] empty_recipients = new String[]{ "" };
            emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, empty_recipients);
            emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
            emailIntent.setType("text/plain");
            // use Chooser, in case multiple apps are installed
            caller.startActivity(Intent.createChooser(emailIntent, caller.getResources().getString(R.string.send)));  
    }
    
    /**
     * Returns a NumberFormat with two minimum digits.
     * @return the NumberFormat
     */
    public static NumberFormat getTwoDigitFormat() {
    	NumberFormat format = NumberFormat.getInstance();
    	format.setMinimumIntegerDigits(2);
    	return format;
    }
}
