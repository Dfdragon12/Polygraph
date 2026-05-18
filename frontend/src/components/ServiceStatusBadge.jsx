const ESTADOS = {
  PENDIENTE:    { color: 'bg-gray-100 text-gray-700',     icono: '⏳', texto: 'Pendiente' },
  PROGRAMANDO:  { color: 'bg-yellow-100 text-yellow-700', icono: '📅', texto: 'Programando' },
  EN_EJECUCION: { color: 'bg-blue-100 text-blue-700',     icono: '▶️', texto: 'En Ejecución' },
  FINALIZADO:   { color: 'bg-orange-100 text-orange-700', icono: '✅', texto: 'Finalizado' },
  PUBLICADO:    { color: 'bg-green-100 text-green-700',   icono: '📢', texto: 'Publicado' },
  CANCELADO:    { color: 'bg-red-100 text-red-700',       icono: '❌', texto: 'Cancelado' },
  REPROGRAMADO: { color: 'bg-purple-100 text-purple-700', icono: '🔄', texto: 'Reprogramado' },
}

export default function ServiceStatusBadge({ estado }) {
  const cfg = ESTADOS[estado] ?? { color: 'bg-gray-100 text-gray-500', icono: '?', texto: estado }
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium ${cfg.color}`}>
      <span>{cfg.icono}</span>
      {cfg.texto}
    </span>
  )
}
