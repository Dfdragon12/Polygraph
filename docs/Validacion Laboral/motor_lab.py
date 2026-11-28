"""Motor de referencia – Verificación laboral (reglas acordadas el 05/10/2026).
Especificación ejecutable para el código de la plataforma: el Java debe dar los mismos resultados."""

CATALOGO = {  # código: (descripción, puntos o 'Crítico')
    'LAB-01': ('Certificado laboral falso o adulterado', 'Crítico'),
    'LAB-02': ('La empresa no reconoce la vinculación declarada', 70),
    'LAB-03': ('Datos que no coinciden de fondo (fechas, cargo o tipo de contrato)', 60),
    'LAB-04': ('El motivo de retiro declarado no coincide con el confirmado', 45),
    'LAB-05': ('Despido con justa causa', 70),
    'LAB-06': ('Abandono del cargo', 60),
    'LAB-07': ('Llamados de atención por faltas de cumplimiento', 50),
    'LAB-08': ('Llamados o sanciones por faltas de conducta o confianza', 70),
    'LAB-09': ('Persona natural o informal: concepto "malo" o no lo volvería a contratar', 50),
    'LAB-10': ('Experiencia omitida en la hoja de vida (más de 30 días), sin explicación razonable', 40),
    'LAB-11': ('No verificable: el evaluado no aportó el certificado solicitado en 4 días hábiles', 50),
    'LAB-12': ('Persona natural o informal: concepto "regular" del jefe', 30),
    'LAB-13': ('Periodo de inactividad no corroborado', 30),
    'LAB-14': ('Periodo de inactividad sustentado solo con la declaración del evaluado', 10),
}
ALERTAS = {'ALR-LAB-01': 'Verificación pendiente por causa ajena al evaluado (la empresa no responde y no hay sábana ni aportes)'}
INACTIVIDAD = {'No se pudo corroborar': 'LAB-13', 'Solo declaración del evaluado': 'LAB-14'}  # una vez por estudio cada uno
SELECCIONABLES = [c for c in CATALOGO if c not in INACTIVIDAD.values()]
SOLO_INFORMAL = {'LAB-09', 'LAB-12'}
UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}  # modelo v1.8, sección 3
PESO_AVANZADO = 10  # % (Validaciones 20 % = laboral 10 + académica 6 + personal 4)

VALIDADO_POR = ['La empresa', 'Jefe inmediato (persona natural o informal)', 'Sábana pensional o bases de datos', 'Pendiente']
TIPO_VINC = ['Empresa', 'Persona natural o informal']
MOTIVO_CLASE = ['Renuncia voluntaria', 'Terminación o fin de contrato', 'Mutuo acuerdo', 'Despido sin justa causa',
                'Despido con justa causa', 'Abandono del cargo', 'Vigente (aún vinculado)', 'No informado']
CORROBORACION = ['Referencia confirmó', 'Aportes como independiente', 'Solo declaración del evaluado', 'No se pudo corroborar']
NOTA_SABANA = ('Se confirma la vinculación por medio de bases de datos públicas y privadas. No fue posible validar motivo de retiro, '
               'llamados de atención ni la veracidad del certificado.')


def fmt(x):
    return f'{x:.1f}'.replace('.', ',')


