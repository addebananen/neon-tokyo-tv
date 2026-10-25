import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.lang.reflect.Method;

/**
 * Holds a key down for a while, as a thumb holds a remote's button: the down, the repeats a held
 * key sends (the first one flagged as a long press) and the up. `input keyevent` can only tap or
 * long press, and the shell may not write raw events, but it may inject them like `input` does.
 *
 * adb shell CLASSPATH=/data/local/tmp/holdkey.dex app_process /system/bin HoldKey <keycode> <ms>
 */
public final class HoldKey {
    public static void main(String[] args) throws Exception {
        int code = Integer.parseInt(args[0]);
        long holdMs = Long.parseLong(args[1]);
        Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        Method inject = managerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
        int async = 0;
        int waitForFinish = 2;

        long downAt = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(downAt, downAt, KeyEvent.ACTION_DOWN, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
        inject.invoke(manager, down, waitForFinish);
        Thread.sleep(500);
        int repeat = 0;
        long now;
        while ((now = SystemClock.uptimeMillis()) < downAt + holdMs) {
            repeat++;
            int flags = repeat == 1 ? KeyEvent.FLAG_LONG_PRESS : 0;
            inject.invoke(manager, KeyEvent.changeTimeRepeat(down, now, repeat, flags), async);
            Thread.sleep(50);
        }
        KeyEvent up = new KeyEvent(downAt, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
        inject.invoke(manager, up, waitForFinish);
        System.out.println("held key " + code + " for " + (up.getEventTime() - downAt) + " ms, " + repeat + " repeats");
    }
}
