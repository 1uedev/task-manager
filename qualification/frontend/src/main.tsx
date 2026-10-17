import { createRoot } from 'react-dom/client';

const target = document.getElementById('root');
if (!target) throw new Error('Missing qualification mount');
createRoot(target).render(<main><h1>Technischer Build-Nachweis</h1><p>Synthetischer React/TypeScript-Build. Keine Produktoberfläche.</p></main>);
