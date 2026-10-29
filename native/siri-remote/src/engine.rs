//! Turns Siri Remote reports into Android key presses that feel like an Apple TV.
//!
//! What the Apple TV does, and so what this does:
//!
//! - **Touch scrolling.** Sliding a finger moves focus one item per stretch of travel. The
//!   stretch shrinks the faster the finger moves, so a slow slide steps item by item and a quick
//!   one races. Lifting during a quick slide flings: focus keeps gliding and slows down by
//!   itself, and touching the pad again catches it.
//! - **Clicks.** The clickpad centre is OK, the ring's four edges are the arrows (gen 2/3).
//!   A held click repeats like a held arrow on any remote.
//! - **Circling** a finger round the ring scrubs (arrow right clockwise, left anti-clockwise).
//! - **TV button**: press = Home, double press = app switcher, hold = quick settings (the
//!   Control Centre of Google TV).
//! - **Back**: press = back, hold = Home.
//! - **Power** toggles the TV; volume, mute and play/pause do what they say; Siri opens search.
//!
//! All time is in seconds since an arbitrary start (the caller passes Android's uptime).

use crate::protocol::{parse_gen1, parse_gen23, Button, Buttons, Finger, Generation, Report};

/// Android key codes used here (android.view.KeyEvent).
pub mod key {
    pub const HOME: i32 = 3;
    pub const BACK: i32 = 4;
    pub const DPAD_UP: i32 = 19;
    pub const DPAD_DOWN: i32 = 20;
    pub const DPAD_LEFT: i32 = 21;
    pub const DPAD_RIGHT: i32 = 22;
    pub const DPAD_CENTER: i32 = 23;
    pub const VOLUME_UP: i32 = 24;
    pub const VOLUME_DOWN: i32 = 25;
    pub const SEARCH: i32 = 84;
    pub const MEDIA_PLAY_PAUSE: i32 = 85;
    pub const VOLUME_MUTE: i32 = 164;
    pub const SETTINGS: i32 = 176;
    pub const APP_SWITCH: i32 = 187;
}

/// What the engine asks the TV to do.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Out {
    /// A key event. `canceled` marks an up that must not trigger the key's action.
    Key { code: i32, down: bool, repeat: u32, canceled: bool },
    /// Power button: the caller turns the TV off when it is on and on when it is off.
    PowerToggle,
    /// Siri held: start streaming the microphone.
    VoiceStart,
    VoiceStop,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Config {
    /// 1.0 = Apple TV default; higher moves focus with less travel.
    pub scroll_speed: f32,
    /// Fling after a quick slide.
    pub momentum: bool,
    /// Circling the ring scrubs (gen 2/3).
    pub ring_scrub: bool,
    /// A light tap on the pad's edge steps once in that direction.
    pub edge_taps: bool,
    pub siri_key: i32,
    pub tv_double_key: i32,
    pub tv_hold_key: i32,
    pub back_hold_home: bool,
}

impl Config {
    pub fn for_generation(gen: Generation) -> Config {
        Config {
            scroll_speed: 1.0,
            momentum: true,
            ring_scrub: gen == Generation::Gen23,
            // gen 1 has no ring, so its edges are tapped instead
            edge_taps: gen == Generation::Gen1,
            siri_key: key::SEARCH,
            tv_double_key: key::APP_SWITCH,
            tv_hold_key: key::SETTINGS,
            back_hold_home: true,
        }
    }
}

// --- Feel. Distances are in pad radii (the clickpad is the unit disc), speeds in radii/s. ---

/// Travel for one focus step at a slow, deliberate slide: about a fifth of the pad's width.
const STEP: f32 = 0.40;
/// Above this speed each step needs less travel...
const ACCEL_FROM: f32 = 0.9;
/// ...by this much per radius/s, up to MAX_GAIN times less.
const ACCEL_GAIN: f32 = 0.75;
const MAX_GAIN: f32 = 3.2;
/// Travel before the slide commits to an axis.
const AXIS_LOCK: f32 = 0.07;
/// Sideways travel, in steps, that turns a locked slide onto the other axis.
const AXIS_SWITCH_STEPS: f32 = 1.5;
/// A lift faster than this flings.
const FLING_MIN_SPEED: f32 = 1.7;
/// A finger that stood still this long before lifting does not fling.
const FLING_STALE: f64 = 0.06;
const FLING_MAX_RATE: f32 = 26.0;
/// Fling slows down with this time constant...
const FLING_TAU: f32 = 0.34;
/// ...and stops below this many steps per second.
const FLING_MIN_RATE: f32 = 2.8;
/// No touch report for this long means the finger left (a lost lift report).
const TOUCH_TIMEOUT: f64 = 0.15;
const TAP_MAX_TIME: f64 = 0.25;
const TAP_MAX_TRAVEL: f32 = 0.12;
const EDGE_TAP_RADIUS: f32 = 0.55;
/// Ring scrubbing: finger this far out, circled this far, then one arrow per SCRUB_STEP.
const RING_RADIUS: f32 = 0.70;
const SCRUB_ENTER: f32 = 0.95;
const SCRUB_STEP: f32 = 0.38;

