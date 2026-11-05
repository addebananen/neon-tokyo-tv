// The fast neon intro: the sign flickers on tube by tube, a light sweeps across, then it opens
// into the app. Any key, click or tap skips it.

export function playIntro() {
  return new Promise((resolve) => {
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) { resolve(); return; }
    const el = document.createElement('div');
    el.className = 'intro';
    el.setAttribute('aria-hidden', 'true');
    const word = (t, cls) => `<span class="${cls}">${[...t].map((c, i) => `<i style="--d:${(i * 53 + (i % 3) * 71) % 420}ms">${c}</i>`).join('')}</span>`;
    el.innerHTML = `<div class="intro-sun"></div><div class="intro-sign">${word('NEON', 'iw neon')}${word('TOKYO', 'iw tokyo')}<span class="intro-jp">ネオン東京</span><span class="intro-sweep"></span></div><div class="intro-horizon"></div>`;
    document.body.append(el);
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      el.classList.add('out');
      removeEventListener('keydown', finish, true);
      removeEventListener('pointerdown', finish, true);
      setTimeout(() => { el.remove(); }, 450);
      resolve();
    };
    addEventListener('keydown', finish, true);
    addEventListener('pointerdown', finish, true);
    requestAnimationFrame(() => el.classList.add('on'));
    setTimeout(finish, 1900);
  });
}
