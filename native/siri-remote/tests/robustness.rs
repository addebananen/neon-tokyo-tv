//! The bridge runs with panic=abort, so a panic here would take the remote away until the TV
//! restarts. These throw millions of random, broken and badly timed reports at the engine and
//! check what must always hold: nothing panics, no key is left stuck down, every up follows a
//! down, timers stay sane, and nothing moves once the remote is gone.

use neon_siri::engine::{key, Engine, Out};
use neon_siri::protocol::{parse_audio, parse_gen1, parse_gen23, Generation};
use std::collections::HashMap;

/// xorshift64*: deterministic, so a failure can be replayed from its seed.
struct Rng(u64);
impl Rng {
    fn next(&mut self) -> u64 {
        self.0 ^= self.0 >> 12;
        self.0 ^= self.0 << 25;
        self.0 ^= self.0 >> 27;
        self.0.wrapping_mul(0x2545F4914F6CDD1D)
    }
    fn below(&mut self, n: u64) -> u64 {
        self.next() % n.max(1)
    }
    fn chance(&mut self, percent: u64) -> bool {
        self.below(100) < percent
    }
    fn bytes_upto(&mut self, max: u64) -> Vec<u8> {
        let n = self.below(max) as usize;
        self.bytes(n)
    }
    fn bytes(&mut self, n: usize) -> Vec<u8> {
        (0..n).map(|_| self.next() as u8).collect()
    }
}

/// Which keys are down, as Android would see them.
#[derive(Default)]
struct Keys {
    down: HashMap<i32, u32>,
    events: usize,
    /// Voice searches open: 0 or 1.
    voice: i32,
}

impl Keys {
    fn apply(&mut self, outs: &[Out], seed: u64) {
        for o in outs {
            match o {
                Out::VoiceStart => self.voice += 1,
                Out::VoiceStop => self.voice -= 1,
                _ => {}
            }
            assert!((0..=1).contains(&self.voice), "seed {seed}: voice start/stop out of step ({})", self.voice);
            if let Out::Key { code, down, repeat, .. } = *o {
                self.events += 1;
                if down {
                    match self.down.get(&code) {
                        None => assert_eq!(repeat, 0, "seed {seed}: key {code} repeats without a first down"),
                        Some(&last) => assert!(
                            repeat > last,
                            "seed {seed}: key {code} pressed again while down (repeat {repeat} after {last})"
                        ),
                    }
                    self.down.insert(code, repeat);
                } else {
                    assert!(self.down.remove(&code).is_some(), "seed {seed}: key {code} up without a down");
                }
            }
        }
    }
}

/// A plausible report from a gen 2/3 remote, or junk.
fn gen23_report(r: &mut Rng) -> (u8, Vec<u8>) {
    match r.below(10) {
        // buttons: a few bits, sometimes all of them, sometimes the wrong length
        0..=2 => {
            let mask: u16 = if r.chance(5) { 0xFFFF } else { 1 << r.below(16) | if r.chance(30) { 1 << r.below(16) } else { 0 } };
            let mut d = mask.to_le_bytes().to_vec();
            if r.chance(10) {
                d.truncate(r.below(3) as usize);
            }
            (0xFB, d)
        }
        // releases are the most common thing a remote sends
        3 => (0xFB, vec![0, 0]),
        // touch with one or two fingers, or a lift, or a cut-off report
        4..=7 => {
            let len = match r.below(6) {
                0 => r.below(25) as usize,
                1 => 18,
                _ => 11,
            };
            let mut d = r.bytes(len);
            if r.chance(20) && len >= 4 {
                d[1..].iter_mut().for_each(|b| *b = 0);
            }
            (0xFC, d)
        }
        8 => (0xFA, r.bytes_upto(120)),
        _ => (r.next() as u8, r.bytes_upto(40)),
    }
}

fn gen1_report(r: &mut Rng) -> (u8, Vec<u8>) {
    let d = match r.below(6) {
        // buttons
        0 | 1 => vec![0x00, 1 << r.below(8) | if r.chance(20) { 1 << r.below(8) } else { 0 }],
        2 => vec![0x00, 0x00],
        // touch (and the buttons held meanwhile), one or two fingers
        3 | 4 => {
            let n = if r.chance(70) { 13 } else { 20 };
            let mut d = r.bytes(n);
            d[2] = 0x32;
            if r.chance(60) {
                d[1] = 0;
            }
            d
        }
        _ => r.bytes_upto(130),
    };
    (0, d)
}

