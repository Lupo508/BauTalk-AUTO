package eu.bautalk.live;

import android.view.View;
import android.widget.AdapterView;

public final class SimpleItemSelected implements AdapterView.OnItemSelectedListener {
    private final Runnable callback;
    public SimpleItemSelected(Runnable callback) { this.callback = callback; }
    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { callback.run(); }
    @Override public void onNothingSelected(AdapterView<?> parent) {}
}