/// Android's own key repeat (ViewConfiguration): first repeat after 400 ms, then every 50 ms.
const REPEAT_DELAY: f64 = 0.40;
const REPEAT_EVERY: f64 = 0.05;
const TV_DOUBLE_WINDOW: f64 = 0.30;
const TV_HOLD: f64 = 0.80;
const BACK_HOLD: f64 = 1.0;
/// Presses the remote buffered while disconnected arrive in a burst right after connecting.
const BURST_WINDOW: f64 = 0.45;
/// How often the caller should tick while something moves.
const FRAME: f64 = 1.0 / 120.0;

#[derive(Debug, Clone, Copy)]
struct Held {
    code: i32,
    since: f64,
    next: f64,
    repeat: u32,
    repeats: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Axis {
    X,
    Y,
}

#[derive(Debug, Clone, Copy)]
struct Touch {
    start: Finger,
    start_t: f64,
    last: Finger,
    last_t: f64,
    last_move_t: f64,
    vx: f32,
    vy: f32,
    acc_x: f32,
    acc_y: f32,
    axis: Option<Axis>,
    travel: f32,
    /// Clicked or caught a fling: this touch neither scrolls on nor taps.
    spent: bool,
    caught: bool,
    angle: f32,
    turned: f32,
    scrubbing: bool,
}

#[derive(Debug, Clone, Copy)]
struct Fling {
    code: i32,
    rate: f32,
    owed: f32,
    t: f64,
}

pub struct Engine {
    gen: Generation,
    pub cfg: Config,
    buttons: Buttons,
    held: Vec<(Button, Held)>,
    touch: Option<Touch>,
    fling: Option<Fling>,
    tv_down: Option<f64>,
    tv_hold_fired: bool,
    tv_double: bool,
    tv_single_at: Option<f64>,
    back_down: Option<f64>,
    back_hold_fired: bool,
    burst_until: Option<f64>,
    burst: Vec<(f64, Buttons)>,
    out: Vec<Out>,
}

impl Engine {
    pub fn new(gen: Generation) -> Engine {
        Engine {
            gen,
            cfg: Config::for_generation(gen),
            buttons: Buttons::default(),
            held: Vec::new(),
            touch: None,
            fling: None,
            tv_down: None,
            tv_hold_fired: false,
            tv_double: false,
            tv_single_at: None,
            back_down: None,
            back_hold_fired: false,
            burst_until: None,
            burst: Vec::new(),
            out: Vec::new(),
        }
    }

    pub fn generation(&self) -> Generation {
        self.gen
    }

    /// Takes what the engine wants done since the last call.
    pub fn drain(&mut self) -> Vec<Out> {
        std::mem::take(&mut self.out)
    }

    /// The remote's link is up and subscribed.
    pub fn connected(&mut self, t: f64) {
        self.release_all(t);
        self.burst_until = Some(t + BURST_WINDOW);
        self.burst.clear();
    }

    /// The link dropped: let go of everything so no key stays stuck down.
    pub fn disconnected(&mut self, t: f64) {
        self.release_all(t);
        self.burst_until = None;
        self.burst.clear();
    }

    /// One notification. `report_id` is the Report Reference id (ignored for gen 1).
    pub fn report(&mut self, report_id: u8, data: &[u8], t: f64) {
        let report = match self.gen {
            Generation::Gen23 => parse_gen23(report_id, data),
            Generation::Gen1 => parse_gen1(data),
        };
        match report {
            Report::Buttons(b) => self.buttons_report(b, t),
            Report::Touch(f) => self.touch_report(&f, t),
            Report::TouchAndButtons(f, b) => {
                self.buttons_report(b, t);
                self.touch_report(&f, t);
            }
            Report::Audio | Report::Unknown => {}
        }
    }

