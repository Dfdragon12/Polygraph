package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.shared.enums.Rol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** AJ-03: solo poligrafía y visita llevan cita (y por tanto pasan por PROGRAMANDO). */
class TipoProgresoLlevaCitaTest {

    private static TipoProgreso con(Rol rol) {
        TipoProgreso t = new TipoProgreso();
        t.setRolResponsable(rol);
        return t;
    }

    @Test
    void poligrafiaYVisitaLlevanCita() {
        assertThat(con(Rol.POLIGRAFISTA).llevaCita()).isTrue();
        assertThat(con(Rol.VISITADOR).llevaCita()).isTrue();
    }

    @Test
    void antecedentesValidacionesYFinancieroNoLlevanCita() {
        assertThat(con(Rol.ANALISTA_INTERNO).llevaCita()).isFalse();
    }
}
