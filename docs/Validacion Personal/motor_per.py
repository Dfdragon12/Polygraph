"""Motor de referencia – Verificación de referencias personales (reglas acordadas el 06/10/2026).
Especificación ejecutable para el código de la plataforma: el Java debe dar los mismos resultados."""
import re

CATALOGO = {  # código: (descripción, puntos, acumula)
    'PER-01': ('Referencia falsa: no conoce al evaluado o no es la persona registrada', 70, 'Por referencia'),
    'PER-02': ('La referencia da información que contradice lo verificado (trabajo, residencia)', 40, 'Por referencia'),
    'PER-03': ('Concepto desfavorable: promedio de la escala menor a 3, o la referencia expresa reservas', 50, 'Por referencia'),
    'PER-05': ('No verificable: no hay 2 referencias válidas y contactables dentro de 4 días hábiles', 45, 'Una vez por estudio'),
    'PER-06': ('Referencia presentada sin cumplir los requisitos (familiar, conviviente, menos de 1 año o sin contacto en 30 días)', 20, 'Una vez por estudio'),
}
ALERTAS = {'ALR-PER-01': 'La referencia no contesta (causa ajena al evaluado; se pasa a la siguiente)'}
SELECCIONABLES = ['PER-01', 'PER-02', 'PER-03']   # en cada referencia validada; PER-05 y PER-06 los genera la plataforma
UMBRALES = {'Estándar': (30, 60), 'Sensible': (25, 50), 'Crítico': (20, 40)}  # modelo v1.8, sección 3
PESO_AVANZADO = 4
REFERENCIAS_REQUERIDAS = 2   # Estudio Avanzado (el candidato registra 4)
ATRIBUTOS = ['Responsabilidad', 'Honestidad', 'Respeto', 'Puntualidad', 'Estabilidad familiar']
ESTADO_DECLARADA = ['Validada', 'Descartada – no cumple requisitos', 'Descartada – no contesta', 'Descartada – número errado no corregido',
                    'No contactada (no fue necesaria)']
COINCIDE = ['Coincide', 'No coincide', 'No sabe']


def fmt(x):
    return f'{x:.1f}'.replace('.', ',')


def promedio(r):
    vals = [int(r[f'r_a{i}']) for i in range(1, 6) if str(r.get(f'r_a{i}', '')).isdigit()]
    return sum(vals) / len(vals) if vals else None


def alertas_registro(f, t):
    """Lo que la plataforma revisa al registrar y muestra al analista antes de llamar."""
    av, tels = [], {}
    cand = re.sub(r'\D', '', f.get('ev_telefono', ''))
    for i, d in enumerate(t.get('referencias_declaradas', []), 1):
        tel = re.sub(r'\D', '', d.get('rd_telefono', ''))
        if len(tel) != 10:
            av.append(f'Referencia declarada {i}: el teléfono no tiene 10 dígitos.')
        if tel and tel == cand:
            av.append(f'Referencia declarada {i}: el teléfono es el mismo del candidato.')
        if tel in tels:
            av.append(f'Referencias declaradas {tels[tel]} y {i}: tienen el mismo teléfono.')
        tels.setdefault(tel, i)
    return av


def validar(f, t, final=False):
    if not f.get('ev_criticidad'):
        raise ValueError('Falta la criticidad del cargo.')
    av = alertas_registro(f, t)
    for r in t.get('referencias', []):
        p = promedio(r)
        if p is not None and p < 3 and r.get('r_ref') != 'PER-03':
            av.append(f"Referencia {r['r_numero']}: promedio {fmt(p)} (menor a 3); la plataforma suma PER-03.")
        if r.get('r_trabajo_coincide') == 'No coincide' or r.get('r_residencia_coincide') == 'No coincide':
            if r.get('r_ref') != 'PER-02':
                av.append(f"Referencia {r['r_numero']}: hay datos que no coinciden; revise si corresponde PER-02.")
    if len(t.get('referencias', [])) < REFERENCIAS_REQUERIDAS:
        av.append(f'Hay menos de {REFERENCIAS_REQUERIDAS} referencias validadas: se suma PER-05. Confirme que venció el plazo de 4 días hábiles.')
    return av


