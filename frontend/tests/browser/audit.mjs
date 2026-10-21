// Shared measurements for the browser tests: colors are resolved through a canvas, so color-mix and other
// modern notations are compared like the browser actually paints them.
export function auditContrast(page) {
  return page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 1;
    const context = canvas.getContext('2d', { willReadFrequently: true });
    const rgba = (value) => {
      context.clearRect(0, 0, 1, 1);
      context.fillStyle = '#000';
      context.fillStyle = value;
      context.fillRect(0, 0, 1, 1);
      const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
      return [r, g, b, a / 255];
    };
    const luminance = ([r, g, b]) => {
      const [x, y, z] = [r, g, b].map((v) => { const c = v / 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; });
      return 0.2126 * x + 0.7152 * y + 0.0722 * z;
    };
    const ratio = (a, b) => { const x = luminance(a); const y = luminance(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
    const blend = (top, bottom) => top.slice(0, 3).map((v, i) => v * top[3] + bottom[i] * (1 - top[3])).concat(1);
    const backgroundOf = (element) => {
      const layers = [];
      for (let node = element; node; node = node.parentElement) {
        const color = rgba(getComputedStyle(node).backgroundColor);
        if (color[3] > 0) layers.push(color);
        if (color[3] >= 1) break;
      }
      let result = [255, 255, 255, 1];
      for (const layer of layers.reverse()) result = blend(layer, result);
      return result;
    };
    const failures = [];
    let checked = 0;
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const element = node.parentElement;
      if (!element || node.textContent.trim() === '') continue;
      const style = getComputedStyle(element);
      const box = element.getBoundingClientRect();
      if (box.width === 0 || box.height === 0 || style.visibility === 'hidden' || element.closest('.visually-hidden,[hidden],option')) continue;
      if (element.closest('button:disabled')) continue; // WCAG excludes inactive controls
      if (element.closest('details:not([open]) > :not(summary)')) continue; // content of a closed disclosure is not shown
      const size = parseFloat(style.fontSize);
      const large = size >= 24 || (size >= 18.66 && Number(style.fontWeight) >= 700);
      const value = ratio(blend(rgba(style.color), backgroundOf(element)), backgroundOf(element));
      checked += 1;
      if (value < (large ? 3 : 4.5)) failures.push(`${element.tagName.toLowerCase()}.${element.className} "${node.textContent.trim().slice(0, 30)}" ${value.toFixed(2)}`);
    }
    return { checked, failures };
  });
}
