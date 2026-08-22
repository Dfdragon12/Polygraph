// Misma paleta semántica usada en el resto de la plataforma (gestor, admin)
const ESTADOS = {
  PENDIENTE:    { color: 'bg-amber-100 text-amber-700',     texto: 'Pendiente' },
  PROGRAMANDO:  { color: 'bg-blue-100 text-blue-700',       texto: 'Programando' },
  EN_EJECUCION: { color: 'bg-primary-100 text-primary-700', texto: 'En ejecución' },
  FINALIZADO:   { color: 'bg-green-100 text-green-700',     texto: 'Finalizado' },
  PUBLICADO:    { color: 'bg-cyan-100 text-cyan-700',       texto: 'Publicado' },
  CANCELADO:    { color: 'bg-red-100 text-red-600',         texto: 'Cancelado' },
  REPROGRAMADO: { color: 'bg-orange-100 text-orange-700',   texto: 'Reprogramado' },
}

export default function ServiceStatusBadge({ status, estado }) {
  const key = (status || estado || '').toUpperCase()
  const cfg = ESTADOS[key] ?? { color: 'bg-gray-100 text-gray-500', texto: key || '—' }
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium whitespace-nowrap ${cfg.color}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current opacity-60" />
      {cfg.texto}
    </span>
  )
}
