package org.totipo.android;

import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

/** Dialog windows dispatch input independently of Activity.onUserInteraction. */
final class InteractionWindow implements Window.Callback {
    private final Window.Callback delegate;
    private final Runnable interaction;
    InteractionWindow(Window.Callback delegate, Runnable interaction) { this.delegate = delegate; this.interaction = interaction; }
    public boolean dispatchTouchEvent(MotionEvent e) { interaction.run(); return delegate.dispatchTouchEvent(e); }
    public boolean dispatchKeyEvent(KeyEvent e) { interaction.run(); return delegate.dispatchKeyEvent(e); }
    public boolean dispatchKeyShortcutEvent(KeyEvent e) { interaction.run(); return delegate.dispatchKeyShortcutEvent(e); }
    public boolean dispatchGenericMotionEvent(MotionEvent e) { interaction.run(); return delegate.dispatchGenericMotionEvent(e); }
    public boolean dispatchTrackballEvent(MotionEvent e) { interaction.run(); return delegate.dispatchTrackballEvent(e); }
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent e) { return delegate.dispatchPopulateAccessibilityEvent(e); }
    public void onActionModeFinished(ActionMode mode) { delegate.onActionModeFinished(mode); }
    public void onActionModeStarted(ActionMode mode) { delegate.onActionModeStarted(mode); }
    public void onAttachedToWindow() { delegate.onAttachedToWindow(); }
    public void onContentChanged() { delegate.onContentChanged(); }
    public boolean onCreatePanelMenu(int id, Menu menu) { return delegate.onCreatePanelMenu(id, menu); }
    public View onCreatePanelView(int id) { return delegate.onCreatePanelView(id); }
    public void onDetachedFromWindow() { delegate.onDetachedFromWindow(); }
    public boolean onMenuItemSelected(int id, MenuItem item) { return delegate.onMenuItemSelected(id, item); }
    public boolean onMenuOpened(int id, Menu menu) { return delegate.onMenuOpened(id, menu); }
    public void onPanelClosed(int id, Menu menu) { delegate.onPanelClosed(id, menu); }
    public boolean onPreparePanel(int id, View view, Menu menu) { return delegate.onPreparePanel(id, view, menu); }
    public boolean onSearchRequested() { return delegate.onSearchRequested(); }
    public boolean onSearchRequested(SearchEvent event) { return delegate.onSearchRequested(event); }
    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) { delegate.onWindowAttributesChanged(attrs); }
    public void onWindowFocusChanged(boolean focused) { delegate.onWindowFocusChanged(focused); }
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) { return delegate.onWindowStartingActionMode(callback); }
    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) { return delegate.onWindowStartingActionMode(callback, type); }
    public void onPointerCaptureChanged(boolean captured) { delegate.onPointerCaptureChanged(captured); }
    public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> groups, Menu menu, int device) { delegate.onProvideKeyboardShortcuts(groups, menu, device); }
}
