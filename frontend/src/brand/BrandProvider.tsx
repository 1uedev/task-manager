import { createContext, useCallback, useContext, useLayoutEffect, useMemo, useState, type ReactNode } from 'react';
import { brandToCssVariables, defaultBrand, parseBrand, type Brand } from './brand.ts';

interface BrandState {
  readonly brand: Brand;
  readonly isDefault: boolean;
  readonly apply: (input: unknown) => boolean;
  readonly reset: () => void;
}

const Context = createContext<BrandState | null>(null);

export function BrandProvider({ children }: { readonly children: ReactNode }) {
  const [brand, setBrand] = useState<Brand>(defaultBrand);

  useLayoutEffect(() => {
    const root = document.documentElement;
    const variables = brandToCssVariables(brand);
    for (const [name, value] of Object.entries(variables)) root.style.setProperty(name, value);
    return () => {
      for (const name of Object.keys(variables)) root.style.removeProperty(name);
    };
  }, [brand]);

  const apply = useCallback((input: unknown) => {
    const result = parseBrand(input);
    if (result.ok) setBrand(result.brand);
    return result.ok;
  }, []);
  const reset = useCallback(() => setBrand(defaultBrand), []);

  const value = useMemo(() => ({ brand, isDefault: brand === defaultBrand, apply, reset }), [brand, apply, reset]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useBrand(): BrandState {
  const value = useContext(Context);
  if (!value) throw new Error('BrandProvider is missing');
  return value;
}
