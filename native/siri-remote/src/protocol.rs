//! What the Siri Remote sends, decoded into buttons and fingers.
//!
//! Two families exist:
//!
//! - **Gen 2 / gen 3** (A2540 Lightning, A2854 USB-C, aluminium with a click ring). Every report has
//!   its own HID report characteristic, told apart by its Report Reference id: `0xFB` buttons
//!   (16-bit mask), `0xFC` touchpad, `0xFA` microphone (Opus).
//! - **Gen 1** (A1513, A1962, black glass touch surface). One characteristic carries everything,
//!   told apart by length; byte 1 is always the button mask. The remote stays silent until the host
//!   writes `0xAF` to its feature report.
//!
//! Sources: the protocol notes of TechnoHoschi/siri-remote-esp32-bridge (gen 3, MIT-0) and
//! The1TrueJoe/siri-remote (gen 1, MIT), both measured on real remotes.

/// Report ids of the gen 2/3 remote.
pub const REPORT_AUDIO: u8 = 0xFA;
pub const REPORT_BUTTONS: u8 = 0xFB;
pub const REPORT_TOUCH: u8 = 0xFC;

/// Written to the gen 1 feature report on every connect: unlocks buttons and touch.
pub const GEN1_UNLOCK: u8 = 0xAF;
/// Feature report `0x98` = 1 starts the microphone stream while Siri is held (gen 1).
pub const GEN1_STREAM_START: [u8; 2] = [0x98, 0x01];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Generation {
    /// Black touch surface, no ring.
    Gen1,
    /// Aluminium with a click ring around the clickpad (Lightning and USB-C).
    Gen23,
}

/// A physical button, the same names whichever remote sent it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Button {
    /// TV / Home button (AirPlay-TV on gen 1).
    Tv,
    VolumeUp,
    VolumeDown,
    /// Clickpad pressed in the centre (any click on gen 1).
    Select,
    Power,
    Siri,
    /// Back (labelled MENU on gen 1).
    Back,
    Mute,
    PlayPause,
    RingUp,
    RingRight,
    RingDown,
    RingLeft,
}

impl Button {
    pub const ALL: [Button; 13] = [
        Button::Tv,
        Button::VolumeUp,
        Button::VolumeDown,
        Button::Select,
        Button::Power,
        Button::Siri,
        Button::Back,
        Button::Mute,
        Button::PlayPause,
        Button::RingUp,
        Button::RingRight,
        Button::RingDown,
        Button::RingLeft,
    ];

    pub fn index(self) -> usize {
        Button::ALL.iter().position(|b| *b == self).unwrap_or(0)
    }
}

/// The set of buttons held right now, one bit per [`Button::index`].
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Buttons(pub u16);

impl Buttons {
    pub fn has(self, b: Button) -> bool {
        self.0 & (1 << b.index()) != 0
    }

    pub fn with(self, b: Button) -> Buttons {
        Buttons(self.0 | (1 << b.index()))
    }

    pub fn iter(self) -> impl Iterator<Item = Button> {
        Button::ALL.into_iter().filter(move |b| self.has(*b))
    }

    /// Gen 2/3 mask, verified bit by bit on an A2854.
    pub fn from_gen23(mask: u16) -> Buttons {
        const MAP: [(u16, Button); 13] = [
            (0x0001, Button::Tv),
            (0x0002, Button::VolumeUp),
            (0x0004, Button::VolumeDown),
            (0x0008, Button::Select),
            (0x0010, Button::Power),
            (0x0020, Button::Siri),
            (0x0040, Button::Back),
            (0x0080, Button::Mute),
            (0x0100, Button::PlayPause),
            (0x0200, Button::RingUp),
            (0x0400, Button::RingRight),
            (0x0800, Button::RingDown),
            (0x1000, Button::RingLeft),
        ];
        MAP.iter()
            .filter(|(bit, _)| mask & bit != 0)
            .fold(Buttons::default(), |acc, (_, b)| acc.with(*b))
    }

    /// Gen 1 mask (byte 1 of every report).
    pub fn from_gen1(mask: u8) -> Buttons {
        const MAP: [(u8, Button); 7] = [
            (0x01, Button::Tv),
            (0x02, Button::VolumeUp),
            (0x04, Button::VolumeDown),
            (0x08, Button::PlayPause),
            (0x10, Button::Siri),
            (0x20, Button::Back),
            (0x80, Button::Select),
        ];
        MAP.iter()
            .filter(|(bit, _)| mask & bit != 0)
            .fold(Buttons::default(), |acc, (_, b)| acc.with(*b))
    }
}

