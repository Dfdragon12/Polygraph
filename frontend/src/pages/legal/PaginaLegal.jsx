import { useEffect } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { VERSION_LEGAL, VIGENTE_DESDE } from '../../config/empresa'
import PieLegal from '../../components/legal/PieLegal'
import { TEXTOS } from './textosLegales'

/** Páginas públicas de políticas: /legal/:tipo (privacidad | terminos | cookies | reembolsos). */
export default function PaginaLegal() {
  const { tipo } = useParams()
  const texto = TEXTOS[tipo]

  useEffect(() => { if (texto) document.title = `${texto.titulo} — Polygraph Service` }, [texto])

  if (!texto) return <Navigate to="/login" replace />

  return (
    <div className="min-h-screen bg-white">
      <header className="border-b border-gray-200 px-4 py-3">
        <Link to="/login" className="text-sm text-primary-700 underline">← Volver al inicio</Link>
      </header>
      <main id="contenido" tabIndex={-1} className="mx-auto max-w-3xl px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-900">{texto.titulo}</h1>
        <p className="mt-1 text-sm text-gray-600">Versión {VERSION_LEGAL} · Vigente desde {VIGENTE_DESDE}</p>
        {texto.secciones.map(([titulo, bloques]) => (
          <section key={titulo} className="mt-6">
            <h2 className="text-lg font-semibold text-gray-900">{titulo}</h2>
            {bloques.map((b, i) => Array.isArray(b)
              ? <ul key={i} className="mt-2 list-disc space-y-1 pl-6 text-sm text-gray-800">{b.map((li) => <li key={li}>{li}</li>)}</ul>
              : <p key={i} className="mt-2 text-sm leading-relaxed text-gray-800">{b}</p>)}
          </section>
        ))}
      </main>
      <PieLegal className="border-t border-gray-200" />
    </div>
  )
}
