import { AYUDA_CONCLUSION, AVISO_FAVORABLE, contieneFavorable } from '../utils/conclusion'

/**
 * PC-02 · Va debajo del campo de conclusión de cada formato (el que el backend marca con `esConclusion`):
 * la ayuda fija «No escribas Favorable o Desfavorable…» y, si el texto lo dice, un aviso que pide corregirlo.
 * El aviso vive en una región `role="status"` que siempre existe, para que el lector de pantalla lo anuncie cuando aparece.
 */
export default function AvisoConclusion({ texto, className = '' }) {
  const malo = contieneFavorable(texto)
  return (
    <div className={`mt-1 space-y-1 ${className}`}>
      <p className="text-xs text-gray-700">{AYUDA_CONCLUSION}</p>
      <p role="status" aria-live="polite"
        className={malo ? 'text-xs font-semibold text-amber-900 bg-amber-50 border border-amber-300 rounded px-2 py-1' : 'sr-only'}>
        {malo ? AVISO_FAVORABLE : ''}
      </p>
    </div>
  )
}
