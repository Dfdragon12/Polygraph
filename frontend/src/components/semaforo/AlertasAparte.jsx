import { useCallback, useEffect, useState } from 'react'
import semaforoService from '../../services/semaforoService'
import { useCambios } from '../../hooks/useCambios'

// Las alertas que van APARTE del semáforo, debajo de él y con otro estilo (SE-09): no son estados, son cosas que piden atención. Las calcula el servidor y
// una que no aplica al perfil no se dibuja. onAlerta(clave) deja que la pantalla filtre su lista cuando se pulsa (opcional).
const ALERTAS = [
  { clave: 'sinAsignar', etiqueta: 'Sin asignar', ayuda: 'Subprocesos que nadie tiene aún' },
  { clave: 'novedadesAbiertas', etiqueta: 'Novedades abiertas', ayuda: 'Sin respuesta todavía' },
  { clave: 'citasDeHoy', etiqueta: 'Citas de hoy', ayuda: 'Poligrafías y visitas de hoy' },
  { clave: 'vencidas', etiqueta: 'Vencidas', ayuda: 'Entrega al cliente pasada' },
  { clave: 'paraHoy', etiqueta: 'Para hoy', ayuda: 'La entrega vence hoy' },
  { clave: 'porConfirmar', etiqueta: 'Por confirmar', ayuda: 'Cita sin registro tras 15 minutos' },
]

export default function AlertasAparte({ onAlerta, refrescar = 0 }) {
  const [datos, setDatos] = useState(null)
  const cargar = useCallback(() => { semaforoService.alertas().then(setDatos).catch(() => setDatos(null)) }, [])
  useEffect(() => { cargar() }, [cargar, refrescar])
  useCambios(cargar)

  const visibles = datos ? ALERTAS.filter(a => datos[a.clave] != null) : []
  if (!visibles.length) return null
  return (
    <section aria-label="Alertas" className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border-l-4 border-slate-400 bg-slate-50 px-4 py-2.5">
      <h3 className="text-[11px] font-bold text-slate-700 uppercase tracking-wider">Alertas</h3>
      {visibles.map(a => {
        const n = datos[a.clave]
        const contenido = (
          <>
            <span className={`inline-flex min-w-6 justify-center rounded px-1.5 text-xs font-bold tabular-nums ${n > 0 ? 'bg-slate-800 text-white' : 'bg-slate-200 text-slate-700'}`}>{n}</span>
            <span className="text-xs font-medium text-slate-900">{a.etiqueta}</span>
          </>
        )
        return onAlerta
          ? <button key={a.clave} type="button" title={a.ayuda} onClick={() => onAlerta(a.clave)}
              className="inline-flex items-center gap-1.5 rounded-md px-1.5 py-1 hover:bg-slate-200 transition-colors">{contenido}</button>
          : <span key={a.clave} title={a.ayuda} className="inline-flex items-center gap-1.5 px-1.5 py-1">{contenido}</span>
      })}
    </section>
  )
}
