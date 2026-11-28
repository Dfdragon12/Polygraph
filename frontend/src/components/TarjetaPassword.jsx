// Tarjeta "Mi contraseña": cuándo se cambió por última vez, cuándo vence y cuánto falta. La usan
// "Mi perfil" de los usuarios internos y "Información de la empresa" del portal cliente, para que
// se vea igual en los dos. Recibe un objeto con los campos que manda el backend:
// fechaUltimoCambioPassword, diasParaCambiarPassword (0 o menos = ya toca; null = sin fecha),
// diasVigenciaPassword y requiereCambioPassword.

const ESTILOS = {
  vencida: { numero: 'text-red-600', barra: 'bg-red-500', aviso: 'bg-red-50 text-red-700 ring-red-100' },
  pronto: { numero: 'text-amber-600', barra: 'bg-amber-500', aviso: 'bg-amber-50 text-amber-700 ring-amber-100' },
  vigente: { numero: 'text-emerald-600', barra: 'bg-emerald-500', aviso: 'bg-emerald-50 text-emerald-700 ring-emerald-100' },
  'sin-fecha': { numero: 'text-gray-500', barra: 'bg-gray-300', aviso: 'bg-gray-50 text-gray-600 ring-gray-200' },
}

const fechaHora = (iso) => new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
const soloFecha = (fecha) => fecha.toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' })

export default function TarjetaPassword({ datos, className = '' }) {
  const vigencia = datos.diasVigenciaPassword
  const dias = datos.diasParaCambiarPassword
  const clave = dias == null ? 'sin-fecha' : dias <= 0 ? 'vencida' : dias <= 7 ? 'pronto' : 'vigente'
  const estilo = ESTILOS[clave]
  const restante = dias == null ? 0 : Math.max(0, Math.min(vigencia, dias))

  const ultimoCambio = datos.fechaUltimoCambioPassword ? new Date(datos.fechaUltimoCambioPassword) : null
  const haceDias = ultimoCambio ? Math.max(0, Math.floor((Date.now() - ultimoCambio.getTime()) / 86400000)) : null
  const textoHace = haceDias === null ? null : haceDias === 0 ? 'hoy' : haceDias === 1 ? 'ayer' : `hace ${haceDias} días`
  const fechaVence = ultimoCambio ? new Date(ultimoCambio.getTime() + vigencia * 86400000) : null

  return (
    <section className={`bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5 ${className}`}>
      <h3 className="text-base font-semibold text-gray-900">Mi contraseña</h3>
      <p className="text-sm text-gray-500 mt-0.5">Se renueva cada {vigencia} días.</p>
      <div className="mt-4">
        <dl className="divide-y divide-gray-100 -my-2.5">
          <div className="py-2.5">
            <dt className="text-xs font-medium text-gray-400">Último cambio</dt>
            {ultimoCambio ? (
              <dd className="mt-0.5">
                <span className="block text-sm font-semibold text-gray-900">{fechaHora(datos.fechaUltimoCambioPassword)}</span>
                <span className="block text-xs text-gray-500">{textoHace}</span>
              </dd>
            ) : (
              <dd className="text-sm text-gray-500 mt-0.5">Sin registro de un cambio anterior</dd>
            )}
          </div>
          {fechaVence && (
            <div className="py-2.5">
              <dt className="text-xs font-medium text-gray-400">Vence el</dt>
              <dd className="text-sm font-semibold text-gray-900 mt-0.5">{soloFecha(fechaVence)}</dd>
            </div>
          )}
          <div className="py-2.5">
            <dt className="text-xs font-medium text-gray-400">Tiempo para cambiarla</dt>
            <dd className="mt-0.5">
              {clave === 'sin-fecha' ? (
                <span className="text-sm text-gray-500">Sin plazo: no hay una fecha de cambio registrada.</span>
              ) : (
                <>
                  <span className={`block text-2xl font-bold ${estilo.numero}`}>
                    {dias <= 0 ? 'Ya toca cambiarla' : `${dias} ${dias === 1 ? 'día' : 'días'}`}
                  </span>
                  <div className="mt-2 h-1.5 rounded-full bg-gray-100 overflow-hidden" role="progressbar" aria-valuemin={0} aria-valuemax={vigencia}
                    aria-valuenow={restante} aria-label="Días que le quedan a tu contraseña">
                    <div className={`h-full rounded-full ${estilo.barra}`} style={{ width: `${(restante / vigencia) * 100}%` }} />
                  </div>
                </>
              )}
            </dd>
          </div>
        </dl>
        {(clave === 'pronto' || clave === 'vencida') && (
          <p className={`mt-4 text-xs rounded-lg px-3 py-2 ring-1 ${estilo.aviso}`}>
            {datos.requiereCambioPassword
              ? 'El administrador marcó tu contraseña para cambio: el sistema te pedirá una nueva al iniciar sesión.'
              : clave === 'vencida'
                ? 'El plazo ya venció: el sistema te pedirá una nueva al iniciar sesión.'
                : 'Te quedan pocos días: al vencer, el sistema te pedirá una nueva al iniciar sesión.'}
          </p>
        )}
      </div>
    </section>
  )
}