def validar(f, t, final=False):
    """Avisos al guardar. 'Falta la criticidad' bloquea el cálculo; 'Pendiente' bloquea solo el informe final."""
    if not f.get('ev_criticidad'):
        raise ValueError('Falta la criticidad del cargo.')
    av, bloqueos = [], []
    if not (f.get('lab_periodo_desde') and f.get('lab_periodo_hasta')):
        av.append('Indique el periodo validado (desde y hasta) según el tipo de estudio.')
    for v in t.get('vinculaciones', []):
        n = v.get('v_numero', '?')
        refs = [v.get('v_ref1', '—'), v.get('v_ref2', '—')]
        if refs[0] == refs[1] and refs[0] != '—':
            av.append(f'Vinculación {n}: el mismo código está marcado dos veces; cuenta una sola vez.')
        for r in refs:
            if r in SOLO_INFORMAL and v.get('v_tipo') != 'Persona natural o informal':
                av.append(f'Vinculación {n}: {r} solo aplica a vinculaciones con persona natural o informales.')
            if r in ALERTAS:
                av.append(f'Vinculación {n}: {r} no suma puntos; regístrela también en la sección 4.')
            if r == 'LAB-11' and not (v.get('v_fecha_sol_eval') and v.get('v_fecha_limite')):
                av.append(f'Vinculación {n}: LAB-11 requiere la fecha de solicitud al evaluado y la fecha límite.')
            if r == 'LAB-01':
                if not v.get('v_lab01_base'):
                    av.append(f'Vinculación {n}: indique si el LAB-01 lo confirmó la empresa o es alteración evidente.')
                elif v['v_lab01_base'] == 'Alteración evidente en inspección visual' and not v.get('v_vobo'):
                    av.append(f'Vinculación {n}: LAB-01 por inspección visual requiere visto bueno del segundo analista.')
        if v.get('v_motivo_clase') == 'Despido con justa causa' and 'LAB-05' not in refs:
            av.append(f'Vinculación {n}: el motivo es despido con justa causa; revise si corresponde LAB-05.')
        if v.get('v_motivo_clase') == 'Abandono del cargo' and 'LAB-06' not in refs:
            av.append(f'Vinculación {n}: el motivo es abandono del cargo; revise si corresponde LAB-06.')
        if v.get('v_validado_por') == 'Pendiente':
            (bloqueos if final else av).append(f'Vinculación {n}: sigue pendiente. Ciérrela por sábana pensional o como LAB-11 antes del informe final.')
    return av, bloqueos


def calificar(f, t, final=False):
    t = dict(t, vinculaciones=[dict(v, v_numero=str(i)) for i, v in enumerate(t.get('vinculaciones', []), 1)])
    avisos, bloqueos = validar(f, t, final)
    hall = []
    for v in t.get('vinculaciones', []):
        vistos = set()
        for r in (v.get('v_ref1', '—'), v.get('v_ref2', '—')):
            if r in SELECCIONABLES and r not in vistos:
                vistos.add(r)
                desc, pts = CATALOGO[r]
                hall.append(dict(h_ref=r, h_descripcion=desc, h_fuente=v.get('v_validado_por', ''),
                                 h_detalle=f"Vinculación N° {v['v_numero']}: {v.get('v_empresa', '')} ({v.get('v_cargo', '')})",
                                 h_puntos=pts if pts == 'Crítico' else str(pts)))
    for opcion, code in INACTIVIDAD.items():
        nc = sum(1 for r in t.get('inactividad', []) if r.get('in_corroboracion') == opcion)
        if nc:
            hall.append(dict(h_ref=code, h_descripcion=CATALOGO[code][0], h_fuente='Periodos de inactividad',
                             h_detalle=f'{nc} periodo(s) (cuenta una vez por estudio)', h_puntos=str(CATALOGO[code][1])))
    criticos = list(dict.fromkeys(h['h_ref'] for h in hall if h['h_puntos'] == 'Crítico'))
    nums = [int(h['h_puntos']) for h in hall if h['h_puntos'] != 'Crítico']
    bruto = sum(nums)
    if criticos:
        puntaje, nivel = 100.0, 'ALTO'
        formula = 'Hallazgo crítico (' + ', '.join(criticos) + '): nivel ALTO'
    else:
        puntaje = float(min(100, bruto))
        b, m = UMBRALES[f['ev_criticidad']]
        nivel = 'BAJO' if puntaje <= b else 'MEDIO' if puntaje <= m else 'ALTO'
        if not nums:
            formula = 'Sin hallazgos que sumen puntos'
        elif len(nums) == 1:
            formula = f"{hall[0]['h_ref']}: {nums[0]} de 100 posibles"
        else:
            formula = ' + '.join(map(str, nums)) + (f' = {bruto} → 100 (tope máximo)' if bruto > 100 else f' = {bruto} de 100 posibles')
    marca = '▲ Hallazgo crítico' if criticos else f'▲ {fmt(puntaje)} puntos'
    campos = dict(srv_puntaje=fmt(puntaje), srv_formula=formula,
                  sem_bajo=marca if nivel == 'BAJO' else '', sem_medio=marca if nivel == 'MEDIO' else '',
                  sem_alto=marca if nivel == 'ALTO' else '',
                  vinc_nota='' if t.get('vinculaciones') else 'El evaluado no registra vinculaciones laborales en el periodo validado.',
                  inactividad_nota='' if t.get('inactividad') else 'No se identificaron periodos de inactividad superiores a 30 días en el periodo validado.')
    filas_v = []
    for v in t.get('vinculaciones', []):
        v = dict(v)
        refs = [r for r in dict.fromkeys((v.get('v_ref1', '—'), v.get('v_ref2', '—'))) if r != '—']
        v['v_calificacion'] = ', '.join(refs) or '—'
        v['v_nota'] = NOTA_SABANA if v.get('v_validado_por') == 'Sábana pensional o bases de datos' else ''
        filas_v.append(v)
    return dict(campos=campos, hallazgos=hall, vinculaciones=filas_v, nivel=nivel, puntaje=puntaje,
                critico=bool(criticos), avisos=avisos, bloqueos=bloqueos)


