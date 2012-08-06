	package An.stop.widgets;

import An.stop.R;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

public class CountdownPicker extends LinearLayout {

	public CountdownPicker(Context context, AttributeSet attrs) {
		super(context, attrs);
		LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.countdown_picker, this, true);
	}

}