fn configure(e: &mut Engine, r: &mut Rng) {
    e.cfg.scroll_speed = [0.5, 0.75, 1.0, 1.35, 2.0][r.below(5) as usize];
    e.cfg.momentum = r.chance(70);
    e.cfg.ring_scrub = r.chance(70);
    e.cfg.edge_taps = r.chance(30);
    e.cfg.back_hold_home = r.chance(80);
    e.cfg.siri_key = if r.chance(70) { 0 } else { key::SEARCH };
    e.cfg.tv_hold_key = if r.chance(50) { -2 } else { key::SETTINGS };
}

fn run_session(seed: u64, steps: usize) {
    let mut r = Rng(seed.wrapping_mul(0x9E3779B97F4A7C15) | 1);
    let gen = if r.chance(25) { Generation::Gen1 } else { Generation::Gen23 };
    let mut e = Engine::new(gen);
    configure(&mut e, &mut r);
    let mut keys = Keys::default();
    let mut t = 100.0 + r.below(1000) as f64;
    e.connected(t);
    keys.apply(&e.drain(), seed);

    for _ in 0..steps {
        // gaps: a fast stream, a pause, a long sleep, and now and then a clock that stutters
        t += match r.below(20) {
            0 => r.below(5000) as f64 / 1000.0,
            1 => 0.0,
            2 => -0.004,
            _ => (5 + r.below(20)) as f64 / 1000.0,
        };
        match r.below(100) {
            0 => {
                e.disconnected(t);
                keys.apply(&e.drain(), seed);
                assert!(keys.down.is_empty(), "seed {seed}: keys stuck down after the link dropped: {:?}", keys.down);
                e.connected(t + 0.25);
            }
            1 if r.chance(20) => configure(&mut e, &mut r),
            _ => {
                let (id, data) = if gen == Generation::Gen1 { gen1_report(&mut r) } else { gen23_report(&mut r) };
                e.report(id, &data, t);
            }
        }
        let due = e.tick(t);
        if let Some(d) = due {
            assert!(d.is_finite() && d >= 0.0 && d < 10.0, "seed {seed}: next tick in {d} s");
        }
        let outs = e.drain();
        assert!(outs.len() < 400, "seed {seed}: {} events from one report", outs.len());
        keys.apply(&outs, seed);
    }

    // the remote goes away: everything lets go, and nothing glides on afterwards
    e.disconnected(t);
    keys.apply(&e.drain(), seed);
    assert!(keys.down.is_empty(), "seed {seed}: keys stuck down at the end: {:?}", keys.down);
    assert_eq!(keys.voice, 0, "seed {seed}: voice search left open after the remote was gone");
    for i in 1..300 {
        let due = e.tick(t + i as f64 * 0.01);
        assert!(e.drain().is_empty(), "seed {seed}: keys after the remote was gone");
        if i > 100 {
            assert!(due.is_none(), "seed {seed}: still ticking long after the remote was gone");
        }
    }
}

#[test]
fn random_sessions_never_panic_or_stick() {
    let sessions: u64 = std::env::var("SIRI_FUZZ_SESSIONS").ok().and_then(|s| s.parse().ok()).unwrap_or(3000);
    for seed in 0..sessions {
        run_session(seed, 600);
    }
}

#[test]
fn parsers_take_any_bytes() {
    let mut r = Rng(42);
    for _ in 0..200_000 {
        let data = r.bytes_upto(200);
        let id = r.next() as u8;
        let _ = parse_gen23(id, &data);
        let _ = parse_gen1(&data);
        if let Some((_, frame)) = parse_audio(&data) {
            assert!(frame.len() + 5 <= data.len());
        }
    }
}

#[test]
fn every_held_button_lets_go_when_the_remote_drops() {
    // all thirteen buttons down at once, then the link drops mid-hold
    for gen in [Generation::Gen23, Generation::Gen1] {
        let mut e = Engine::new(gen);
        let mut keys = Keys::default();
        e.connected(0.0);
        e.tick(1.0);
        keys.apply(&e.drain(), 0);
        match gen {
            Generation::Gen23 => e.report(0xFB, &[0xFF, 0x1F], 2.0),
            Generation::Gen1 => e.report(0, &[0x00, 0xFF], 2.0),
        }
        for i in 0..200 {
            e.tick(2.0 + i as f64 * 0.01);
        }
        keys.apply(&e.drain(), 0);
        assert!(!keys.down.is_empty());
        e.disconnected(4.0);
        keys.apply(&e.drain(), 0);
        assert!(keys.down.is_empty(), "{gen:?}: stuck {:?}", keys.down);
    }
}
