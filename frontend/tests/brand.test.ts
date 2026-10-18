import assert from 'node:assert/strict';
import { test } from 'node:test';
import { brandToCssVariables, contrastRatio, defaultBrand, parseBrand, syntheticBrand } from '../src/brand/brand.ts';

test('built in brandings are valid and readable', () => {
  for (const brand of [defaultBrand, syntheticBrand]) {
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
  });
});
