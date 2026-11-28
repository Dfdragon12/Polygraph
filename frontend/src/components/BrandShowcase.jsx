import { useEffect, useRef, useState } from 'react'

/* Carrusel informativo del panel de marca (Login/Register/RecuperarPassword/ResetPassword).
   Usa fotos reales (public/brand/, licencia Pexels — libres de uso). Cada slide muestra el
   servicio + una lista corta de "por qué hacerlo con nosotros" (beneficios concretos, no una
   sola frase). Cambia solo cada 6s (crossfade + deslizamiento) pero también se puede navegar
   manualmente con flechas o los puntos — al navegar a mano se reinicia el temporizador para que
   no salte justo después del clic. */

const SLIDES = [
  {
    titulo: 'Una sola plataforma',
    texto: 'Solicita, agenda y haz seguimiento de cada estudio en un solo lugar.',
    beneficios: [
      'Semáforo de servicio en tiempo real',
      'Informes centralizados y trazables',
      'Portal dedicado para tu equipo y tus clientes',
    ],
    imagen: '/brand/plataforma.jpg',
  },
  {
    titulo: 'Poligrafías',
    texto: 'Pruebas de polígrafo aplicadas por examinadores certificados.',
    beneficios: [
      'Examinadores certificados internacionalmente',
      'Protocolos estandarizados y confidenciales',
      '15 años de experiencia respaldando cada resultado',
    ],
    imagen: '/brand/poligrafia.jpg',
  },
  {
    titulo: 'Visitas domiciliarias',
    texto: 'Verificación en campo del entorno familiar y social del evaluado.',
    beneficios: [
      'Visitadores en todo el país',
      'Evidencia fotográfica y reporte estructurado',
      'Reportes listos en tiempo récord',
    ],
    imagen: '/brand/visita.jpg',
  },
  {
    titulo: 'Estudios de seguridad',
    texto: 'Análisis integral de antecedentes y referencias del evaluado.',
    beneficios: [
      'Cobertura nacional',
      'Seguimiento en tiempo real de cada estudio',
      'Decisiones de contratación mejor informadas',
    ],
    imagen: '/brand/seguridad.jpg',
  },
  {
    titulo: 'Validación de hoja de vida',
    texto: 'Confirmación de estudios y experiencia laboral del evaluado.',
    beneficios: [
      'Verificación directa con las fuentes originales',
      'Sin intermediarios ni demoras',
      'Reportes claros y trazables',
    ],
    imagen: '/brand/hojadevida.jpg',
  },
]

const IconoAnterior = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
  </svg>
)
const IconoSiguiente = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
  </svg>
)

export default function BrandShowcase() {
  const [indice, setIndice] = useState(0)
  const intervaloRef = useRef(null)

  const reiniciarAutoplay = () => {
    if (intervaloRef.current) clearInterval(intervaloRef.current)
    intervaloRef.current = setInterval(() => {
      setIndice((i) => (i + 1) % SLIDES.length)
    }, 6000)
  }

  useEffect(() => {
    reiniciarAutoplay()
    return () => clearInterval(intervaloRef.current)
  }, [])

  const irA = (nuevo) => {
    setIndice((nuevo + SLIDES.length) % SLIDES.length)
    reiniciarAutoplay()
  }

  return (
    <div>
      <div className="relative min-h-[450px] rounded-3xl overflow-hidden group">
        {SLIDES.map((slide, i) => (
          <div
            key={slide.titulo}
            className={`absolute inset-0 transition-all duration-700 ease-out ${
              i === indice ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2 pointer-events-none'
            }`}
          >
            {/* Difuminado: fundido lineal arriba/abajo, en este contenedor fijo (no en la <img>
                que se escala con Ken Burns) para que no se distorsione. */}
            <div
              className="absolute inset-0"
              style={{
                WebkitMaskImage: 'linear-gradient(to bottom, transparent 0%, black 18%, black 80%, transparent 100%)',
                maskImage: 'linear-gradient(to bottom, transparent 0%, black 18%, black 80%, transparent 100%)',
              }}
            >
              <img
                src={slide.imagen}
                alt={slide.titulo}
                className="w-full h-full object-cover animate-ken-burns"
              />
            </div>
            <div className="absolute inset-0 bg-gradient-to-t from-slate-900 via-slate-900/50 to-transparent" />
            <div className="relative z-10 h-full flex flex-col justify-end p-6">
              <h2 className="text-2xl font-bold text-white leading-snug">{slide.titulo}</h2>
              <p className="text-slate-300 text-[13px] leading-relaxed mt-2 max-w-sm">{slide.texto}</p>
              <div className="h-px w-10 bg-primary-400/60 my-3" />
              <ul className="space-y-2 max-w-sm">
                {slide.beneficios.map((beneficio) => (
                  <li key={beneficio} className="flex items-center gap-2.5">
                    <span className="w-1.5 h-1.5 rounded-full bg-primary-300 flex-shrink-0" />
                    <span className="text-slate-200 text-[13px] leading-snug">{beneficio}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        ))}

        {/* Flechas de navegación manual — visibles siempre en mobile-like/táctil, con fade-in al
            pasar el mouse en desktop (opacity-0 group-hover:opacity-100) para no ensuciar la foto */}
        <button
          type="button"
          onClick={() => irA(indice - 1)}
          aria-label="Servicio anterior"
          className="absolute left-2 top-1/2 -translate-y-1/2 z-20 w-8 h-8 rounded-full bg-black/35 hover:bg-black/55 backdrop-blur-sm flex items-center justify-center text-white opacity-70 group-hover:opacity-100 transition-all"
        >
          {IconoAnterior}
        </button>
        <button
          type="button"
          onClick={() => irA(indice + 1)}
          aria-label="Siguiente servicio"
          className="absolute right-2 top-1/2 -translate-y-1/2 z-20 w-8 h-8 rounded-full bg-black/35 hover:bg-black/55 backdrop-blur-sm flex items-center justify-center text-white opacity-70 group-hover:opacity-100 transition-all"
        >
          {IconoSiguiente}
        </button>
      </div>

      {/* Indicadores clicleables — navegación directa a un slide */}
      <div className="flex justify-center gap-2 mt-4">
        {SLIDES.map((slide, i) => (
          <button
            key={slide.titulo}
            type="button"
            onClick={() => irA(i)}
            aria-label={`Ir a ${slide.titulo}`}
            className={`h-2 rounded-full transition-all duration-500 ${
              i === indice ? 'w-8 bg-white' : 'w-2 bg-white/30 hover:bg-white/50'
            }`}
          />
        ))}
      </div>
    </div>
  )
}
