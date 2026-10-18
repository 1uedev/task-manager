import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App.tsx';
import { BrandProvider } from './brand/BrandProvider.tsx';
import { I18nProvider } from './i18n/I18nProvider.tsx';
import './styles.css';

const target = document.getElementById('root');
if (!target) throw new Error('Missing mount element');
createRoot(target).render(
  <StrictMode>
    <I18nProvider>
      <BrandProvider>
        <App />
      </BrandProvider>
    </I18nProvider>
  </StrictMode>,
);
