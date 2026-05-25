import { differenceInDays, parseISO, format, isValid } from 'date-fns'
import { es } from 'date-fns/locale'

function WorkTimeline({ experiencias = [], inactividades = [] }) {
  const validas = experiencias.filter(e => e.fechaInicio && isValid(parseISO(e.fechaInicio)))

  if (validas.length === 0) {
    return (
      <div className="text-center py-6 text-gray-400 text-sm">
        Aún no hay experiencias laborales registradas
      </div>
    )
  }

  const fmt = (fecha) => {
    if (!fecha) return 'Actual'
    const parsed = parseISO(fecha)
    if (!isValid(parsed)) return 'Fecha inválida'
    return format(parsed, 'MMM yyyy', { locale: es })
  }

  const sorted = [...validas].sort(
    (a, b) => parseISO(a.fechaInicio) - parseISO(b.fechaInicio)
  )

  const fechaMin = parseISO(sorted[0].fechaInicio)
  const fechaMax = new Date()
  const totalDias = differenceInDays(fechaMax, fechaMin) || 1

  const toPercent = (fecha) => {
    if (!fecha) return 0
    const d = parseISO(fecha)
    if (!isValid(d)) return 0
    const dias = differenceInDays(d, fechaMin)
    return Math.min(100, Math.max(0, (dias / totalDias) * 100))
  }

  const widthPercent = (inicio, fin) => {
    const inicioDate = parseISO(inicio)
    if (!isValid(inicioDate)) return 1
    const finDate = fin && isValid(parseISO(fin)) ? parseISO(fin) : new Date()
    const dias = differenceInDays(finDate, inicioDate)
    return Math.max(1, (dias / totalDias) * 100)
  }

  return (
    <div className="mt-4">
      <h4 className="text-sm font-semibold text-gray-700 mb-3">Línea de tiempo laboral</h4>

      <div className="relative h-8 bg-gray-100 rounded-lg overflow-hidden">
        {sorted.map((exp, i) => (
          <div
            key={i}
            title={`${exp.empresa} — ${exp.cargo}\n${fmt(exp.fechaInicio)} → ${exp.fechaFin ? fmt(exp.fechaFin) : 'Actualidad'}`}
            className="absolute top-0 h-full bg-green-400 rounded opacity-80 hover:opacity-100 transition-opacity cursor-pointer"
            style={{
              left: `${toPercent(exp.fechaInicio)}%`,
              width: `${widthPercent(exp.fechaInicio, exp.fechaFin)}%`,
            }}
          />
        ))}

        {inactividades.map((gap, i) => (
          <div
            key={`gap-${i}`}
            title={`Inactividad: ${gap.diasInactivo} días\n${fmt(gap.fechaInicio)} → ${fmt(gap.fechaFin)}`}
            className={`absolute top-0 h-full rounded opacity-70 cursor-pointer ${
              gap.diasInactivo > 30 ? 'bg-red-400' : 'bg-yellow-300'
            }`}
            style={{
              left: `${toPercent(gap.fechaInicio)}%`,
              width: `${widthPercent(gap.fechaInicio, gap.fechaFin)}%`,
            }}
          />
        ))}
      </div>

      <div className="flex gap-4 mt-2 text-xs text-gray-500">
        <span className="flex items-center gap-1">
          <span className="w-3 h-3 bg-green-400 rounded inline-block" /> Empleado
        </span>
        <span className="flex items-center gap-1">
          <span className="w-3 h-3 bg-red-400 rounded inline-block" /> Inactividad &gt;30 días
        </span>
        <span className="flex items-center gap-1">
          <span className="w-3 h-3 bg-yellow-300 rounded inline-block" /> Inactividad ≤30 días
        </span>
      </div>

      {inactividades.filter((g) => g.diasInactivo > 30).map((gap, i) => (
        <div
          key={`alert-${i}`}
          className="mt-2 p-2 bg-red-50 border border-red-200 rounded text-xs text-red-700"
        >
          Tiempo muerto de <strong>{gap.diasInactivo} días</strong> entre{' '}
          {fmt(gap.fechaInicio)} y {fmt(gap.fechaFin)}. Se requiere justificación.
        </div>
      ))}
    </div>
  )
}

export default WorkTimeline
