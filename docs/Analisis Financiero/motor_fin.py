"""Motor de referencia – Verificación de antecedentes financieros (reglas acordadas el 06/10/2026).
Especificación ejecutable para el código de la plataforma: el Java debe dar los mismos resultados.

Fuentes:
- Todos los estudios: consulta de SÍNTESIS de centrales de riesgo (totales por sector, barra de comportamiento de los
  últimos 12 meses, análisis de ingresos, indicador de pago).
- Solo OEA: además, el REPORTE DETALLADO (hábito de pago de cada obligación) y la declaración de renta y patrimonio.
"""

CATALOGO = {  # código: (descripción, puntos, acumula)
    'FIN-01': ('Obligación en cartera castigada sin pagar, o con mora vigente superior a 180 días', 60, 'Por obligación (OEA); una vez con la síntesis'),
    'FIN-02': ('Obligación con mora vigente de 90 a 180 días', 45, 'Por obligación (OEA); una vez con la síntesis'),
    'FIN-03': ('Moras de 30 a 89 días repetidas: 3 o más meses con mora 30 o mora 60 en el último año', 25, 'Una vez por estudio'),
    'FIN-04': ('Endeudamiento alto: la utilización del ingreso estimado frente a la cuota mensual supera el 70 %', 40, 'Una vez por estudio'),
    'FIN-05': ('Proceso ejecutivo o embargo vigente', 50, 'Una vez por estudio'),
    'FIN-06': ('Codeudor de una obligación en mora (estudios OEA, con el reporte detallado)', 20, 'Por obligación'),
    'FIN-07': ('Inconsistencia patrimonial: bienes declarados que no aparecen en los registros, bienes registrados que no declaró, o cambio '
               'patrimonial frente al estudio anterior que no se explica con sus ingresos, sin explicación razonable (estudios OEA)', 30, 'Una vez por estudio'),
    'FIN-08': ('Ocultamiento financiero: negó u omitió deudas, moras, reportes o embargos que aparecen en centrales de riesgo '
               '(poligrafía, visita domiciliaria o formulario del evaluado)', 40, 'Una vez por estudio'),
}
ALERTAS = {'ALR-FIN-01': 'Score en la banda más baja de la central consultada sin ningún código FIN marcado: revisar el reporte antes de cerrar'}
UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}  # modelo v1.8, sección 3
PESO_AVANZADO = 15
LIMITE_ENDEUDAMIENTO = 70           # % de utilización del ingreso estimado
MESES_FIN03 = 3                     # meses con mora 30 o 60 en el último año
MESES_FIN01 = 5                     # meses seguidos con mora 90 o más (hasta el más reciente) => más de 180 días
# Bandas del indicador de pago: se configuran por central en la plataforma. Estas son las de la central actual.
BANDAS = [(150, 400, 'Muy bajo'), (401, 500, 'Bajo'), (501, 600, 'Medio'), (601, 950, 'Alto')]

SECTORES = [('fin', 'Sector financiero'), ('real', 'Sector real'), ('coop', 'Sector cooperativo'), ('tel', 'Sector telcos')]
COLS = ('vig', 'cer', 'pri', 'cod', 'cuota', 'mora')   # vigentes, cerrados, principal, codeudor y otros, valor cuota*, saldo en mora* (*en miles)
MAYOR_MORA = ['Normal', 'Mora 30', 'Mora 60', 'Mora 90 o más', 'No se recibió / no existe']
CALIDAD = ['Principal', 'Codeudor y otros']
ESTADO_OBLIG = ['En mora', 'Castigada', 'Cartera de difícil cobro', 'Saldada castigada']
NOVEDAD_PAT = ['Primer estudio', 'Sin cambio', 'Nuevo', 'Ya no aparece', 'No registra']
RESULTADO_CONS = ['Coincide', 'No coincide – lo explicó', 'No coincide – omitió u ocultó (FIN-08)', 'No incluido en el estudio']
SALVEDAD = ('Alcance: la consulta de síntesis muestra los totales por sector y el comportamiento de pago de los últimos 12 meses. No identifica '
            'la entidad de cada obligación, la cartera castigada ni las obligaciones como codeudor en mora.')


def fmt(x):
    return f'{x:.1f}'.replace('.', ',')


def miles(n):
    return f'{int(n):,}'.replace(',', '.')


def num(v, default=0):
    try:
        return int(str(v).replace('.', '').replace(',', '').strip())
    except (TypeError, ValueError):
        return default