def V(n, emp='Empresa de ejemplo', r1='—', r2='—', por='La empresa', tipo='Empresa', **k):
    return dict(v_numero=str(n), v_empresa=emp, v_cargo='Cargo de ejemplo', v_ref1=r1, v_ref2=r2, v_validado_por=por, v_tipo=tipo, **k)


P = dict(lab_periodo_desde='10/2022', lab_periodo_hasta='10/2026')
NC = dict(in_corroboracion='No se pudo corroborar')
CASOS = {
    'L1 Sin novedad (Estándar)': (dict(P, ev_criticidad='Estándar'), dict(vinculaciones=[V(1), V(2)])),
    'L2 Validada por sábana pensional (Estándar)': (dict(P, ev_criticidad='Estándar'), dict(vinculaciones=[V(1, por='Sábana pensional o bases de datos')])),
    'L3 Llamado de cumplimiento + motivo no coincide (Sensible)': (dict(P, ev_criticidad='Sensible'),
        dict(vinculaciones=[V(1, r1='LAB-07'), V(2, r1='LAB-04')])),
    'L4 Certificado falso (Crítico)': (dict(P, ev_criticidad='Crítico'),
        dict(vinculaciones=[V(1, r1='LAB-01', v_lab01_base='Confirmado por la empresa')])),
    'L5 Datos no coinciden + 2 inactividades sin corroborar (Estándar)': (dict(P, ev_criticidad='Estándar'),
        dict(vinculaciones=[V(1, r1='LAB-03')], inactividad=[NC, NC])),
    'L6 Llamados en dos empresas, uno repetido (Estándar)': (dict(P, ev_criticidad='Estándar'),
        dict(vinculaciones=[V(1, r1='LAB-07', r2='LAB-07'), V(2, r1='LAB-07')])),
    'L7 Despido con justa causa + abandono (Estándar)': (dict(P, ev_criticidad='Estándar'),
        dict(vinculaciones=[V(1, r1='LAB-05', v_motivo_clase='Despido con justa causa'), V(2, r1='LAB-06', v_motivo_clase='Abandono del cargo')])),
    'L8 Concepto regular en empresa formal (aviso) (Sensible)': (dict(P, ev_criticidad='Sensible'),
        dict(vinculaciones=[V(1, r1='LAB-12')])),
    'L9 Nunca ha trabajado formalmente, inactividad corroborada (Estándar)': (dict(P, ev_criticidad='Estándar'),
        dict(inactividad=[dict(in_corroboracion='Referencia confirmó')])),
    'L10 Inactividad: una sin corroborar y dos solo declaradas (Sensible)': (dict(P, ev_criticidad='Sensible'),
        dict(vinculaciones=[V(1)], inactividad=[NC, dict(in_corroboracion='Solo declaración del evaluado'), dict(in_corroboracion='Solo declaración del evaluado')])),
}

if __name__ == '__main__':
    for n, (f, t) in CASOS.items():
        r = calificar(f, t)
        print(n, '|', r['campos']['srv_puntaje'], r['nivel'], '|', r['campos']['srv_formula'], '|', [h['h_ref'] for h in r['hallazgos']], r['avisos'])
    f, t = dict(P, ev_criticidad='Estándar'), dict(vinculaciones=[V(1, por='Pendiente')])
    print('Pendiente, informe final →', calificar(f, t, final=True)['bloqueos'])
