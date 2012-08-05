package An.stop.util;

import An.stop.R;
import android.app.Activity;
import android.content.Intent;

/**
 * Little helper class for various things.
 * 
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
}
