const ESTADOS = {
  PENDIENTE:    { color: 'bg-gray-200 text-gray-700',    icono: '🕐', texto: 'Pendiente' },
  PROGRAMANDO:  { color: 'bg-yellow-100 text-yellow-800', icono: '📅', texto: 'Programando' },
  EN_EJECUCION: { color: 'bg-blue-100 text-blue-800',    icono: '⚙️', texto: 'En Ejecución' },
  FINALIZADO:   { color: 'bg-orange-100 text-orange-800', icono: '✅', texto: 'Finalizado' },
  PUBLICADO:    { color: 'bg-green-100 text-green-800',  icono: '📄', texto: 'Publicado' },
  CANCELADO:    { color: 'bg-red-100 text-red-800',      icono: '❌', texto: 'Cancelado' },
  REPROGRAMADO: { color: 'bg-purple-100 text-purple-800', icono: '🔄', texto: 'Reprogramado' },
}

export default function ServiceStatusBadge({ status, estado }) {
  const key = (status || estado || '').toUpperCase()
  const cfg = ESTADOS[key] ?? { color: 'bg-gray-100 text-gray-500', icono: '?', texto: key || '—' }
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium whitespace-nowrap ${cfg.color}`}>
      <span>{cfg.icono}</span>
      {cfg.texto}
    </span>
  )
}