    /// Advances timers (repeats, holds, flings). Returns seconds until the next tick is due,
    /// or `None` when nothing is pending.
    pub fn tick(&mut self, t: f64) -> Option<f64> {
        if let Some(until) = self.burst_until {
            if t >= until {
                self.end_burst(t);
            }
        }
        // key repeat
        for (_, h) in self.held.iter_mut() {
            if h.repeats && t >= h.next {
                h.repeat += 1;
                h.next = (h.next + REPEAT_EVERY).max(t);
                self.out.push(Out::Key { code: h.code, down: true, repeat: h.repeat, canceled: false });
            }
        }
        // TV: single press resolves once no second press came
        if let Some(at) = self.tv_single_at {
            if t >= at {
                self.tv_single_at = None;
                self.click(key::HOME);
            }
        }
        if let Some(down) = self.tv_down {
            if !self.tv_hold_fired && !self.tv_double && t - down >= TV_HOLD {
                self.tv_hold_fired = true;
                self.click(self.cfg.tv_hold_key);
            }
        }
        if let Some(down) = self.back_down {
            if self.cfg.back_hold_home && !self.back_hold_fired && t - down >= BACK_HOLD {
                self.back_hold_fired = true;
                self.release_button(Button::Back, true);
                self.click(key::HOME);
            }
        }
        if let Some(touch) = self.touch {
            if t - touch.last_t >= TOUCH_TIMEOUT {
                self.lift(t, false);
            }
        }
        self.advance_fling(t);
        self.next_due(t)
    }

    fn next_due(&self, t: f64) -> Option<f64> {
        let mut due: Option<f64> = None;
        let mut at = |when: f64| {
            let d = (when - t).max(0.0);
            due = Some(due.map_or(d, |x: f64| x.min(d)));
        };
        if let Some(u) = self.burst_until {
            at(u);
        }
        for (_, h) in &self.held {
            if h.repeats {
                at(h.next);
            }
        }
        if let Some(s) = self.tv_single_at {
            at(s);
        }
        if let Some(d) = self.tv_down {
            if !self.tv_hold_fired && !self.tv_double {
                at(d + TV_HOLD);
            }
        }
        if let Some(d) = self.back_down {
            if self.cfg.back_hold_home && !self.back_hold_fired {
                at(d + BACK_HOLD);
            }
        }
        if let Some(touch) = self.touch {
            at(touch.last_t + TOUCH_TIMEOUT);
        }
        if self.fling.is_some() {
            at(t + FRAME);
        }
        due
    }

    // ------------------------------------------------------------------ buttons

    fn buttons_report(&mut self, now: Buttons, t: f64) {
        if self.burst_until.is_some() {
            self.burst.push((t, now));
            return;
        }
        self.apply_buttons(now, t);
    }

    /// Of the presses buffered while disconnected keep only the last one: the press that woke
    /// the remote. Older ones (maybe from hours ago) are dropped.
    fn end_burst(&mut self, t: f64) {
        self.burst_until = None;
        let queued = std::mem::take(&mut self.burst);
        let mut from = None;
        let mut prev = Buttons::default();
        for (i, (_, b)) in queued.iter().enumerate() {
            if prev.0 == 0 && b.0 != 0 {
                from = Some(i);
            }
            prev = *b;
        }
        if let Some(i) = from {
            for (_, b) in &queued[i..] {
                self.apply_buttons(*b, t);
            }
        }
    }

    fn apply_buttons(&mut self, now: Buttons, t: f64) {
        let before = self.buttons;
        self.buttons = now;
        for b in Button::ALL {
            match (before.has(b), now.has(b)) {
                (false, true) => self.press(b, t),
                (true, false) => self.release(b, t),
                _ => {}
            }
        }
    }

    fn press(&mut self, b: Button, t: f64) {
        // a click ends scrolling for this touch, and catches a fling
        if matches!(
            b,
            Button::Select | Button::RingUp | Button::RingDown | Button::RingLeft | Button::RingRight
        ) {
            self.fling = None;
            if let Some(touch) = self.touch.as_mut() {
                touch.spent = true;
            }
        }
        match b {
            Button::Select => self.hold(b, key::DPAD_CENTER, true, t),
            Button::RingUp => self.hold(b, key::DPAD_UP, true, t),
            Button::RingDown => self.hold(b, key::DPAD_DOWN, true, t),
            Button::RingLeft => self.hold(b, key::DPAD_LEFT, true, t),
            Button::RingRight => self.hold(b, key::DPAD_RIGHT, true, t),
            Button::VolumeUp => self.hold(b, key::VOLUME_UP, true, t),
            Button::VolumeDown => self.hold(b, key::VOLUME_DOWN, true, t),
            Button::PlayPause => self.hold(b, key::MEDIA_PLAY_PAUSE, false, t),
            Button::Mute => self.hold(b, key::VOLUME_MUTE, false, t),
            Button::Siri => {
                // 0: no key, the voice search takes the press
                if self.cfg.siri_key != 0 {
                    self.hold(b, self.cfg.siri_key, false, t);
                }
                self.out.push(Out::VoiceStart);
            }
            Button::Power => self.out.push(Out::PowerToggle),
            Button::Back => {
                self.back_down = Some(t);
                self.back_hold_fired = false;
                self.hold(b, key::BACK, false, t);
            }
            Button::Tv => {
                self.tv_hold_fired = false;
                if self.tv_single_at.take().is_some() {
                    self.tv_double = true;
                    self.click(self.cfg.tv_double_key);
                } else {
                    self.tv_double = false;
                }
                self.tv_down = Some(t);
            }
        }
    }