def ref_obligacion(o):
    """Código de una obligación del reporte detallado (solo OEA)."""
    dias = num(o.get('o_dias'))
    if o.get('o_estado') == 'Saldada castigada':
        return None                                   # informativo
    if o.get('o_calidad') == 'Codeudor y otros':
        return 'FIN-06'
    if o.get('o_estado') == 'Castigada' or dias > 180:
        return 'FIN-01'
    if dias >= 90:
        return 'FIN-02'
    return None                                       # menos de 90 días: cuenta en la barra (FIN-03)


def validar(f, t, final=False):
    if not f.get('ev_criticidad'):
        raise ValueError('Falta la criticidad del cargo.')
    av = []
    oea = f.get('es_oea') == 'Sí'
    m90 = num(f.get('fin_meses_mora_90'))
    mora_tot = sum(num(f.get(f's_{k}_mora')) for k, _ in SECTORES)
    if m90 > 0 and mora_tot == 0:
        av.append('Hay meses con mora 90 o más pero el saldo en mora es 0: revise la consulta de síntesis.')
    if mora_tot > 0 and m90 == 0 and num(f.get('fin_meses_mora_30_60')) == 0:
        av.append('Hay saldo en mora pero ningún mes en mora en la barra: revise la consulta de síntesis.')
    if oea and mora_tot > 0 and not t.get('obligaciones_negativas'):
        av.append('Estudio OEA con saldo en mora y sin obligaciones registradas en la sección 8: registre el detalle del reporte detallado.')
    if oea and not t.get('patrimonios'):
        av.append('Estudio OEA sin bienes registrados: si no tiene bienes, registre una fila "No registra".')
    for p in t.get('patrimonios', []):
        if p.get('pt_estado') in ('Nuevo', 'Ya no aparece') and not p.get('pt_explicacion') and f.get('oea_inconsistencia') != 'Sí':
            av.append(f"Bien \"{p.get('pt_tipo', '')}\": marcado \"{p['pt_estado']}\" sin explicación; revise si corresponde FIN-07.")
    if f.get('fin_endeudamiento') in (None, '') and f.get('fin_ingreso') != 'No reportado':
        av.append('Falta el % de utilización del ingreso estimado (o marque el ingreso como "No reportado").')
    return av


