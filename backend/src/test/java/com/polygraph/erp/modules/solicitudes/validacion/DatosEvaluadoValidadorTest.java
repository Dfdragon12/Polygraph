package com.polygraph.erp.modules.solicitudes.validacion;

import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** NS-07: un mensaje claro por campo para lo que falta o está mal. */
class DatosEvaluadoValidadorTest {

    private static SolicitudRequest completa() {
        return new SolicitudRequest("1020304050", "Ana", "Pérez", "3001234567", "ana@correo.com", 1, "Analista", "ESTANDAR", null, List.of(1), null, null, null);
    }

    private static SolicitudRequest con(String cedula, String celular, String email, Integer ciudad, String cargo, String criticidad) {
        return new SolicitudRequest(cedula, "Ana", "Pérez", celular, email, ciudad, cargo, criticidad, null, List.of(1), null, null, null);
    }

    @Test
    void una_solicitud_completa_no_tiene_errores() {
        assertThat(DatosEvaluadoValidador.errores(completa())).isEmpty();
    }

    @Test
    void sin_email_celular_o_criticidad_se_rechaza_con_un_mensaje_por_campo() {
        var e = DatosEvaluadoValidador.errores(con("1020304050", null, null, 1, "Analista", null));
        assertThat(e).containsOnlyKeys("criticidadCargo", "celular", "email");
        assertThat(e.get("celular")).isEqualTo("El celular es obligatorio");
        assertThat(e.get("email")).isEqualTo("El correo es obligatorio");
        assertThat(e.get("criticidadCargo")).isEqualTo("La criticidad del cargo es obligatoria");
    }

    @Test
    void cargo_y_ciudad_son_obligatorios() {
        var e = DatosEvaluadoValidador.errores(con("1020304050", "3001234567", "a@b.co", null, "  ", "SENSIBLE"));
        assertThat(e).containsOnlyKeys("cargo", "idCiudad");
    }

    @Test
    void el_celular_tiene_10_digitos_y_empieza_por_3() {
        assertThat(DatosEvaluadoValidador.errores(con("1020304050", "2001234567", "a@b.co", 1, "X", "CRITICO"))).containsKey("celular");
        assertThat(DatosEvaluadoValidador.errores(con("1020304050", "300123456", "a@b.co", 1, "X", "CRITICO"))).containsKey("celular");
        assertThat(DatosEvaluadoValidador.errores(con("1020304050", "3001234567", "a@b.co", 1, "X", "CRITICO"))).isEmpty();
    }

    @Test
    void el_correo_debe_tener_formato_valido() {
        assertThat(DatosEvaluadoValidador.errores(con("1020304050", "3001234567", "sin-arroba", 1, "X", "CRITICO"))).containsKey("email");
        assertThat(DatosEvaluadoValidador.errores(con("1020304050", "3001234567", "a@b", 1, "X", "CRITICO"))).containsKey("email");
    }

    @Test
    void la_cedula_debe_ser_numerica() {
        assertThat(DatosEvaluadoValidador.errores(con("10.203.040", "3001234567", "a@b.co", 1, "X", "CRITICO"))).containsKey("cedula");
        assertThat(DatosEvaluadoValidador.errores(con("ABC123456", "3001234567", "a@b.co", 1, "X", "CRITICO"))).containsKey("cedula");
    }

    @Test
    void la_criticidad_solo_admite_los_tres_valores() {
        assertThat(DatosEvaluadoValidador.errores(con("1020304050", "3001234567", "a@b.co", 1, "X", "ALTA"))).containsKey("criticidadCargo");
        assertThat(DatosEvaluadoValidador.criticidad("Crítico")).isEqualTo(CriticidadCargo.CRITICO);
        assertThat(DatosEvaluadoValidador.criticidad("sensible")).isEqualTo(CriticidadCargo.SENSIBLE);
    }

    private static SolicitudRequest conDireccion(String direccion) {
        return new SolicitudRequest("1020304050", "Ana", "Pérez", "3001234567", "ana@correo.com", 1, "Analista", "ESTANDAR", direccion, List.of(1), null, null, null);
    }

    @Test
    void con_visita_la_direccion_es_obligatoria() {
        assertThat(DatosEvaluadoValidador.errores(conDireccion(null), true)).containsOnlyKeys("direccionVisita");
        assertThat(DatosEvaluadoValidador.errores(conDireccion("  "), true)).containsOnlyKeys("direccionVisita");
        assertThat(DatosEvaluadoValidador.errores(conDireccion("Cra 7"), true)).containsOnlyKeys("direccionVisita");
        assertThat(DatosEvaluadoValidador.errores(conDireccion("Cra 7 # 45-10, barrio Chapinero"), true)).isEmpty();
    }

    @Test
    void sin_visita_no_se_pide_la_direccion() {
        assertThat(DatosEvaluadoValidador.errores(conDireccion(null), false)).isEmpty();
        assertThat(DatosEvaluadoValidador.errores(conDireccion(null))).isEmpty();
    }
}