    fn release(&mut self, b: Button, t: f64) {
        match b {
            Button::Tv => {
                if !self.tv_hold_fired && !self.tv_double {
                    self.tv_single_at = Some(t + TV_DOUBLE_WINDOW);
                }
                self.tv_down = None;
                self.tv_double = false;
            }
            Button::Back => {
                self.back_down = None;
                self.release_button(b, false);
            }
            Button::Siri => {
                self.release_button(b, false);
                self.out.push(Out::VoiceStop);
            }
            Button::Power => {}
            _ => self.release_button(b, false),
        }
    }

    fn hold(&mut self, b: Button, code: i32, repeats: bool, t: f64) {
        self.out.push(Out::Key { code, down: true, repeat: 0, canceled: false });
        self.held.retain(|(x, _)| *x != b);
        self.held.push((b, Held { code, since: t, next: t + REPEAT_DELAY, repeat: 0, repeats }));
    }

    fn release_button(&mut self, b: Button, canceled: bool) {
        if let Some(i) = self.held.iter().position(|(x, _)| *x == b) {
            let (_, h) = self.held.remove(i);
            self.out.push(Out::Key { code: h.code, down: false, repeat: 0, canceled });
        }
    }

    fn click(&mut self, code: i32) {
        self.out.push(Out::Key { code, down: true, repeat: 0, canceled: false });
        self.out.push(Out::Key { code, down: false, repeat: 0, canceled: false });
    }

    fn release_all(&mut self, t: f64) {
        for (b, _) in self.held.clone() {
            self.release_button(b, true);
        }
        self.buttons = Buttons::default();
        self.touch = None;
        self.fling = None;
        self.tv_down = None;
        self.tv_single_at = None;
        self.back_down = None;
        let _ = t;
    }

    /// How long a button has been held, for tests and diagnostics.
    pub fn held_for(&self, b: Button, t: f64) -> Option<f64> {
        self.held.iter().find(|(x, _)| *x == b).map(|(_, h)| t - h.since)
    }

    // ------------------------------------------------------------------ touch

    fn touch_report(&mut self, fingers: &[Finger], t: f64) {
        if self.burst_until.is_some() {
            return;
        }
        let Some(f) = fingers.first().copied() else {
            self.lift(t, true);
            return;
        };
        let Some(mut touch) = self.touch else {
            let caught = self.fling.take().is_some();
            self.touch = Some(Touch {
                start: f,
                start_t: t,
                last: f,
                last_t: t,
                last_move_t: t,
                vx: 0.0,
                vy: 0.0,
                acc_x: 0.0,
                acc_y: 0.0,
                axis: None,
                travel: 0.0,
                spent: caught,
                caught,
                angle: f.y.atan2(f.x),
                turned: 0.0,
                scrubbing: false,
            });
            return;
        };

        let dx = f.x - touch.last.x;
        let dy = f.y - touch.last.y;
        let dt = ((t - touch.last_t) as f32).clamp(0.004, 0.05);
        let moved = (dx * dx + dy * dy).sqrt();
        touch.travel += moved;
        if moved > 0.004 {
            touch.last_move_t = t;
        }
        // smoothed velocity; reports come every 15 ms
        touch.vx = 0.55 * (dx / dt) + 0.45 * touch.vx;
        touch.vy = 0.55 * (dy / dt) + 0.45 * touch.vy;
        touch.last = f;
        touch.last_t = t;

        // two fingers: no scrolling (Apple TV ignores them too)
        if fingers.len() > 1 || touch.spent {
            self.touch = Some(touch);
            return;
        }

        if self.cfg.ring_scrub && self.ring_scrub(&mut touch, f) {
            self.touch = Some(touch);
            return;
        }

        let speed = (touch.vx * touch.vx + touch.vy * touch.vy).sqrt();
        let gain = (1.0 + ACCEL_GAIN * (speed - ACCEL_FROM).max(0.0)).min(MAX_GAIN);
        touch.acc_x += dx * gain;
        touch.acc_y += dy * gain;
        let step = STEP / self.cfg.scroll_speed.clamp(0.4, 2.5);

        if touch.axis.is_none() {
            let (ax, ay) = (touch.acc_x.abs(), touch.acc_y.abs());
            if ax.max(ay) >= AXIS_LOCK {
                touch.axis = Some(if ax >= ay { Axis::X } else { Axis::Y });
            }
        }
        // sideways drift along a locked slide fades, so a slanted slide stays on its axis
        // while a real turn (an L) still switches
        match touch.axis {
            Some(Axis::X) => touch.acc_y -= touch.acc_y.signum() * touch.acc_y.abs().min(0.5 * (dx * gain).abs()),
            Some(Axis::Y) => touch.acc_x -= touch.acc_x.signum() * touch.acc_x.abs().min(0.5 * (dy * gain).abs()),
            None => {}
        }
        match touch.axis {
            Some(Axis::X) if touch.acc_y.abs() >= AXIS_SWITCH_STEPS * step => {
                touch.axis = Some(Axis::Y);
                touch.acc_x = 0.0;
            }
            Some(Axis::Y) if touch.acc_x.abs() >= AXIS_SWITCH_STEPS * step => {
                touch.axis = Some(Axis::X);
                touch.acc_y = 0.0;
            }
            _ => {}
        }
        let mut steps = 0;
        while steps < 3 {
            match touch.axis {
                Some(Axis::X) if touch.acc_x.abs() >= step => {
                    let s = touch.acc_x.signum();
                    touch.acc_x -= s * step;
                    self.click(if s > 0.0 { key::DPAD_RIGHT } else { key::DPAD_LEFT });
                }
                Some(Axis::Y) if touch.acc_y.abs() >= step => {
                    let s = touch.acc_y.signum();
                    touch.acc_y -= s * step;
                    self.click(if s > 0.0 { key::DPAD_UP } else { key::DPAD_DOWN });
                }
                _ => break,
            }
            steps += 1;
        }
        self.touch = Some(touch);
    }

