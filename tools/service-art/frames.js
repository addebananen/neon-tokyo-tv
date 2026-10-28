// The frames the art is drawn in. cx and gy are where the subject stands, s is its scale.
// The hero keeps its subject right of centre, clear of the title and the fades on the left
// of the home screen.
const FRAMES = {
  wide: { W: 448, H: 252, cx: 224, gy: 192, s: 1.0 },
  square: { W: 320, H: 320, cx: 160, gy: 254, s: 1.16 },
  tall: { W: 264, H: 396, cx: 132, gy: 340, s: 1.0 },
  hero: { W: 640, H: 360, cx: 474, gy: 216, s: 1.0 },
};
module.exports = { FRAMES };
