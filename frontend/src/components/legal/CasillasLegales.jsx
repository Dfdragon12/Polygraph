import { Link } from 'react-router-dom'

/**
 * Casillas legales del registro (react-hook-form). Ninguna viene marcada. Términos y privacidad son
 * obligatorias; marketing es opcional y nunca condiciona el servicio.
 * Los enlaces abren en pestaña nueva para no perder lo que la persona ya escribió.
 */
export default function CasillasLegales({ register, errors, prefijo = '' }) {
  const enlace = 'text-primary-700 underline hover:text-primary-800'
  const nuevaPestana = { target: '_blank', rel: 'noopener noreferrer' }
  const fila = 'flex items-start gap-3'
  const caja = 'mt-0.5 h-5 w-5 shrink-0 accent-primary-600'

  return (
    <fieldset className="space-y-3 rounded-xl border border-gray-200 p-4">
      <legend className="px-1 text-sm font-semibold text-gray-800">Aceptaciones</legend>

      <div>
        <div className={fila}>
          <input id={`${prefijo}acepta-terminos`} type="checkbox" className={caja}
            aria-required="true" aria-invalid={!!errors.aceptaTerminos}
            aria-describedby={errors.aceptaTerminos ? `${prefijo}err-terminos` : undefined}
            {...register('aceptaTerminos')} />
          <label htmlFor={`${prefijo}acepta-terminos`} className="text-sm text-gray-800">
            He leído y acepto los{' '}
            <Link to="/legal/terminos" className={enlace} {...nuevaPestana}>Términos y condiciones</Link>{' '}
            y declaro ser mayor de edad y tener facultades para actuar en nombre de la empresa, si aplica. <span aria-hidden="true">*</span>
          </label>
        </div>
        {errors.aceptaTerminos && (
          <p id={`${prefijo}err-terminos`} role="alert" className="mt-1 text-xs text-red-700">{errors.aceptaTerminos.message}</p>
        )}
      </div>

      <div>
        <div className={fila}>
          <input id={`${prefijo}acepta-privacidad`} type="checkbox" className={caja}
            aria-required="true" aria-invalid={!!errors.aceptaPrivacidad}
            aria-describedby={errors.aceptaPrivacidad ? `${prefijo}err-privacidad` : undefined}
            {...register('aceptaPrivacidad')} />
          <label htmlFor={`${prefijo}acepta-privacidad`} className="text-sm text-gray-800">
            He leído la{' '}
            <Link to="/legal/privacidad" className={enlace} {...nuevaPestana}>Política de Privacidad</Link>{' '}
            y autorizo el tratamiento de mis datos para gestionar mi cuenta y prestarme el servicio. <span aria-hidden="true">*</span>
          </label>
        </div>
        {errors.aceptaPrivacidad && (
          <p id={`${prefijo}err-privacidad`} role="alert" className="mt-1 text-xs text-red-700">{errors.aceptaPrivacidad.message}</p>
        )}
      </div>

      <div className={fila}>
        <input id={`${prefijo}acepta-marketing`} type="checkbox" className={caja} {...register('aceptaMarketing')} />
        <label htmlFor={`${prefijo}acepta-marketing`} className="text-sm text-gray-800">
          (Opcional) Autorizo el envío de información comercial y novedades. Puedo retirarla cuando quiera.
        </label>
      </div>

      <p className="text-xs text-gray-600">* Obligatorio para crear la cuenta.</p>
    </fieldset>
  )
}
