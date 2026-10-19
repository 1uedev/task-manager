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

// TASK-036: the company sets one accent color (and a logo); light and dark surfaces are fixed. Text on the
// accent is chosen here so that it always reaches 4.5, whatever color is picked.
export const DEFAULT_ACCENT = '#f97316';
export const ACCENT_PRESETS = [
  { id: 'orange', color: '#f97316' },
  { id: 'amber', color: '#f59e0b' },
  { id: 'red', color: '#e11d48' },
  { id: 'green', color: '#16a34a' },
  { id: 'blue', color: '#2563eb' },
  { id: 'violet', color: '#7c3aed' },
] as const;

export type Surface = 'light' | 'dark';
const SURFACES: Readonly<Record<Surface, { readonly background: string; readonly text: string }>> = {
  light: { background: '#ffffff', text: '#17140f' },
  dark: { background: '#121110', text: '#efebe6' },
};
const WARM_INK = '#1c0f05';

export function isAccentColor(value: unknown): value is string {
  return typeof value === 'string' && HEX.test(value);
}

// A warm dark ink or white, whichever reads better; pure black or white as the last resort. For every color one
// of black and white reaches at least 4.58, so the result always meets 4.5.
export function textOn(accent: string): string {
  const preferred = contrastRatio(WARM_INK, accent) >= contrastRatio('#ffffff', accent) ? WARM_INK : '#ffffff';
  if (contrastRatio(preferred, accent) >= MIN_CONTRAST) return preferred;
  return contrastRatio('#000000', accent) >= contrastRatio('#ffffff', accent) ? '#000000' : '#ffffff';
}

export function accentBrand(accent: string, logoUrl: string | null, surface: Surface): Brand {
  const primary = isAccentColor(accent) ? accent.toLowerCase() : DEFAULT_ACCENT;
  return { name: 'Task Manager', logoUrl, colors: { primary, onPrimary: textOn(primary), ...SURFACES[surface] } };
}

export const defaultBrand: Brand = accentBrand(DEFAULT_ACCENT, null, 'light');

// A logo is either a path on the own origin or an https address. The value is parsed against a fixed
// base instead of being judged by its first characters: browsers treat a backslash like a slash and drop
// tabs and line breaks, so a value that starts with a slash and a backslash would otherwise point to another host.
const LOGO_BASE = 'https://brand.invalid';

export function acceptableLogo(url: string): string | null {
  if (url === '' || url.length > 2048 || /[\u0000-\u0020\u007f-\u009f\\]/.test(url)) return null;
  try {
    if (url.startsWith('/')) {
      const local = new URL(url, LOGO_BASE);
      return local.origin === LOGO_BASE ? `${local.pathname}${local.search}` : null;
    }
    const remote = new URL(url);
    return remote.protocol === 'https:' && remote.username === '' && remote.password === '' ? remote.href : null;
  } catch {
    return null;
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
    const accepted = typeof item.logoUrl === 'string' ? acceptableLogo(item.logoUrl) : null;
    if (accepted !== null) logoUrl = accepted;
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

function channels(hex: string): [number, number, number] {
  return [1, 3, 5].map((offset) => parseInt(hex.slice(offset, offset + 2), 16)) as [number, number, number];
}

function mix(foreground: string, background: string, share: number): string {
  const a = channels(foreground);
  const b = channels(background);
  const part = (index: 0 | 1 | 2) => Math.round(a[index] * share + b[index] * (1 - share)).toString(16).padStart(2, '0');
  return `#${part(0)}${part(1)}${part(2)}`;
}

function best(candidates: readonly string[], background: string, minimum: number, fallback: string): string {
  const ranked = candidates.filter((color) => contrastRatio(color, background) >= minimum);
  ranked.sort((x, y) => contrastRatio(y, background) - contrastRatio(x, background));
  return ranked[0] ?? fallback;
}

export interface Palette {
  readonly muted: string;
  readonly border: string;
  readonly error: string;
  readonly focus: string;
  readonly link: string;
}

// Secondary colors used directly on the page background are derived from the configured colors, so that
// every combination that is actually shown keeps its contrast (text 4.5, borders and focus 3). Because the
// main text color is already required to reach 4.5, falling back to it always satisfies every limit.
export function derivePalette(brand: Brand): Palette {
  const { background, text, primary } = brand.colors;
  const softened = mix(text, background, 0.75);
  const outline = mix(text, background, 0.55);
  return {
    muted: contrastRatio(softened, background) >= MIN_CONTRAST ? softened : text,
    border: contrastRatio(outline, background) >= 3 ? outline : text,
    error: best(['#8f1d1d', '#ff9d9d'], background, MIN_CONTRAST, text),
    focus: best(['#b35c00', '#ffbf47'], background, 3, text),
    link: ink(primary, text, background),
  };
}

// The accent darkened (or lightened) toward the text color just enough to be readable as text on the page,
// used for links and accent colored labels. The text color itself always reaches 4.5, so the loop ends.
function ink(primary: string, text: string, background: string): string {
  for (let step = 0; step <= 20; step += 1) {
    const candidate = mix(text, primary, step / 20);
    if (contrastRatio(candidate, background) >= MIN_CONTRAST) return candidate;
  }
  return text;
}

export function brandToCssVariables(brand: Brand): Record<string, string> {
  const palette = derivePalette(brand);
  return {
    '--brand-primary': brand.colors.primary,
    '--brand-on-primary': brand.colors.onPrimary,
    '--brand-background': brand.colors.background,
    '--brand-text': brand.colors.text,
    '--muted': palette.muted,
    '--border': palette.border,
    '--error': palette.error,
    '--focus': palette.focus,
    '--link': palette.link,
  };
}
