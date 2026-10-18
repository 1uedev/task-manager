import assert from 'node:assert/strict';
import { test } from 'node:test';
import { brandToCssVariables, contrastRatio, darkSyntheticBrand, defaultBrand, derivePalette, parseBrand, syntheticBrand, type Brand } from '../src/brand/brand.ts';

test('built in brandings are valid and readable', () => {
  for (const brand of [defaultBrand, syntheticBrand, darkSyntheticBrand]) {
    const result = parseBrand(brand);
    assert.equal(result.ok, true);
    assert.ok(contrastRatio(brand.colors.onPrimary, brand.colors.primary) >= 4.5);
    assert.ok(contrastRatio(brand.colors.text, brand.colors.background) >= 4.5);
  }
});

test('contrast ratio matches known reference values', () => {
  assert.equal(Math.round(contrastRatio('#000000', '#ffffff')), 21);
  assert.equal(contrastRatio('#ffffff', '#ffffff'), 1);
});

test('only a complete setting is accepted', () => {
  assert.deepEqual(parseBrand(null), { ok: false, problems: ['name', 'colors.primary', 'colors.onPrimary', 'colors.background', 'colors.text'] });
  const result = parseBrand({ ...defaultBrand, colors: { ...defaultBrand.colors, primary: 'blue' } });
  assert.deepEqual(result, { ok: false, problems: ['colors.primary'] });
});

test('unreadable color combinations are rejected', () => {
  const result = parseBrand({ ...defaultBrand, colors: { ...defaultBrand.colors, primary: '#ffff00' } });
  assert.deepEqual(result, { ok: false, problems: ['contrast.primary'] });
});

test('colors are data, not executable or style injecting content', () => {
  for (const value of ['red;}body{display:none', 'url(javascript:alert(1))', '#12345', '#12345g', 'expression(1)']) {
    const result = parseBrand({ ...defaultBrand, colors: { ...defaultBrand.colors, text: value } });
    assert.equal(result.ok, false, value);
  }
});

test('logo addresses must be same origin paths or https', () => {
  assert.equal(parseBrand({ ...defaultBrand, logoUrl: '/brand/logo.svg' }).ok, true);
  assert.equal(parseBrand({ ...defaultBrand, logoUrl: 'https://cdn.example.invalid/logo.svg' }).ok, true);
  for (const logoUrl of ['javascript:alert(1)', 'data:image/svg+xml,<svg/>', 'http://example.invalid/logo.png', '//example.invalid/logo.png', 42]) {
    assert.equal(parseBrand({ ...defaultBrand, logoUrl }).ok, false, String(logoUrl));
  }
});

test('names are limited and trimmed', () => {
  const ok = parseBrand({ ...defaultBrand, name: '  Beispiel GmbH ' });
  assert.equal(ok.ok && ok.brand.name, 'Beispiel GmbH');
  assert.equal(parseBrand({ ...defaultBrand, name: 'x'.repeat(81) }).ok, false);
  assert.equal(parseBrand({ ...defaultBrand, name: '   ' }).ok, false);
});

test('css variables are produced from the validated setting only', () => {
  assert.deepEqual(brandToCssVariables(defaultBrand), {
    '--brand-primary': '#1f4e79',
    '--brand-on-primary': '#ffffff',
    '--brand-background': '#ffffff',
    '--brand-text': '#1b1b1b',
    '--muted': '#545454',
    '--border': '#828282',
    '--error': '#8f1d1d',
    '--focus': '#b35c00',
    '--link': '#1f4e79',
  });
});

test('logo addresses are normalized and cannot point to another host', () => {
  const accepted = (logoUrl: string) => {
    const result = parseBrand({ ...defaultBrand, logoUrl });
    return result.ok ? result.brand.logoUrl : null;
  };
  assert.equal(accepted('/brand/logo.svg'), '/brand/logo.svg');
  assert.equal(accepted('/brand/logo.svg?v=2'), '/brand/logo.svg?v=2');
  assert.equal(accepted('https://CDN.example.invalid/logo.svg'), 'https://cdn.example.invalid/logo.svg');
  const rejected = [
    '/\\example.invalid/logo.svg',
    '/\t/example.invalid/logo.svg',
    '/\n/example.invalid/logo.svg',
    '/\r/example.invalid',
    '//example.invalid/logo.png',
    '/ /example.invalid',
    'https://cdn.example.invalid/a b.svg',
    '\\\\example.invalid\\logo.svg',
    'data:image/svg+xml,<svg/>',
    'javascript:alert(1)',
    'https://user:secret@example.invalid/logo.svg',
    'logo.svg',
    '',
    `/${'a'.repeat(2048)}`,
  ];
  for (const logoUrl of rejected) assert.equal(accepted(logoUrl), null, JSON.stringify(logoUrl));
});

function ratiosOf(brand: Brand): Record<string, number> {
  const palette = derivePalette(brand);
  const { background } = brand.colors;
  return {
    muted: contrastRatio(palette.muted, background),
    border: contrastRatio(palette.border, background),
    error: contrastRatio(palette.error, background),
    focus: contrastRatio(palette.focus, background),
    link: contrastRatio(palette.link, background),
  };
}

test('secondary colors keep their contrast on a dark branding', () => {
  const ratios = ratiosOf(darkSyntheticBrand);
  for (const name of ['muted', 'error', 'link']) assert.ok(ratios[name]! >= 4.5, `${name} ${ratios[name]}`);
  for (const name of ['border', 'focus']) assert.ok(ratios[name]! >= 3, `${name} ${ratios[name]}`);
});

test('every accepted branding yields a readable palette', () => {
  const samples = ['#000000', '#ffffff', '#101010', '#f0f0f0', '#7a2e0e', '#fdf8f3', '#003366', '#ffcc00', '#808080', '#8f1d1d', '#ff9d9d', '#b35c00', '#ffbf47', '#222244', '#ddeedd'];
  let accepted = 0;
  for (const background of samples) {
    for (const text of samples) {
      for (const primary of samples) {
        for (const onPrimary of ['#000000', '#ffffff']) {
          const result = parseBrand({ name: 'Test', logoUrl: null, colors: { primary, onPrimary, background, text } });
          if (!result.ok) continue;
          accepted += 1;
          const ratios = ratiosOf(result.brand);
          const label = `${background} ${text} ${primary}`;
          for (const name of ['muted', 'error', 'link']) assert.ok(ratios[name]! >= 4.5, `${label} ${name} ${ratios[name]}`);
          for (const name of ['border', 'focus']) assert.ok(ratios[name]! >= 3, `${label} ${name} ${ratios[name]}`);
        }
      }
    }
  }
  assert.ok(accepted > 200, `only ${accepted} combinations were checked`);
});
