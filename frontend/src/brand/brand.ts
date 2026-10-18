// Company branding: logo and colors from a setting. Values are validated data, never executable code.

export interface Brand {
  readonly name: string;
  readonly logoUrl: string | null;
  readonly colors: {
    readonly primary: string;
    readonly onPrimary: string;
    readonly background: string;
    readonly text: string;
  };
}

export const defaultBrand: Brand = {
  name: 'Task Manager',
  logoUrl: null,
  colors: { primary: '#1f4e79', onPrimary: '#ffffff', background: '#ffffff', text: '#1b1b1b' },
};

export const syntheticBrand: Brand = {
  name: 'Beispiel GmbH',
  logoUrl: null,
  colors: { primary: '#7a2e0e', onPrimary: '#ffffff', background: '#fdf8f3', text: '#2a1a12' },
};

const HEX = /^#[0-9a-fA-F]{6}$/;
const MIN_CONTRAST = 4.5;

function luminance(hex: string): number {
  const channel = (offset: number) => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(1) + 0.7152 * channel(3) + 0.0722 * channel(5);
}

export function contrastRatio(foreground: string, background: string): number {
  const a = luminance(foreground);
  const b = luminance(background);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

function acceptableLogo(url: string): boolean {
  if (url.startsWith('/') && !url.startsWith('//')) return true;
  try {
    return new URL(url).protocol === 'https:';
  } catch {
    return false;
  }
}

export type BrandResult =
  | { readonly ok: true; readonly brand: Brand }
  | { readonly ok: false; readonly problems: readonly string[] };

// Accepts only a complete, valid and readable setting. Anything else keeps the previous branding.
export function parseBrand(input: unknown): BrandResult {
  const problems: string[] = [];
  const item = (typeof input === 'object' && input !== null ? input : {}) as Record<string, unknown>;
  const colors = (typeof item.colors === 'object' && item.colors !== null ? item.colors : {}) as Record<string, unknown>;
  const name = typeof item.name === 'string' ? item.name.trim() : '';
  if (name === '' || name.length > 80) problems.push('name');
  let logoUrl: string | null = null;
  if (item.logoUrl !== undefined && item.logoUrl !== null) {
    if (typeof item.logoUrl === 'string' && acceptableLogo(item.logoUrl)) logoUrl = item.logoUrl;
    else problems.push('logoUrl');
  }
  const read = (key: 'primary' | 'onPrimary' | 'background' | 'text'): string => {
    const value = colors[key];
    if (typeof value === 'string' && HEX.test(value)) return value.toLowerCase();
    problems.push(`colors.${key}`);
    return '#000000';
  };
  const parsed = { primary: read('primary'), onPrimary: read('onPrimary'), background: read('background'), text: read('text') };
  if (problems.length === 0) {
    if (contrastRatio(parsed.onPrimary, parsed.primary) < MIN_CONTRAST) problems.push('contrast.primary');
    if (contrastRatio(parsed.text, parsed.background) < MIN_CONTRAST) problems.push('contrast.text');
  }
  return problems.length > 0 ? { ok: false, problems } : { ok: true, brand: { name, logoUrl, colors: parsed } };
}

export function brandToCssVariables(brand: Brand): Record<string, string> {
  return {
    '--brand-primary': brand.colors.primary,
    '--brand-on-primary': brand.colors.onPrimary,
    '--brand-background': brand.colors.background,
    '--brand-text': brand.colors.text,
  };
}
