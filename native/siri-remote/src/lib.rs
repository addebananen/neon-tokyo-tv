//! Apple Siri Remote support for Neon Tokyo.
//!
//! The Kotlin side (`com.lumera.app.siri`) holds the Bluetooth link and injects keys; this
//! library decodes what the remote sends and decides which keys that is, with the Apple TV's
//! touch scrolling. See [`engine`] for the behaviour and [`protocol`] for the wire format.

pub mod engine;
pub mod protocol;

use engine::{Engine, Out};
use jni::objects::{JByteArray, JClass};
use jni::sys::{jboolean, jfloat, jint, jintArray, jlong};
use jni::JNIEnv;
use protocol::Generation;

/// Flattens the engine's output for Java: `[nextTickMs or -1, (kind, code, repeat, flags)*]`.
/// kind: 0 key down, 1 key up, 2 power toggle, 3 voice start, 4 voice stop. flags: 1 canceled.
fn encode(due: Option<f64>, outs: &[Out]) -> Vec<i32> {
    let mut v = Vec::with_capacity(1 + outs.len() * 4);
    v.push(due.map_or(-1, |d| (d * 1000.0).ceil() as i32));
    for o in outs {
        let g = match *o {
            Out::Key { code, down, repeat, canceled } => {
                [if down { 0 } else { 1 }, code, repeat as i32, canceled as i32]
            }
            Out::PowerToggle => [2, 0, 0, 0],
            Out::VoiceStart => [3, 0, 0, 0],
            Out::VoiceStop => [4, 0, 0, 0],
        };
        v.extend_from_slice(&g);
    }
    v
}

fn secs(ms: jlong) -> f64 {
    ms as f64 / 1000.0
}

/// # Safety
/// `h` must come from `nativeCreate` and not be destroyed.
unsafe fn engine<'a>(h: jlong) -> &'a mut Engine {
    &mut *(h as *mut Engine)
}

fn to_java(env: &mut JNIEnv, v: Vec<i32>) -> jintArray {
    match env.new_int_array(v.len() as i32) {
        Ok(arr) => {
            let _ = env.set_int_array_region(&arr, 0, &v);
            arr.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

fn step(env: &mut JNIEnv, e: &mut Engine, t: f64) -> jintArray {
    let due = e.tick(t);
    let outs = e.drain();
    to_java(env, encode(due, &outs))
}

#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeCreate(
    _env: JNIEnv,
    _class: JClass,
    gen: jint,
) -> jlong {
    let gen = if gen == 1 { Generation::Gen1 } else { Generation::Gen23 };
    Box::into_raw(Box::new(Engine::new(gen))) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    h: jlong,
) {
    if h != 0 {
        drop(unsafe { Box::from_raw(h as *mut Engine) });
    }
}

#[no_mangle]
#[allow(clippy::too_many_arguments)]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeConfigure(
    _env: JNIEnv,
    _class: JClass,
    h: jlong,
    scroll_speed: jfloat,
    momentum: jboolean,
    ring_scrub: jboolean,
    edge_taps: jboolean,
    back_hold_home: jboolean,
    siri_key: jint,
    tv_double_key: jint,
    tv_hold_key: jint,
) {
    let e = unsafe { engine(h) };
    e.cfg.scroll_speed = scroll_speed;
    e.cfg.momentum = momentum != 0;
    e.cfg.ring_scrub = ring_scrub != 0;
    e.cfg.edge_taps = edge_taps != 0;
    e.cfg.back_hold_home = back_hold_home != 0;
    e.cfg.siri_key = siri_key;
    e.cfg.tv_double_key = tv_double_key;
    e.cfg.tv_hold_key = tv_hold_key;
}

#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeConnected(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
    t: jlong,
) -> jintArray {
    let e = unsafe { engine(h) };
    e.connected(secs(t));
    step(&mut env, e, secs(t))
}

