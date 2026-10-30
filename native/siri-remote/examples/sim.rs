//! Plays a pretend-remote script (`.github/scripts/siri-sim.py make`) through the engine the
//! way the bridge does, on a virtual clock, and prints the bridge's debug log. Lets the
//! emulator test's checks be tried here first:
//!
//!   cargo run --example sim -- remote.sim > log && siri-sim.py check log remote

use neon_siri::engine::{Engine, Out};
use neon_siri::protocol::Generation;

fn main() {
    let path = std::env::args().nth(1).expect("script");
    let text = std::fs::read_to_string(path).expect("read");
    let mut engine: Option<Engine> = None;
    let mut due: Option<f64> = None;
    let mut screen_on = std::env::args().nth(2).as_deref() != Some("screen-off");

    let emit = |e: &mut Engine, d: Option<f64>, t: f64, screen_on: &mut bool| -> Option<f64> {
        for o in e.drain() {
            match o {
                Out::Key { code: -2, down: true, .. } => println!("control center: opened"),
                Out::Key { code: -2, .. } => {}
                Out::Key { code, down, repeat, canceled } => println!(
                    "key {code} {} r{repeat}{}",
                    if down { "down" } else { "up" },
                    if canceled { " canceled" } else { "" }
                ),
                Out::PowerToggle => {
                    println!("power: screen was {}", if *screen_on { "on" } else { "off" });
                    let code = if *screen_on { 26 } else { 224 };
                    println!("key {code} down r0");
                    println!("key {code} up r0");
                    *screen_on = !*screen_on;
                }
                Out::VoiceStart => println!("voice start"),
                Out::VoiceStop => println!("voice stop"),
            }
        }
        d.map(|d| t + d.max(0.001))
    };

    for line in text.lines() {
        let p: Vec<&str> = line.split_whitespace().collect();
        let Some(t) = p.first().and_then(|x| x.parse::<f64>().ok()).map(|ms| ms / 1000.0) else { continue };
        // timers due before this report
        while let (Some(at), Some(e)) = (due, engine.as_mut()) {
            if at > t {
                break;
            }
            let d = e.tick(at);
            due = emit(e, d, at, &mut screen_on);
        }
        match p.get(1).copied() {
            Some("ready") => {
                let gen = if p.get(2) == Some(&"1") { Generation::Gen1 } else { Generation::Gen23 };
                let e = engine.get_or_insert_with(|| {
                    let mut e = Engine::new(gen);
                    e.cfg.siri_key = 0;
                    e.cfg.tv_hold_key = -2;
                    e
                });
                e.connected(t);
                let d = e.tick(t);
                due = emit(e, d, t, &mut screen_on);
            }
            Some("lost") => {
                if let Some(e) = engine.as_mut() {
                    e.disconnected(t);
                    let d = e.tick(t);
                    due = emit(e, d, t, &mut screen_on);
                }
            }
            Some("mark") => println!("mark {}", p[2..].join(" ")),
            Some("r") => {
                let id = u8::from_str_radix(p[2], 16).unwrap();
                let hex = p.get(3).copied().unwrap_or("");
                let data: Vec<u8> = (0..hex.len() / 2).map(|i| u8::from_str_radix(&hex[2 * i..2 * i + 2], 16).unwrap()).collect();
                if let Some(e) = engine.as_mut() {
                    if id != 0xFA {
                        e.report(id, &data, t);
                    }
                    let d = e.tick(t);
                    due = emit(e, d, t, &mut screen_on);
                }
            }
            _ => {}
        }
    }
    // let the last timers run out
    while let (Some(at), Some(e)) = (due, engine.as_mut()) {
        let d = e.tick(at);
        due = emit(e, d, at, &mut screen_on);
    }
}
