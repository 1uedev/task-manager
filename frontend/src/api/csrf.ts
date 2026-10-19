// The only cookie the interface reads: the CSRF token the server set for this browser, which every changing request
// repeats in a header. The session cookie is HttpOnly and stays invisible here (checked by tests/static.test.ts).
const NAME = '__Host-tm-csrf';
const TOKEN = /^[A-Za-z0-9_-]{16,256}$/;

export function readCsrfToken(cookies: string = globalThis.document?.cookie ?? ''): string | null {
  for (const part of cookies.split(';')) {
    const separator = part.indexOf('=');
    if (separator < 0 || part.slice(0, separator).trim() !== NAME) continue;
    const value = part.slice(separator + 1).trim();
    return TOKEN.test(value) ? value : null;
  }
  return null;
}