/// One finger, in pad coordinates normalised so the clickpad is the unit disc:
/// centre (0, 0), edge at radius 1, `+x` right and `+y` up.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Finger {
    pub x: f32,
    pub y: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Report {
    Buttons(Buttons),
    /// Fingers on the pad; empty when the last finger has lifted.
    Touch(Vec<Finger>),
    /// Gen 1 touch reports carry the buttons too.
    TouchAndButtons(Vec<Finger>, Buttons),
    /// A microphone frame (Siri held).
    Audio,
    Unknown,
}

// Gen 3 pad range, measured edge to edge: x -960..320, y -985..290 (12-bit signed).
const G3_CX: f32 = -320.0;
const G3_CY: f32 = -347.0;
const G3_R: f32 = 640.0;

fn signed12(v: u16) -> i32 {
    let v = (v & 0x0FFF) as i32;
    if v & 0x800 != 0 {
        v - 0x1000
    } else {
        v
    }
}

/// One 7-byte gen 3 finger slot: x, y (12-bit each, packed), size, size, ?, state.
fn gen3_slot(s: &[u8]) -> Option<Finger> {
    if s.len() < 7 {
        return None;
    }
    let state = s[6] & 0x07;
    // 3 = touch down, 4 = touching, 5..7 = lifting, 0 = gone
    if !(3..=4).contains(&state) {
        return None;
    }
    let x = signed12(s[0] as u16 | ((s[1] as u16 & 0x0F) << 8)) as f32;
    let y = signed12((s[1] as u16 >> 4) | ((s[2] as u16) << 4)) as f32;
    Some(Finger {
        x: (x - G3_CX) / G3_R,
        y: (y - G3_CY) / G3_R,
    })
}

/// Decodes one gen 2/3 notification of the report with id `report_id`.
pub fn parse_gen23(report_id: u8, data: &[u8]) -> Report {
    match report_id {
        REPORT_BUTTONS if data.len() >= 2 => {
            Report::Buttons(Buttons::from_gen23(u16::from_le_bytes([data[0], data[1]])))
        }
        REPORT_TOUCH if data.len() >= 11 => {
            let mut fingers = Vec::with_capacity(2);
            if let Some(f) = gen3_slot(&data[4..11]) {
                fingers.push(f);
            }
            if data.len() >= 18 {
                if let Some(f) = gen3_slot(&data[11..18]) {
                    fingers.push(f);
                }
            }
            Report::Touch(fingers)
        }
        REPORT_AUDIO => Report::Audio,
        _ => Report::Unknown,
    }
}

// Gen 1 decoded units: x about 0..100, y about 0..105 (coarser).
const G1_CX: f32 = 50.0;
const G1_CY: f32 = 52.0;
const G1_RX: f32 = 50.0;
const G1_RY: f32 = 52.0;

fn gen1_slot(f: &[u8]) -> Option<Finger> {
    if f.len() < 7 || f[5] == 0 {
        return None; // pressure 0: lifted
    }
    let x = (f[0] as f32 + 255.0 * (f[1] & 7) as f32 - 230.0) / 15.0;
    let y = if f[2] & 0x80 != 0 { f[2] as f32 } else { f[2] as f32 + 255.0 } - 188.0;
    Some(Finger {
        x: (x - G1_CX) / G1_RX,
        y: (y - G1_CY) / G1_RY,
    })
}

/// Decodes one gen 1 notification (everything arrives on the same characteristic).
pub fn parse_gen1(data: &[u8]) -> Report {
    match data.len() {
        2 if data[0] == 0x00 => Report::Buttons(Buttons::from_gen1(data[1])),
        2 => Report::Audio, // end of a voice stream
        13 | 20 if data[2] == 0x32 => {
            let mut fingers = Vec::with_capacity(2);
            if let Some(f) = gen1_slot(&data[6..13]) {
                fingers.push(f);
            }
            if data.len() == 20 {
                if let Some(f) = gen1_slot(&data[13..20]) {
                    fingers.push(f);
                }
            }
            Report::TouchAndButtons(fingers, Buttons::from_gen1(data[1]))
        }
        n if n > 20 && data[0] == 0x10 => Report::Audio,
        _ => Report::Unknown,
    }
}

