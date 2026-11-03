package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.gestor.service.ReglasEnlaceProveedor.Estado;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** PF-09 · Lo que un enlace de proveedor puede y no puede hacer, y cuándo deja de servir. */
class ReglasEnlaceProveedorTest {

    private static final long EV = 44;
    private static final String F = "/api/v1/gestor/formularios/subproceso/";

    @Test
    void tokenEsAleatorioLargoYSoloSeGuardaSuHuella() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = ReglasEnlaceProveedor.nuevoToken();
            assertTrue(ReglasEnlaceProveedor.tokenBienFormado(t));
            assertTrue(t.length() >= 43, "256 bits en base64 sin relleno");
            assertTrue(vistos.add(t), "no se repite");
            assertEquals(64, ReglasEnlaceProveedor.huella(t).length());
            assertNotEquals(t, ReglasEnlaceProveedor.huella(t));
        }
        assertEquals(ReglasEnlaceProveedor.huella("abc"), ReglasEnlaceProveedor.huella("abc"));
    }

    @Test
    void basuraNoPasaPorToken() {
        assertFalse(ReglasEnlaceProveedor.tokenBienFormado(null));
        assertFalse(ReglasEnlaceProveedor.tokenBienFormado(""));
        assertFalse(ReglasEnlaceProveedor.tokenBienFormado("corto"));
        assertFalse(ReglasEnlaceProveedor.tokenBienFormado("a".repeat(101)));
        assertFalse(ReglasEnlaceProveedor.tokenBienFormado("abc def ghi jkl mno pqr stu"));
        assertFalse(ReglasEnlaceProveedor.tokenBienFormado("' OR 1=1 --xxxxxxxxxxxxxxxxxxxxxx"));
    }

    @Test
    void estadoDelEnlace() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-11-02T10:00:00-05:00");
        assertEquals(Estado.VIGENTE, ReglasEnlaceProveedor.estado(ahora.plusHours(1), null, null, null, ahora));
        assertEquals(Estado.VENCIDO, ReglasEnlaceProveedor.estado(ahora.minusSeconds(1), null, null, null, ahora));
        assertEquals(Estado.REVOCADO, ReglasEnlaceProveedor.estado(ahora.plusHours(1), ahora, null, null, ahora));
        assertEquals(Estado.ENVIADO, ReglasEnlaceProveedor.estado(ahora.plusHours(1), null, ahora, "ENVIADO", ahora));
        assertEquals(Estado.REEMPLAZADO, ReglasEnlaceProveedor.estado(ahora.plusHours(1), null, ahora, "REEMPLAZADO", ahora));
        assertEquals(72, ReglasEnlaceProveedor.VIGENCIA.toHours());
    }

    @Test
    void abreSoloLaPantallaDeSuEvaluacion() {
        String base = F + EV;
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", base, EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("PUT", base, EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", base + "/preguntas-historial", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", base + "/candidato", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", base + "/soportes/9", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("POST", base + "/documento-pdf", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("POST", base + "/foto", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("POST", base + "/foto/bloquear", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", base + "/foto/imagen", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("PATCH", "/api/v1/gestor/asignaciones/" + EV + "/completar", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/catalogo/frases-estandar", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/proveedor/sesion", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/trabajos/123e4567-e89b-12d3-a456-426614174000", EV));
        assertTrue(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/trabajos/123e4567-e89b-12d3-a456-426614174000/resultado", EV));
    }

    @Test
    void ningunaOtraEvaluacionNiOtraPantalla() {
        String otra = F + (EV + 1);
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", otra, EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("PUT", otra, EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("PATCH", "/api/v1/gestor/asignaciones/" + (EV + 1) + "/completar", EV));
        // lo que un poligrafista con usuario sí podría, pero un enlace no
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/gestor/asignaciones", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/gestor/solicitudes", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/auth/me", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/gestor/formularios/subproceso/" + EV + "/documento", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("PATCH", base(EV) + "/soportes/9/revision", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("DELETE", "/api/v1/gestor/asignaciones/" + EV + "/enlace-proveedor", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("POST", "/api/v1/gestor/asignaciones/" + EV + "/enlace-proveedor", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("POST", F + EV + "/foto/reemplazo", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("PUT", "/api/v1/catalogo/frases-estandar/obs_vacia", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("DELETE", base(EV), EV));
    }

    @Test
    void noSeColanRutasDisfrazadas() {
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + EV + "/../" + (EV + 1), EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + "%34" + "4", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + EV + ";x=1", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + "044", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + EV + "/", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + EV + "//candidato", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", "/api/v1/trabajos/no-es-un-uuid", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida("GET", F + EV + "/soportes/9/../10", EV));
        assertFalse(ReglasEnlaceProveedor.rutaPermitida(null, F + EV, EV));
    }

    private static String base(long id) { return F + id; }
}