    /// Circling on the ring scrubs. Returns true while this touch is scrubbing.
    fn ring_scrub(&mut self, touch: &mut Touch, f: Finger) -> bool {
        let r = (f.x * f.x + f.y * f.y).sqrt();
        let angle = f.y.atan2(f.x);
        if r < RING_RADIUS {
            touch.turned = 0.0;
            touch.angle = angle;
            return touch.scrubbing;
        }
        let mut d = angle - touch.angle;
        if d > std::f32::consts::PI {
            d -= 2.0 * std::f32::consts::PI;
        } else if d < -std::f32::consts::PI {
            d += 2.0 * std::f32::consts::PI;
        }
        touch.angle = angle;
        touch.turned += d;
        if !touch.scrubbing {
            if touch.turned.abs() < SCRUB_ENTER {
                return false;
            }
            touch.scrubbing = true;
            touch.turned = touch.turned.signum() * (touch.turned.abs() - SCRUB_ENTER + SCRUB_STEP);
            touch.acc_x = 0.0;
            touch.acc_y = 0.0;
        }
        while touch.turned.abs() >= SCRUB_STEP {
            let s = touch.turned.signum();
            touch.turned -= s * SCRUB_STEP;
            // angles grow anti-clockwise; clockwise moves forward like the Apple TV
            self.click(if s < 0.0 { key::DPAD_RIGHT } else { key::DPAD_LEFT });
        }
        true
    }

    fn lift(&mut self, t: f64, reported: bool) {
        let Some(touch) = self.touch.take() else { return };
        if touch.spent || touch.scrubbing {
            return;
        }
        let quick = t - touch.start_t <= TAP_MAX_TIME && touch.travel <= TAP_MAX_TRAVEL;
        if quick && self.cfg.edge_taps && !touch.caught {
            let s = touch.start;
            if (s.x * s.x + s.y * s.y).sqrt() >= EDGE_TAP_RADIUS {
                let code = if s.x.abs() >= s.y.abs() {
                    if s.x > 0.0 { key::DPAD_RIGHT } else { key::DPAD_LEFT }
                } else if s.y > 0.0 {
                    key::DPAD_UP
                } else {
                    key::DPAD_DOWN
                };
                self.click(code);
            }
            return;
        }
        if !self.cfg.momentum || !reported || t - touch.last_move_t > FLING_STALE {
            return;
        }
        let (v, code) = match touch.axis {
            Some(Axis::X) => (touch.vx, if touch.vx > 0.0 { key::DPAD_RIGHT } else { key::DPAD_LEFT }),
            Some(Axis::Y) => (touch.vy, if touch.vy > 0.0 { key::DPAD_UP } else { key::DPAD_DOWN }),
            None => return,
        };
        let speed = v.abs();
        if speed < FLING_MIN_SPEED {
            return;
        }
        let gain = (1.0 + ACCEL_GAIN * (speed - ACCEL_FROM).max(0.0)).min(MAX_GAIN);
        let step = STEP / self.cfg.scroll_speed.clamp(0.4, 2.5);
        let rate = (speed * gain / step).min(FLING_MAX_RATE);
        // what was left over of the last stretch counts towards the first glide step
        let left = match touch.axis {
            Some(Axis::X) => touch.acc_x.abs() / step,
            _ => touch.acc_y.abs() / step,
        };
        self.fling = Some(Fling { code, rate, owed: left.min(0.9), t });
    }

