"""Motor de referencia – Verificación académica (reglas acordadas el 02/10/2026; estado "Terminó materias" agregado el 07/10/2026).
Especificación ejecutable para el código de la plataforma: el Java debe dar los mismos resultados."""

CATALOGO = {  # código: (descripción, puntos o 'Crítico')
    'ACA-01': ('Documento falso o adulterado', 'Crítico'),
    'ACA-02': ('La institución no confirma el título declarado', 70),
    'ACA-03': ('Datos que no coinciden con lo declarado (diferencia de fondo)', 45),
    'ACA-04': ('No verificable: el evaluado no aportó lo solicitado dentro del plazo', 45),
    'ACA-05': ('Institución no reconocida o título sin validez en Colombia', 30),
}
ALERTAS = {
    'ALR-ACA-01': 'Indicios de alteración sin confirmar',
    'ALR-ACA-02': 'Verificación pendiente por causa ajena al evaluado',
}
UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}  # modelo v1.8, sección 3
CRITERIOS = ['Último estudio finalizado', 'Por solicitud del cliente', 'Último estudio no verificable en el plazo']
ESTADOS = ['Graduado', 'Terminó materias, pendiente de grado', 'En curso', 'Retirado sin graduarse', 'Aplazado']
SIN_GRADO = ('Terminó materias, pendiente de grado', 'En curso', 'Retirado sin graduarse', 'Aplazado')
PESO_AVANZADO = 6  # % dentro del Estudio Avanzado (Validaciones 20 % = laboral 10 + académica 6 + personal 4)


def fmt(x):
    return f'{x:.1f}'.replace('.', ',')


def validar(f):
    """Avisos al guardar. Solo el primero bloquea el cálculo."""
    av = []
    if not f.get('ev_criticidad'):
        raise ValueError('Falta la criticidad del cargo.')
    ref = f.get('aca_ref', '—')
    if ref == 'ACA-01':
        if not f.get('aca_ac01_base'):
            av.append('ACA-01: indique si lo confirmó la institución o si es una alteración evidente en la inspección visual.')
        elif f['aca_ac01_base'] == 'Alteración evidente en inspección visual' and not f.get('aca_vobo'):
            av.append('ACA-01 por inspección visual: falta el visto bueno del segundo analista.')
    if ref in ALERTAS:
        av.append(f'{ref} no suma puntos: regístrela también en la sección 4 (hallazgos sin impacto).')
    if ref == 'ACA-04' and not (f.get('aca_fecha_sol_eval') and f.get('aca_fecha_limite')):
        av.append('ACA-04: registre la fecha de solicitud al evaluado y la fecha límite (4 días hábiles).')
    if not f.get('aca_criterio'):
        av.append('Indique el criterio de verificación.')
    elif f['aca_criterio'] != CRITERIOS[0] and len(f.get('aca_observaciones', '')) < 20:
        av.append('Criterio distinto al último estudio finalizado: explique en Observaciones qué estudio se verificó y por qué.')
    if ref == '—' and f.get('aca_estado') in SIN_GRADO and f.get('aca_documento') in ('Diploma', 'Acta de grado'):
        av.append('El estudio no tiene grado pero el evaluado aportó diploma o acta: revise si corresponde ACA-01 o ALR-ACA-01.')
    if f.get('aca_criterio') == CRITERIOS[0] and f.get('aca_estado') == ESTADOS[1]:
        av.append('Un estudio con materias terminadas y sin grado no es el "Último estudio finalizado": verifique el último estudio con grado, '
                  'o use el criterio "Por solicitud del cliente" y explíquelo en Observaciones.')
    return av


def calificar(f, t):
    """f = campos del formulario (incluye ev_criticidad y aca_ac01_base, que no se imprimen); t = tablas."""
    avisos = validar(f)
    ref = f.get('aca_ref', '—')
    hall = []
    if ref in CATALOGO:
        desc, pts = CATALOGO[ref]
        hall.append(dict(h_ref=ref, h_descripcion=desc, h_fuente=f.get('aca_medio', ''),
                         h_detalle=f"{f.get('aca_titulo', '')} – {f.get('aca_institucion', '')}",
                         h_puntos=pts if pts == 'Crítico' else str(pts)))
    critico = any(h['h_puntos'] == 'Crítico' for h in hall)
    nums = [int(h['h_puntos']) for h in hall if h['h_puntos'] != 'Crítico']
    if critico:
        puntaje, nivel = 100.0, 'ALTO'
        formula = f'Hallazgo crítico ({ref}): nivel ALTO'
    else:
        puntaje = float(min(100, sum(nums)))
        b, m = UMBRALES[f['ev_criticidad']]
        nivel = 'BAJO' if puntaje <= b else 'MEDIO' if puntaje <= m else 'ALTO'
        formula = 'Sin hallazgos que sumen puntos' if not nums else f'{ref}: {nums[0]} de 100 posibles'
    marca = '▲ Hallazgo crítico' if critico else f'▲ {fmt(puntaje)} puntos'
    campos = dict(srv_puntaje=fmt(puntaje), srv_formula=formula,
                  sem_bajo=marca if nivel == 'BAJO' else '', sem_medio=marca if nivel == 'MEDIO' else '',
                  sem_alto=marca if nivel == 'ALTO' else '')
    return dict(campos=campos, hallazgos=hall, nivel=nivel, puntaje=puntaje, critico=critico, avisos=avisos)


BASE = dict(aca_institucion='Institución de ejemplo', aca_titulo='Título de ejemplo', aca_medio='Base de datos de la institución',
            aca_criterio='Último estudio finalizado')
CASOS = {
    'A1 Sin novedad (Estándar)': dict(BASE, ev_criticidad='Estándar', aca_ref='—'),
    'A2 Documento falso por inspección visual, sin visto bueno (Estándar)': dict(BASE, ev_criticidad='Estándar', aca_ref='ACA-01',
                                                                                   aca_ac01_base='Alteración evidente en inspección visual'),
    'A3 Institución no confirma el título (Estándar)': dict(BASE, ev_criticidad='Estándar', aca_ref='ACA-02'),
    'A4 Datos que no coinciden (Estándar)': dict(BASE, ev_criticidad='Estándar', aca_ref='ACA-03'),
    'A5 No aportó lo solicitado (Crítico)': dict(BASE, ev_criticidad='Crítico', aca_ref='ACA-04', aca_fecha_sol_eval='28/09/2026',
                                                   aca_fecha_limite='02/10/2026'),
    'A6 Institución no reconocida (Estándar)': dict(BASE, ev_criticidad='Estándar', aca_ref='ACA-05'),
    'A7 Institución no responde (Sensible)': dict(BASE, ev_criticidad='Sensible', aca_ref='ALR-ACA-02'),
    'A8 Profesional por solicitud del cliente, sin explicar (Sensible)': dict(BASE, ev_criticidad='Sensible', aca_ref='ACA-03',
                                                                              aca_criterio='Por solicitud del cliente'),
    'A9 Terminó materias, marcado como último finalizado (Estándar)': dict(BASE, ev_criticidad='Estándar', aca_ref='—',
                                                                           aca_estado='Terminó materias, pendiente de grado',
                                                                           aca_documento='Certificado de terminación de materias'),
}

if __name__ == '__main__':
    for n, f in CASOS.items():
        r = calificar(f, {})
        print(n, '|', r['campos']['srv_puntaje'], r['nivel'], '|', r['campos']['srv_formula'], '|', r['avisos'])
