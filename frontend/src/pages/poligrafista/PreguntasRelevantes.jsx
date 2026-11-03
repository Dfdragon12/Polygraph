import { Botones } from './BloquesEntrevista'
import { TextoHoja } from './componentesHoja'

// PF-05 · Preguntas relevantes de la prueba: una tarjeta por pregunta, precargadas con los temas que escogió el cliente. El poligrafista puede
// redactarlas o ajustarlas (queda registro de qué, quién y cuándo) y marca el resultado de cada una con los botones de siempre.

const NUMEROS = ['cero', 'uno', 'dos', 'tres', 'cuatro', 'cinco', 'seis', 'siete', 'ocho', 'nueve', 'diez', 'once', 'doce', 'trece', 'catorce', 'quince',
  'dieciséis', 'diecisiete', 'dieciocho', 'diecinueve', 'veinte', 'veintiuno', 'veintidós', 'veintitrés', 'veinticuatro', 'veinticinco', 'veintiséis',
  'veintisiete', 'veintiocho', 'veintinueve']
const DECENAS = ['', '', '', 'treinta', 'cuarenta', 'cincuenta', 'sesenta', 'setenta', 'ochenta', 'noventa']

// «tres», «veintidós»… para el campo «N° de preguntas relevantes (en letras)». Más de 99 se deja en cifras.
export function enLetras(n) {
  if (n < NUMEROS.length) return NUMEROS[n]
  if (n > 99) return String(n)
  const u = n % 10
  return u === 0 ? DECENAS[Math.floor(n / 10)] : `${DECENAS[Math.floor(n / 10)]} y ${NUMEROS[u]}`
}

const col = (seccion, clave) => seccion?.columnas.find(c => c.clave === clave)

export function PreguntasRelevantes({ seccion, temasCliente, cambios, noRealizado, disabled, onCelda, onAgregar }) {
  if (!seccion) return <p className="text-gray-700">Este tema no tiene campos en la plantilla.</p>
  const cPregunta = col(seccion, 'pr_pregunta')
  const cResultado = col(seccion, 'pr_resultado')
  const opciones = cResultado?.opciones ?? []
  const delCliente = (texto) => temasCliente.some(t => (texto ?? '').toLowerCase().startsWith((t.nombre ?? '').toLowerCase()))

  return (
    <div>
      {noRealizado && (
        <p className="mb-3 text-sm text-gray-700 bg-gray-50 border border-gray-200 rounded px-3 py-2">
          El examen no se realizó, así que las preguntas no son obligatorias.
        </p>
      )}
      {seccion.filas.length === 0 && <p className="text-gray-700 mb-3">Todavía no hay preguntas. El cliente no escogió temas: agrega las preguntas de la prueba.</p>}
      <div className="space-y-4">
        {seccion.filas.map((f, n) => {
          const texto = f.valores[cPregunta.idColumna] ?? ''
          const registro = cambios.filter(c => c.posicion === n + 1)
          const ultimo = registro[registro.length - 1]
          return (
            <div key={f.localId} className="rounded border border-gray-300 p-3">
              <div className="flex flex-wrap items-center gap-2 mb-2">
                <span className="text-[15px] font-bold text-gray-900">R{n + 1}</span>
                {delCliente(texto) && <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-amber-100 text-amber-800">Tema pedido por el cliente</span>}
              </div>
              <TextoHoja value={texto} disabled={disabled} aria-label={`Pregunta relevante R${n + 1}`}
                onChange={v => onCelda(seccion.idSeccion, f.localId, cPregunta.idColumna, v)} />
              {ultimo && (
                <p className="mt-1 text-xs text-gray-600">
                  {ultimo.anterior ? 'Ajustada' : 'Agregada'} por {ultimo.usuario} el {new Date(ultimo.fecha).toLocaleString('es-CO', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}
                </p>
              )}
              <div className="mt-3">
                <p className="text-sm font-bold text-gray-900 mb-1">Resultado</p>
                <Botones valor={f.valores[cResultado.idColumna] ?? ''} opciones={opciones} etiqueta={`Resultado de R${n + 1}`} disabled={disabled}
                  onElegir={v => onCelda(seccion.idSeccion, f.localId, cResultado.idColumna, v)} />
              </div>
            </div>
          )
        })}
      </div>
      {!disabled && (
        <button type="button" onClick={() => onAgregar(seccion.idSeccion)}
          className="mt-3 min-h-11 px-4 text-sm font-medium rounded-lg border border-primary-300 text-primary-700 hover:bg-primary-50">+ Agregar pregunta</button>
      )}
    </div>
  )
}

