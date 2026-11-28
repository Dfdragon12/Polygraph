import sys; sys.path.insert(0, '.')
from motor_ant import calificar
SI = dict(dec1_coincide='Sí', dec2_coincide='Sí', dec3_coincide='Sí')
CASOS = {
 'C1 Limpio (Estándar)': (dict(ev_criticidad='Estándar', ev_conduccion='No', ev_estado_doc='Vigente', **SI), {}),
 'C2 Archivados + RNMC repetida + SIMIT sin conducción (Estándar)': (
   dict(ev_criticidad='Estándar', ev_conduccion='No', ev_estado_doc='Vigente', simit_ref='LEG-17', simit_multas='2 pendientes', **SI),
   dict(listas_nacionales=[dict(ln_fuente='Medidas correctivas (RNMC)', ln_ref='LEG-18', ln_observacion='Multa tipo 2 (2021)'),
                           dict(ln_fuente='Medidas correctivas (RNMC)', ln_ref='LEG-18', ln_observacion='Multa tipo 1 (2019), en proceso')],
        procesos_privados=[dict(pp_numero='1', pp_delito='Hurto', pp_estado='Inactivo', pp_calificacion='LEG-19'),
                           dict(pp_numero='2', pp_delito='Violencia intrafamiliar', pp_estado='Inactivo', pp_calificacion='LEG-19')])),
 'C3 Crítico: sanción disciplinaria vigente (Sensible)': (
   dict(ev_criticidad='Sensible', ev_conduccion='No', ev_estado_doc='Vigente', **SI),
   dict(listas_nacionales=[dict(ln_fuente='Procuraduría', ln_ref='LEG-09', ln_observacion='Suspensión vigente hasta 2027')],
        procesos=[dict(p_numero='1', p_delito='Hurto', p_estado='Terminado', p_calificacion='LEG-21')])),
 'C4 Tope 100: proceso activo no relacionado + noticias (Crítico)': (
   dict(ev_criticidad='Crítico', ev_conduccion='No', ev_estado_doc='Vigente', dec1_coincide='No', dec2_coincide='Sí', dec3_coincide='Sí'),
   dict(listas_internacionales=[dict(li_lista='Noticias criminales', li_ref='LEG-13', li_observacion='Nota de prensa 2025')],
        procesos=[dict(p_numero='1', p_delito='Lesiones personales', p_estado='Activo', p_calificacion='LEG-11')])),
 'C5 Cargo de conducción (Estándar)': (
   dict(ev_criticidad='Estándar', ev_conduccion='Sí', ev_estado_doc='Vigente', simit_ref='LEG-17', simit_multas='3 pendientes', **SI),
   dict(runt=[dict(runt_categoria='C2', runt_estado='Vencida', runt_ref='LEG-14')])),
}
if __name__ == '__main__':
    for n, (f, t) in CASOS.items():
        r = calificar(f, t)
        print(n, '|', r['campos']['srv_puntaje'], r['nivel'], '|', r['campos']['srv_formula'], '|', [h['h_ref'] for h in r['hallazgos']], r['avisos'])