def calificar(f, t, final=False):
    avisos = validar(f, t, final)
    oea = f.get('es_oea') == 'Sí'
    hall, info = [], list(t.get('informativos', []))

    def add(code, fuente, detalle):
        d, p, _ = CATALOGO[code]
        hall.append(dict(h_ref=code, h_descripcion=d, h_fuente=fuente, h_detalle=detalle, h_puntos=str(p)))

    # --- totales de la síntesis (en miles)
    campos = {}
    for c in COLS:
        tot = 0
        for k, _ in SECTORES:
            v = num(f.get(f's_{k}_{c}'))
            campos[f's_{k}_{c}'] = miles(v)
            tot += v
        campos[f's_tot_{c}'] = miles(tot)
    campos['cr_cuotas'] = f"${campos['s_tot_cuota']} mil"
    mora_tot = num(campos['s_tot_mora'])

    # --- comportamiento de los últimos 12 meses (barra de la síntesis)
    m90 = num(f.get('fin_meses_mora_90'))
    m3060 = num(f.get('fin_meses_mora_30_60'))
    cp, obligs = [], []
    if oea:                                                  # OEA: detalle por obligación del reporte detallado
        for o in t.get('obligaciones_negativas', []):
            code = ref_obligacion(o)
            dias = num(o.get('o_dias'))
            o = dict(o, o_ref=code or '—', o_dias_mora=f'{dias} días' if dias else '—', o_valor_mora=miles(num(o.get('o_valor'))))
            obligs.append(o)
            if code:
                add(code, 'Reporte detallado', f"{o.get('o_entidad', '')} ({o.get('o_tipo', '')}), {o.get('o_calidad', '').lower()}, "
                                               f"{o.get('o_estado', '').lower()}, {o['o_dias_mora']}, ${o['o_valor_mora']} mil")
        if m90 and not any(h['h_ref'] in ('FIN-01', 'FIN-02', 'FIN-06') for h in hall):
            avisos.append('La barra muestra mora 90 o más pero ninguna obligación del reporte detallado tiene 90 días o más: revise.')
        if m90:
            cp.append('Moras de 90 días o más: ver sección 8')
    else:                                                    # solo síntesis: FIN-01 / FIN-02 una vez por estudio
        if m90 >= MESES_FIN01:
            add('FIN-01', 'Consulta de síntesis', f'Mora 90 o más en los {m90} meses más recientes (más de 180 días); saldo en mora ${miles(mora_tot)} mil')
            cp.append(f'FIN-01 ({m90} meses seguidos con mora 90 o más)')
        elif m90 >= 1:
            meses = 'el mes más reciente' if m90 == 1 else f'los {m90} meses más recientes'
            add('FIN-02', 'Consulta de síntesis', f'Mora 90 o más en {meses}; saldo en mora ${miles(mora_tot)} mil')
            cp.append(f"FIN-02 ({m90} {'mes' if m90 == 1 else 'meses'} con mora 90 o más)")
    if m3060 >= MESES_FIN03:
        add('FIN-03', 'Consulta de síntesis', f'{m3060} meses con mora 30 o 60 en el último año')
        cp.append(f'FIN-03 ({m3060} meses con mora 30 o 60)')
    campos['cp_ref'] = ' · '.join(cp) or '—'

    # --- análisis de ingresos
    pct = f.get('fin_endeudamiento')
    if f.get('fin_ingreso') == 'No reportado' or pct in (None, ''):
        campos.update(fin_ingreso='No reportado', fin_endeudamiento='No reportado',
                      fin_endeudamiento_ref='— (sin ingreso estimado, FIN-04 no aplica)')
    else:
        p = num(pct)
        campos['fin_ingreso'] = f"${miles(num(f.get('fin_ingreso')))} mil"
        campos['fin_endeudamiento'] = f'{p} %'
        if p > LIMITE_ENDEUDAMIENTO:
            add('FIN-04', 'Consulta de síntesis', f'Utilización del ingreso estimado: {p} %')
            campos['fin_endeudamiento_ref'] = f'FIN-04 ({p} %, supera el 70 %)'
        else:
            campos['fin_endeudamiento_ref'] = '— (no supera el 70 %)'

    # --- ejecutivo / embargo (FIN-05) y ocultamiento (FIN-08)
    if f.get('fin_ejecutivo') == 'Sí':
        add('FIN-05', f.get('fin_ejecutivo_fuente', 'Antecedentes judiciales'), f.get('fin_ejecutivo_detalle', ''))
    omit = [n for k, n in (('ing', 'ingresos'), ('deu', 'deudas y cuotas'), ('mor', 'moras y reportes'), ('bie', 'bienes'))
            if str(f.get(f'c_{k}_res', '')).startswith('No coincide – omitió')]
    if omit:
        add('FIN-08', 'Consolidado financiero', f.get('fin_ocultamiento_detalle') or f"Omitió u ocultó: {', '.join(omit)}")

    # --- patrimonio (OEA, FIN-07)
    pats = []
    if oea:
        for p in t.get('patrimonios', []):
            pats.append(dict(p, pt_novedad=p.get('pt_estado', '') + (f": {p['pt_explicacion']}" if p.get('pt_explicacion') else '')))
        if f.get('oea_inconsistencia') == 'Sí':
            add('FIN-07', 'Declaración de renta y patrimonio', f.get('oea_inconsistencia_detalle', ''))

    # --- indicador de pago (informativo)
    score = num(f.get('cr_score'), None) if f.get('cr_score') not in (None, '') else None
    banda = None
    for i, (lo, hi, nom) in enumerate(BANDAS, 1):
        campos[f'cr_banda_{i}'] = f'{lo} a {hi} {nom}'
        campos[f'cr_m{i}'] = ''
        if score is not None and lo <= score <= hi:
            campos[f'cr_m{i}'] = '▲'; banda = (i, nom)
    campos['cr_score'] = str(score) if score is not None else 'No reportado'
    if score is not None:
        info.insert(0, dict(i_situacion='Indicador de pago (score)', i_detalle=f"{score} – banda {banda[1] if banda else 'fuera de rango'}",
                            i_tratamiento='Informativo: no suma puntos.'))
    if banda and banda[0] == 1 and not hall:
        avisos.append('ALR-FIN-01: score en la banda más baja sin ningún código FIN marcado. Revise el reporte antes de cerrar.')

    # --- puntaje, fórmula, nivel
    nums = [int(h['h_puntos']) for h in hall]
    bruto = sum(nums)
    puntaje = float(min(100, bruto))
    b, m = UMBRALES[f['ev_criticidad']]
    nivel = 'BAJO' if puntaje <= b else 'MEDIO' if puntaje <= m else 'ALTO'
    if not nums:
        formula = 'Sin hallazgos que sumen puntos'
    elif len(nums) == 1:
        formula = f"{hall[0]['h_ref']}: {nums[0]} de 100 posibles"
    else:
        formula = ' + '.join(map(str, nums)) + (f' = {bruto} → 100 (tope máximo)' if bruto > 100 else f' = {bruto} de 100 posibles')
    marca = f'▲ {fmt(puntaje)} puntos'
    campos.update(srv_puntaje=fmt(puntaje), srv_formula=formula,
                  sem_bajo=marca if nivel == 'BAJO' else '', sem_medio=marca if nivel == 'MEDIO' else '', sem_alto=marca if nivel == 'ALTO' else '',
                  cr_salvedad='' if oea else SALVEDAD, sec_concepto='10' if oea else '8')
    oea_tab = []
    if oea:
        oea_tab = [dict(oea_negativas_nota='' if obligs else 'No registra obligaciones con reporte negativo en el reporte detallado.',
                        oea_estudio_anterior=f.get('oea_estudio_anterior') or 'Primer estudio',
                        oea_observaciones=f.get('oea_observaciones', ''))]
    return dict(campos=campos, hallazgos=hall, informativos=info, obligaciones_negativas=obligs, patrimonios=pats, oea=oea_tab,
                nivel=nivel, puntaje=puntaje, critico=False, avisos=avisos)


