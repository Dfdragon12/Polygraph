// Consentimiento de cookies. Solo las técnicas se usan sin preguntar; analíticas y marketing
// arrancan SIEMPRE desactivadas y ningún script de esas categorías debe cargarse sin un "true".
const CLAVE = 'consentimiento_cookies'
export const VERSION_CONSENTIMIENTO = '1.0'
const VIGENCIA_MS = 365 * 24 * 60 * 60 * 1000

export function leerConsentimiento() {
  try {
    const c = JSON.parse(localStorage.getItem(CLAVE))
    if (!c || c.version !== VERSION_CONSENTIMIENTO || Date.now() - c.fecha > VIGENCIA_MS) return null
    return c
  } catch { return null }
}

export function guardarConsentimiento({ analiticas, marketing }) {
  const c = { version: VERSION_CONSENTIMIENTO, fecha: Date.now(), tecnicas: true, analiticas: !!analiticas, marketing: !!marketing }
  try { localStorage.setItem(CLAVE, JSON.stringify(c)) } catch { /* sin almacenamiento: se vuelve a preguntar */ }
  window.dispatchEvent(new CustomEvent('consentimiento-cookies', { detail: c }))
  return c
}

/** Úsese antes de cargar cualquier script analítico/de marketing: categoria = 'analiticas' | 'marketing'. */
export function tieneConsentimiento(categoria) {
  return leerConsentimiento()?.[categoria] === true
}

export function abrirPreferenciasCookies() {
  window.dispatchEvent(new CustomEvent('abrir-preferencias-cookies'))
}