    fn advance_fling(&mut self, t: f64) {
        let Some(mut f) = self.fling else { return };
        let dt = (t - f.t).max(0.0) as f32;
        if dt <= 0.0 {
            return;
        }
        // integral of rate * e^(-s/tau) over dt
        let decay = (-dt / FLING_TAU).exp();
        f.owed += f.rate * FLING_TAU * (1.0 - decay);
        f.rate *= decay;
        f.t = t;
        let mut n = 0;
        while f.owed >= 1.0 && n < 4 {
            f.owed -= 1.0;
            self.click(f.code);
            n += 1;
        }
        self.fling = if f.rate < FLING_MIN_RATE { None } else { Some(f) };
    }

    /// A finger on the pad, already decoded (for the browser simulator).
    pub fn touch_finger(&mut self, f: Finger, t: f64) {
        self.touch_report(&[f], t);
    }

    /// The finger left the pad (for the browser simulator).
    pub fn touch_lift(&mut self, t: f64) {
        self.touch_report(&[], t);
    }

    pub fn flinging(&self) -> bool {
        self.fling.is_some()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::REPORT_BUTTONS;

    fn keys(out: &[Out]) -> Vec<(i32, bool)> {
        out.iter()
            .filter_map(|o| match o {
                Out::Key { code, down, .. } => Some((*code, *down)),
                _ => None,
            })
            .collect()
    }

    fn downs(out: &[Out], code: i32) -> usize {
        out.iter()
            .filter(|o| matches!(o, Out::Key { code: c, down: true, repeat: 0, .. } if *c == code))
            .count()
    }

    fn ready() -> Engine {
        let mut e = Engine::new(Generation::Gen23);
        e.connected(0.0);
        e.tick(1.0);
        e.drain();
        e
    }

    fn buttons(e: &mut Engine, mask: u16, t: f64) {
        e.report(REPORT_BUTTONS, &mask.to_le_bytes(), t);
    }

    /// Slides one finger from (x0,y0) to (x1,y1) over `secs`, a report every 15 ms.
    fn slide(e: &mut Engine, from: (f32, f32), to: (f32, f32), t0: f64, secs: f64, lift: bool) -> f64 {
        let n = (secs / 0.015).ceil() as usize;
        let mut t = t0;
        for i in 0..=n {
            let a = i as f32 / n as f32;
            let f = Finger { x: from.0 + (to.0 - from.0) * a, y: from.1 + (to.1 - from.1) * a };
            e.touch_report(&[f], t);
            e.tick(t);
            t += 0.015;
        }
        if lift {
            e.touch_report(&[], t);
        }
        t
    }

    fn run(e: &mut Engine, from: f64, to: f64) {
        let mut t = from;
        while t < to {
            e.tick(t);
            t += FRAME;
        }
    }

    #[test]
    fn click_is_ok_and_repeats_when_held() {
        let mut e = ready();
        buttons(&mut e, 0x0008, 1.0);
        assert_eq!(keys(&e.drain()), vec![(key::DPAD_CENTER, true)]);
        run(&mut e, 1.0, 1.6);
        let out = e.drain();
        let repeats = out.iter().filter(|o| matches!(o, Out::Key { repeat, .. } if *repeat > 0)).count();
        assert!((3..=5).contains(&repeats), "{repeats}");
        buttons(&mut e, 0, 1.6);
        assert_eq!(keys(&e.drain()), vec![(key::DPAD_CENTER, false)]);
    }

    #[test]
    fn ring_edges_are_arrows() {
        let mut e = ready();
        for (mask, code) in [
            (0x0200, key::DPAD_UP),
            (0x0400, key::DPAD_RIGHT),
            (0x0800, key::DPAD_DOWN),
            (0x1000, key::DPAD_LEFT),
        ] {
            buttons(&mut e, mask, 1.0);
            buttons(&mut e, 0, 1.05);
            assert_eq!(keys(&e.drain()), vec![(code, true), (code, false)]);
        }
    }

    #[test]
    fn slow_slide_steps_item_by_item() {
        let mut e = ready();
        // a full pad width (2 radii), slowly over two and a half seconds: about five items
        let t = slide(&mut e, (-1.0, 0.0), (1.0, 0.0), 1.0, 2.5, true);
        run(&mut e, t, t + 1.0);
        let out = e.drain();
        let n = downs(&out, key::DPAD_RIGHT);
        assert!((4..=6).contains(&n), "{n}");
        assert_eq!(downs(&out, key::DPAD_UP) + downs(&out, key::DPAD_DOWN), 0);
    }

    #[test]
    fn quick_flick_glides_and_slows_down() {
        let mut e = ready();
        let t = slide(&mut e, (0.0, -0.6), (0.0, 0.6), 1.0, 0.12, true);
        let during = downs(&e.drain(), key::DPAD_UP);
        let mut times = vec![];
        let mut s = t;
        while s < t + 2.0 {
            e.tick(s);
            for o in e.drain() {
                if matches!(o, Out::Key { code: key::DPAD_UP, down: true, .. }) {
                    times.push(s);
                }
            }
            s += FRAME;
        }
        assert!(times.len() >= 4, "glides on: {during} during, {} after", times.len());
        let gaps: Vec<f64> = times.windows(2).map(|w| w[1] - w[0]).collect();
        assert!(gaps.last().unwrap() > gaps.first().unwrap(), "slows down: {gaps:?}");
        assert!(!e.flinging(), "stops by itself");
    }

    #[test]
    fn touching_again_catches_the_glide() {
        let mut e = ready();
        let t = slide(&mut e, (0.0, -0.6), (0.0, 0.6), 1.0, 0.12, true);
        run(&mut e, t, t + 0.1);
        assert!(e.flinging());
        e.touch_report(&[Finger { x: 0.0, y: 0.0 }], t + 0.1);
        assert!(!e.flinging());
        e.drain();
        e.touch_report(&[], t + 0.2);
        run(&mut e, t + 0.2, t + 1.0);
        assert!(keys(&e.drain()).is_empty(), "the catch is not a tap");
    }

    #[test]
    fn a_resting_finger_does_not_fling() {
        let mut e = ready();
        let t = slide(&mut e, (-0.8, 0.0), (0.8, 0.0), 1.0, 0.15, false);
        // stays still 200 ms, then lifts
        e.touch_report(&[Finger { x: 0.8, y: 0.0 }], t + 0.1);
        e.touch_report(&[Finger { x: 0.8, y: 0.0 }], t + 0.2);
        e.touch_report(&[], t + 0.21);
        e.drain();
        run(&mut e, t + 0.21, t + 1.0);
        assert!(keys(&e.drain()).is_empty());
    }

    #[test]
    fn faster_slides_need_less_travel() {
        let mut slow = ready();
        slide(&mut slow, (0.0, -0.8), (0.0, 0.8), 1.0, 1.2, false);
        let mut fast = ready();
        slide(&mut fast, (0.0, -0.8), (0.0, 0.8), 1.0, 0.25, false);
        assert!(downs(&fast.drain(), key::DPAD_UP) > downs(&slow.drain(), key::DPAD_UP));
    }

    #[test]
    fn a_diagonal_moves_along_one_axis() {
        let mut e = ready();
        slide(&mut e, (-0.8, -0.2), (0.8, 0.2), 1.0, 0.8, true);
        let out = e.drain();
        assert!(downs(&out, key::DPAD_RIGHT) >= 3);
        assert_eq!(downs(&out, key::DPAD_UP), 0);
    }

    #[test]
    fn an_l_shaped_slide_turns() {
        let mut e = ready();
        let t = slide(&mut e, (-0.8, -0.6), (0.4, -0.6), 1.0, 0.9, false);
        slide(&mut e, (0.4, -0.6), (0.4, 0.8), t, 0.9, true);
        let out = e.drain();
        assert!(downs(&out, key::DPAD_RIGHT) >= 2);
        assert!(downs(&out, key::DPAD_UP) >= 2);
    }

    #[test]
    fn clicking_while_touching_does_not_scroll() {
        let mut e = ready();
        e.touch_report(&[Finger { x: 0.0, y: 0.0 }], 1.0);
        buttons(&mut e, 0x0008, 1.02);
        e.touch_report(&[Finger { x: 0.3, y: 0.0 }], 1.04);
        e.touch_report(&[Finger { x: 0.6, y: 0.0 }], 1.06);
        buttons(&mut e, 0, 1.1);
        e.touch_report(&[], 1.12);
        run(&mut e, 1.12, 2.0);
        assert_eq!(keys(&e.drain()), vec![(key::DPAD_CENTER, true), (key::DPAD_CENTER, false)]);
    }

    #[test]
    fn circling_the_ring_scrubs() {
        let mut e = ready();
        let mut t = 1.0;
        // clockwise from 12 o'clock half way round
        for i in 0..=30 {
            let a = std::f32::consts::FRAC_PI_2 - std::f32::consts::PI * i as f32 / 30.0;
            e.touch_report(&[Finger { x: 0.9 * a.cos(), y: 0.9 * a.sin() }], t);
            t += 0.015;
        }
        e.touch_report(&[], t);
        let out = e.drain();
        assert!(downs(&out, key::DPAD_RIGHT) >= 3, "{:?}", keys(&out));
        assert_eq!(downs(&out, key::DPAD_LEFT), 0);
    }

    #[test]
    fn tv_press_double_and_hold() {
        let mut e = ready();
        buttons(&mut e, 0x0001, 1.0);
        buttons(&mut e, 0, 1.1);
        run(&mut e, 1.1, 1.3);
        assert!(keys(&e.drain()).is_empty(), "waits for a second press");
        run(&mut e, 1.3, 1.5);
        assert_eq!(keys(&e.drain()), vec![(key::HOME, true), (key::HOME, false)]);

        buttons(&mut e, 0x0001, 2.0);
        buttons(&mut e, 0, 2.08);
        buttons(&mut e, 0x0001, 2.2);
        buttons(&mut e, 0, 2.3);
        run(&mut e, 2.3, 3.0);
        assert_eq!(keys(&e.drain()), vec![(key::APP_SWITCH, true), (key::APP_SWITCH, false)]);

        buttons(&mut e, 0x0001, 4.0);
        run(&mut e, 4.0, 5.0);
        buttons(&mut e, 0, 5.0);
        run(&mut e, 5.0, 6.0);
        assert_eq!(keys(&e.drain()), vec![(key::SETTINGS, true), (key::SETTINGS, false)]);
    }

    #[test]
    fn back_press_and_hold_for_home() {
        let mut e = ready();
        buttons(&mut e, 0x0040, 1.0);
        buttons(&mut e, 0, 1.1);
        assert_eq!(keys(&e.drain()), vec![(key::BACK, true), (key::BACK, false)]);
        buttons(&mut e, 0x0040, 2.0);
        run(&mut e, 2.0, 3.2);
        buttons(&mut e, 0, 3.2);
        let out = e.drain();
        assert!(out.contains(&Out::Key { code: key::BACK, down: false, repeat: 0, canceled: true }));
        assert_eq!(downs(&out, key::HOME), 1);
    }

    #[test]
    fn media_volume_power_siri() {
        let mut e = ready();
        buttons(&mut e, 0x0100, 1.0);
        buttons(&mut e, 0, 1.1);
        buttons(&mut e, 0x0080, 1.2);
        buttons(&mut e, 0, 1.3);
        buttons(&mut e, 0x0002, 1.4);
        buttons(&mut e, 0, 1.5);
        buttons(&mut e, 0x0010, 1.6);
        buttons(&mut e, 0, 1.7);
        buttons(&mut e, 0x0020, 1.8);
        buttons(&mut e, 0, 1.9);
        let out = e.drain();
        assert_eq!(downs(&out, key::MEDIA_PLAY_PAUSE), 1);
        assert_eq!(downs(&out, key::VOLUME_MUTE), 1);
        assert_eq!(downs(&out, key::VOLUME_UP), 1);
        assert_eq!(downs(&out, key::SEARCH), 1);
        assert!(out.contains(&Out::PowerToggle));
        assert!(out.contains(&Out::VoiceStart) && out.contains(&Out::VoiceStop));
    }

    #[test]
    fn buffered_presses_keep_only_the_waking_one() {
        let mut e = Engine::new(Generation::Gen23);
        e.connected(10.0);
        // three old presses and the one that woke the remote, 15 ms apart
        let mut t = 10.05;
        for mask in [0x0040u16, 0x0100, 0x0002, 0x0008] {
            buttons(&mut e, mask, t);
            buttons(&mut e, 0, t + 0.015);
            t += 0.03;
        }
        run(&mut e, 10.05, 11.0);
        assert_eq!(keys(&e.drain()), vec![(key::DPAD_CENTER, true), (key::DPAD_CENTER, false)]);
    }

    #[test]
    fn a_dropped_link_releases_held_keys() {
        let mut e = ready();
        buttons(&mut e, 0x0004, 1.0);
        e.disconnected(1.2);
        let out = e.drain();
        assert!(out.contains(&Out::Key { code: key::VOLUME_DOWN, down: false, repeat: 0, canceled: true }));
        assert_eq!(e.tick(2.0), None, "nothing left running");
    }

    #[test]
    fn gen1_edge_tap_steps_once() {
        let mut e = Engine::new(Generation::Gen1);
        e.connected(0.0);
        e.tick(1.0);
        e.touch_report(&[Finger { x: 0.8, y: 0.05 }], 1.0);
        e.touch_report(&[Finger { x: 0.81, y: 0.05 }], 1.05);
        e.touch_report(&[], 1.1);
        assert_eq!(keys(&e.drain()), vec![(key::DPAD_RIGHT, true), (key::DPAD_RIGHT, false)]);
    }

    #[test]
    fn idle_engine_needs_no_ticks() {
        let mut e = ready();
        assert_eq!(e.tick(5.0), None);
        e.touch_report(&[Finger { x: 0.0, y: 0.0 }], 5.0);
        assert!(e.tick(5.0).is_some());
    }
}