# ---------------------------------------------------------------- casos de prueba
def S(fin=(1, 3, 4, 0, 500, 0), real=(0, 0, 0, 0, 0, 0), coop=(0, 0, 0, 0, 0, 0), tel=(1, 2, 3, 0, 80, 0)):
    d = {}
    for k, vals in (('fin', fin), ('real', real), ('coop', coop), ('tel', tel)):
        d.update({f's_{k}_{c}': str(v) for c, v in zip(COLS, vals)})
    return d


def F(crit='Estándar', oea='No', m90=0, m3060=0, pct=25, score=780, sect=None, **kw):
    d = dict(ev_criticidad=crit, es_oea=oea, fin_meses_mora_90=str(m90), fin_meses_mora_30_60=str(m3060), fin_ingreso='3000',
             fin_endeudamiento=str(pct), cr_score=str(score), c_ing_res='Coincide', c_deu_res='Coincide', c_mor_res='Coincide', c_bie_res='Coincide')
    d.update(S(**(sect or {})))
    d.update(kw)
    return d


def OB(ent, est, dias, val, cal='Principal', tipo='CTC'):
    return dict(o_entidad=ent, o_tipo=tipo, o_calidad=cal, o_estado=est, o_dias=str(dias), o_valor=str(val))


def PT(tipo, est, exp=''):
    return dict(pt_tipo=tipo, pt_ubicacion='Ubicación de ejemplo', pt_matricula='000-000', pt_estado=est, pt_explicacion=exp)


CASOS = {
    'F1 Sin novedades (Estándar)': (F(), {}),
    'F2 Tres meses con mora 30 o 60 (Estándar)': (F(m3060=3, sect=dict(tel=(1, 2, 3, 0, 80, 60))), {}),
    'F3 Mora 90 o más en 2 meses + moras repetidas (Estándar)': (F(m90=2, m3060=3, sect=dict(tel=(1, 0, 1, 0, 0, 386))), {}),
    'F4 Mora 90 o más en 6 meses seguidos (Sensible)': (F('Sensible', m90=6, sect=dict(real=(1, 0, 1, 0, 0, 2400))), {}),
    'F5 Endeudamiento 78 % (Estándar)': (F(pct=78), {}),
    'F6 Embargo vigente + ocultó la mora en la visita (Crítico)': (
        F('Crítico', m90=1, sect=dict(tel=(1, 0, 1, 0, 0, 150)), fin_ejecutivo='Sí', fin_ejecutivo_detalle='Embargo de salario vigente (proceso ejecutivo)',
          c_mor_res='No coincide – omitió u ocultó (FIN-08)'), {}),
    'F7 OEA: castigada + codeudor en mora + patrimonio inconsistente (Estándar)': (
        F(oea='Sí', m90=6, sect=dict(real=(2, 1, 2, 1, 0, 3100)), oea_inconsistencia='Sí',
          oea_inconsistencia_detalle='Vehículo nuevo de alto valor que no se explica con el ingreso estimado'),
        dict(obligaciones_negativas=[OB('Almacén de ejemplo', 'Castigada', 400, 1900, tipo='CAU'),
                                     OB('Cooperativa de ejemplo', 'En mora', 120, 1200, cal='Codeudor y otros', tipo='CAC')],
             patrimonios=[PT('Vehículo', 'Nuevo')])),
    'F8 Score muy bajo sin códigos (Estándar)': (F(score=380), {}),
    'F9 OEA sin reportes negativos, primer estudio (Sensible)': (F('Sensible', oea='Sí'), dict(patrimonios=[PT('Apartamento', 'Primer estudio')])),
}

if __name__ == '__main__':
    for n, (f, t) in CASOS.items():
        r = calificar(f, t)
        print(n, '|', r['campos']['srv_puntaje'], r['nivel'], '|', r['campos']['srv_formula'], '|', [h['h_ref'] for h in r['hallazgos']], '|', r['avisos'])
