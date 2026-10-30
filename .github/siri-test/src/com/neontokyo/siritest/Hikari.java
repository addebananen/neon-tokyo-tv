package com.neontokyo.siritest;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** The app the voice test asks Neon Tokyo to open ("öppna Hikari"). */
public class Hikari extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText("Hikari");
        t.setTextSize(48);
        setContentView(t);
    }
}
