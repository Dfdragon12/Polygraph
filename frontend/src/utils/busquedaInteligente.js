function normalizar(texto) {
  return (texto || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
}

/**
 * Mapa de intenciones comunes del cliente → categoría del catálogo que mejor las resuelve.
 * Las `clasificaciones` deben coincidir EXACTO con `clasificaciones_proceso.nombre` en la base
 * de datos (ver V118__catalogo_real_negocio.sql) — están en MAYÚSCULAS sin tilde: SERVICIOS,
 * PRUEBAS DE CONFIABILIDAD, ESTUDIOS DE SEGURIDAD, POLIGRAFIA, VERIFEYE. Si no coinciden
 * exacto, el filtro de categoría no encuentra nada y "Ver recomendados" se queda vacío.
 * No es IA real: es un diccionario curado a mano, pero le permite al cliente escribir en
 * lenguaje natural ("quiero validar un robo") y recibir una recomendación con sentido,
 * en vez de depender de que el texto exacto del servicio coincida con lo que escribió.
 */
const RECOMENDACIONES = [
  {
    id: 'robo',
    palabras: ['robo', 'robaron', 'hurto', 'hurtaron', 'faltante', 'desfalco', 'perdida de mercancia', 'perdida de dinero', 'perdida de inventario'],
    clasificaciones: ['POLIGRAFIA', 'PRUEBAS DE CONFIABILIDAD'],
    mensaje: 'Para investigar un robo, hurto o faltante, lo más usado es una Poligrafía o Prueba de Confiabilidad.',
  },
  {
    id: 'honestidad',
    palabras: ['honestidad', 'honesto', 'honesta', 'deshonesto', 'confianza', 'confiable', 'es de fiar', 'sospecha', 'sospechoso', 'desconfianza', 'mentira', 'miente'],
    clasificaciones: ['POLIGRAFIA', 'PRUEBAS DE CONFIABILIDAD'],
    mensaje: 'Para validar la honestidad o confiabilidad de una persona, te recomendamos una Poligrafía o Prueba de Confiabilidad.',
  },
  {
    id: 'drogas',
    palabras: ['droga', 'drogas', 'sustancias', 'consumo', 'alcohol'],
    clasificaciones: ['POLIGRAFIA', 'PRUEBAS DE CONFIABILIDAD'],
    mensaje: 'Para investigar consumo de sustancias, la Poligrafía o Prueba de Confiabilidad es la indicada.',
  },
  {
    id: 'antecedentes',
    palabras: ['antecedentes', 'referencias laborales', 'experiencia laboral', 'verificar hoja de vida', 'verificacion laboral', 'estudios', 'titulo', 'validacion laboral', 'validacion academica'],
    clasificaciones: ['SERVICIOS'],
    mensaje: 'Para verificar antecedentes, referencias laborales o académicas, revisa los servicios de Validaciones y Estudios.',
  },
  {
    id: 'domicilio',
    palabras: ['domicilio', 'vivienda', 'entorno familiar', 'visita domiciliaria', 'donde vive', 'direccion'],
    clasificaciones: ['SERVICIOS'],
    mensaje: 'Para conocer el entorno familiar y la vivienda de una persona, te recomendamos una Visita Domiciliaria.',
  },
  {
    id: 'acoso',
    palabras: ['acoso', 'conducta', 'comportamiento', 'agresion', 'violencia'],
    clasificaciones: ['ESTUDIOS DE SEGURIDAD', 'POLIGRAFIA'],
    mensaje: 'Para casos de conducta o comportamiento, un Estudio de Seguridad o una Poligrafía pueden ayudarte.',
  },
  {
    id: 'completo',
    palabras: ['estudio completo', 'todo incluido', 'paquete completo', 'estudio integral'],
    clasificaciones: ['ESTUDIOS DE SEGURIDAD'],
    mensaje: 'Si buscas un estudio integral con varios servicios combinados, revisa Estudios de Seguridad.',
  },
]

/** Devuelve la recomendación cuyo texto coincide con la búsqueda del cliente, o null si no aplica. */
export function recomendarPorTexto(texto) {
  const q = normalizar(texto).trim()
  if (q.length < 3) return null
  return RECOMENDACIONES.find((r) => r.palabras.some((p) => q.includes(normalizar(p)))) ?? null
}

/** Coincidencia simple por nombre/descripción — sin distinguir mayúsculas ni tildes. */
export function coincideTexto(servicio, texto) {
  const q = normalizar(texto).trim()
  if (!q) return true
  return normalizar(servicio.nombreProceso).includes(q) || normalizar(servicio.descripcion).includes(q)
}