def calificar(f, t, final=False):
    refs = [dict(r, r_numero=str(i)) for i, r in enumerate(t.get('referencias', []), 1)]
    t = dict(t, referencias=refs)
    avisos = validar(f, t, final)
    hall = []

    def add(code, fuente, detalle):
        d, p, _ = CATALOGO[code]
        hall.append(dict(h_ref=code, h_descripcion=d, h_fuente=fuente, h_detalle=detalle, h_puntos=str(p)))
    for r in refs:
        p = promedio(r)
        codes = []
        if r.get('r_ref') in SELECCIONABLES:
            codes.append(r['r_ref'])
        if p is not None and p < 3 and 'PER-03' not in codes:
            codes.append('PER-03')
        for c in codes:
            add(c, f"Referencia N° {r['r_numero']}", f"{r.get('r_nombre', '')} ({r.get('r_parentesco', '')})")
        r['r_promedio'] = fmt(p) if p is not None else '—'
        r['r_calificacion'] = ', '.join(codes) or '—'
    if len(refs) < REFERENCIAS_REQUERIDAS:
        add('PER-05', 'Referencias personales', f'{len(refs)} de {REFERENCIAS_REQUERIDAS} referencias validadas')
    nreq = sum(1 for d in t.get('referencias_declaradas', []) if d.get('rd_estado') == 'Descartada – no cumple requisitos')
    if nreq:
        add('PER-06', 'Referencias declaradas', f'{nreq} referencia(s) descartada(s) por no cumplir los requisitos (cuenta una vez)')
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
    ps = [promedio(r) for r in refs if promedio(r) is not None]
    campos = dict(srv_puntaje=fmt(puntaje), srv_formula=formula,
                  sem_bajo=marca if nivel == 'BAJO' else '', sem_medio=marca if nivel == 'MEDIO' else '', sem_alto=marca if nivel == 'ALTO' else '',
                  per_promedio_general=fmt(sum(ps) / len(ps)) if ps else '—',
                  ref_nota='' if refs else 'No fue posible validar referencias personales.')
    return dict(campos=campos, hallazgos=hall, referencias=refs, nivel=nivel, puntaje=puntaje, critico=False, avisos=avisos)


def R(nombre='Referencia de ejemplo', esc=(5, 5, 5, 5, 5), ref='—', trab='Coincide', res='Coincide'):
    d = dict(r_nombre=nombre, r_parentesco='Amigo', r_ref=ref, r_trabajo_coincide=trab, r_residencia_coincide=res)
    d.update({f'r_a{i}': str(v) for i, v in enumerate(esc, 1)})
    return d


def D(tel, estado='Validada'):
    return dict(rd_nombre='Declarada', rd_parentesco='Amigo', rd_tiempo='5 años', rd_telefono=tel, rd_estado=estado)


OK4 = [D('3000000001'), D('3000000002'), D('3000000003', 'No contactada (no fue necesaria)'), D('3000000004', 'No contactada (no fue necesaria)')]
CASOS = {
    'P1 Dos referencias favorables (Estándar)': (dict(ev_criticidad='Estándar', ev_telefono='3100000000'), dict(referencias=[R(), R()], referencias_declaradas=OK4)),
    'P2 Una referencia con promedio 2,6 (Estándar)': (dict(ev_criticidad='Estándar', ev_telefono='3100000000'),
        dict(referencias=[R(esc=(3, 2, 3, 2, 3)), R()], referencias_declaradas=OK4)),
    'P3 Referencia falsa + otra descartada por requisitos (Estándar)': (dict(ev_criticidad='Estándar', ev_telefono='3100000000'),
        dict(referencias=[R(ref='PER-01'), R()], referencias_declaradas=[D('3000000001', 'Descartada – no cumple requisitos'), D('3000000002'), D('3000000003'), D('3000000004', 'No contactada (no fue necesaria)')])),
    'P4 Solo una referencia validada (Sensible)': (dict(ev_criticidad='Sensible', ev_telefono='3100000000'),
        dict(referencias=[R()], referencias_declaradas=[D('3000000001'), D('3000000002', 'Descartada – no contesta'), D('3000000003', 'Descartada – no contesta'), D('3000000004', 'Descartada – número errado no corregido')])),
    'P5 Ambas contradicen lo verificado (Crítico)': (dict(ev_criticidad='Crítico', ev_telefono='3100000000'),
        dict(referencias=[R(ref='PER-02', trab='No coincide'), R(ref='PER-02', res='No coincide')], referencias_declaradas=OK4)),
    'P6 Teléfonos repetidos al registrar, referencias favorables (Estándar)': (dict(ev_criticidad='Estándar', ev_telefono='3100000000'),
        dict(referencias=[R(), R()], referencias_declaradas=[D('3183504618'), D('3183504618'), D('31835046'), D('3100000000')])),
}

if __name__ == '__main__':
    for n, (f, t) in CASOS.items():
        r = calificar(f, t)
        print(n, '|', r['campos']['srv_puntaje'], r['nivel'], '|', r['campos']['srv_formula'], '|', [h['h_ref'] for h in r['hallazgos']], r['avisos'])