#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeDisconnected(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
    t: jlong,
) -> jintArray {
    let e = unsafe { engine(h) };
    e.disconnected(secs(t));
    step(&mut env, e, secs(t))
}

#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeReport(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
    report_id: jint,
    data: JByteArray,
    t: jlong,
) -> jintArray {
    let e = unsafe { engine(h) };
    let bytes = env.convert_byte_array(&data).unwrap_or_default();
    e.report(report_id as u8, &bytes, secs(t));
    step(&mut env, e, secs(t))
}

#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeTick(
    mut env: JNIEnv,
    _class: JClass,
    h: jlong,
    t: jlong,
) -> jintArray {
    let e = unsafe { engine(h) };
    step(&mut env, e, secs(t))
}

/// The Opus frame in a microphone report, prefixed with its sequence number (2 bytes, little
/// endian), or null when the report is not audio.
#[no_mangle]
pub extern "system" fn Java_com_lumera_app_siri_SiriNative_nativeAudioFrame<'a>(
    env: JNIEnv<'a>,
    _class: JClass,
    data: JByteArray,
) -> jni::sys::jbyteArray {
    let bytes = env.convert_byte_array(&data).unwrap_or_default();
    match protocol::parse_audio(&bytes) {
        Some((seq, frame)) => {
            let mut v = seq.to_le_bytes().to_vec();
            v.extend_from_slice(frame);
            env.byte_array_from_slice(&v).map(|a| a.into_raw()).unwrap_or(std::ptr::null_mut())
        }
        None => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_keys_and_due() {
        let v = encode(
            Some(0.0101),
            &[Out::Key { code: 23, down: true, repeat: 0, canceled: false }, Out::PowerToggle],
        );
        assert_eq!(v, vec![11, 0, 23, 0, 0, 2, 0, 0, 0]);
        assert_eq!(encode(None, &[])[0], -1);
    }
}

/// The same engine for the browser, so the scrolling can be felt on a phone before the TV.
#[cfg(target_arch = "wasm32")]
pub mod wasm {
    use super::*;
    use protocol::Finger;

    static mut ENGINE: Option<Engine> = None;
    static mut OUT: [i32; 256] = [0; 256];

    #[allow(static_mut_refs)]
    fn e() -> &'static mut Engine {
        unsafe { ENGINE.get_or_insert_with(|| Engine::new(Generation::Gen23)) }
    }

    #[allow(static_mut_refs)]
    fn flush(t: f64) -> *const i32 {
        let due = e().tick(t);
        let v = encode(due, &e().drain());
        unsafe {
            OUT[0] = v.len().min(255) as i32;
            for (i, x) in v.iter().take(255).enumerate() {
                OUT[i + 1] = *x;
            }
            OUT.as_ptr()
        }
    }

    #[no_mangle]
    pub extern "C" fn sim_reset(t: f64, speed: f32, momentum: i32) -> *const i32 {
        unsafe { ENGINE = Some(Engine::new(Generation::Gen23)) };
        e().cfg.scroll_speed = speed;
        e().cfg.momentum = momentum != 0;
        e().connected(t - 1.0);
        flush(t)
    }

    /// One finger at (x, y) in pad radii, or lifted.
    #[no_mangle]
    pub extern "C" fn sim_touch(x: f32, y: f32, down: i32, t: f64) -> *const i32 {
        if down != 0 {
            e().touch_finger(Finger { x, y }, t);
        } else {
            e().touch_lift(t);
        }
        flush(t)
    }

    /// The gen 2/3 button mask, as the remote sends it.
    #[no_mangle]
    pub extern "C" fn sim_buttons(mask: i32, t: f64) -> *const i32 {
        e().report(protocol::REPORT_BUTTONS, &(mask as u16).to_le_bytes(), t);
        flush(t)
    }

    #[no_mangle]
    pub extern "C" fn sim_tick(t: f64) -> *const i32 {
        flush(t)
    }
}
