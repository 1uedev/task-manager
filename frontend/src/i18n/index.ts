import { de } from './de.ts';
import { en } from './en.ts';
import type { Registry } from './core.ts';

// Adding a language means adding one catalog here (and its display name in both catalogs).
export const registry: Registry = { de, en };
export const supportedLanguages = ['de', 'en'] as const;
export type SupportedLanguage = (typeof supportedLanguages)[number];
export type { MessageKey } from './de.ts';