/// One microphone report (gen 2/3, id 0xFA, 99 bytes): two zero bytes, a 16-bit little-endian
/// sequence number, the Opus frame's length, then the frame (CELT wideband, 20 ms, TOC 0xB8).
/// Returns the sequence number and the frame.
pub fn parse_audio(data: &[u8]) -> Option<(u16, &[u8])> {
    if data.len() < 6 {
        return None;
    }
    let seq = u16::from_le_bytes([data[2], data[3]]);
    let len = data[4] as usize;
    if len == 0 || 5 + len > data.len() {
        return None;
    }
    Some((seq, &data[5..5 + len]))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn audio_frames_come_out_whole() {
        let mut d = vec![0u8; 99];
        d[2] = 0x34;
        d[3] = 0x12;
        d[4] = 3;
        d[5..8].copy_from_slice(&[0xB8, 1, 2]);
        assert_eq!(parse_audio(&d), Some((0x1234, &[0xB8u8, 1, 2][..])));
        d[4] = 200;
        assert_eq!(parse_audio(&d), None, "a length past the end is refused");
        assert_eq!(parse_audio(&[0, 0, 1]), None);
    }

    #[test]
    fn gen23_buttons_decode_every_bit() {
        let all = Buttons::from_gen23(0x1FFF);
        assert_eq!(all.iter().count(), 13);
        let r = parse_gen23(REPORT_BUTTONS, &[0x08, 0x00]);
        assert_eq!(r, Report::Buttons(Buttons::default().with(Button::Select)));
        let r = parse_gen23(REPORT_BUTTONS, &[0x00, 0x12]);
        assert_eq!(
            r,
            Report::Buttons(Buttons::default().with(Button::RingLeft).with(Button::RingUp))
        );
    }

    fn g3_touch(x: i32, y: i32, state: u8) -> Vec<u8> {
        let x = (x & 0xFFF) as u16;
        let y = (y & 0xFFF) as u16;
        vec![
            0x32,
            0x01,
            0,
            0,
            (x & 0xFF) as u8,
            ((x >> 8) as u8 & 0x0F) | (((y & 0x0F) as u8) << 4),
            (y >> 4) as u8,
            0x40,
            0x30,
            0,
            state,
        ]
    }

    #[test]
    fn gen3_touch_centre_and_edges() {
        let Report::Touch(f) = parse_gen23(REPORT_TOUCH, &g3_touch(-320, -347, 4)) else {
            panic!()
        };
        assert!(f[0].x.abs() < 0.01 && f[0].y.abs() < 0.01);
        let Report::Touch(f) = parse_gen23(REPORT_TOUCH, &g3_touch(320, -347, 3)) else {
            panic!()
        };
        assert!((f[0].x - 1.0).abs() < 0.01);
        let Report::Touch(f) = parse_gen23(REPORT_TOUCH, &g3_touch(-320, 290, 4)) else {
            panic!()
        };
        assert!(f[0].y > 0.95, "up is positive");
    }

    #[test]
    fn gen3_lift_has_no_fingers() {
        assert_eq!(parse_gen23(REPORT_TOUCH, &g3_touch(0, 0, 6)), Report::Touch(vec![]));
        assert_eq!(parse_gen23(REPORT_TOUCH, &g3_touch(0, 0, 0)), Report::Touch(vec![]));
    }

    #[test]
    fn gen1_buttons_and_touch() {
        assert_eq!(
            parse_gen1(&[0x00, 0x20]),
            Report::Buttons(Buttons::default().with(Button::Back))
        );
        // finger in the middle: x raw = 50*15+230 = 980 = 0xD4 + 255*3 ; y = 52 -> f2 = 240
        let mut d = vec![1, 0x80, 0x32, 0, 0, 0, 0xD7, 3, 240, 0, 0, 40, 0];
        let Report::TouchAndButtons(f, b) = parse_gen1(&d) else { panic!() };
        assert!(b.has(Button::Select));
        assert!(f[0].x.abs() < 0.05 && f[0].y.abs() < 0.05, "{:?}", f[0]);
        d[11] = 0;
        let Report::TouchAndButtons(f, _) = parse_gen1(&d) else { panic!() };
        assert!(f.is_empty());
    }
}
