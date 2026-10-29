package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.entity.MotivoSubproceso;
import com.polygraph.erp.modules.catalogo.repository.MotivoSubprocesoRepository;
import com.polygraph.erp.shared.enums.TipoMotivo;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MotivoSubprocesoServiceTest {

    private final MotivoSubprocesoRepository repo = mock(MotivoSubprocesoRepository.class);
    private final MotivoSubprocesoService service = new MotivoSubprocesoService(repo);

    private MotivoSubproceso motivo(int id, TipoMotivo tipo, String nombre, boolean otro, boolean activo) {
        MotivoSubproceso m = MotivoSubproceso.builder().id(id).tipo(tipo).nombre(nombre).requiereTexto(otro).activo(activo).build();
        when(repo.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    @Test
    void unMotivoDeLaListaSeAceptaTalCual() {
        motivo(1, TipoMotivo.DEVOLUCION, "Informe incompleto", false, true);
        var r = service.resolver(1, TipoMotivo.DEVOLUCION, null);
        assertEquals("Informe incompleto", r.descripcion());
        assertNull(r.texto());
    }

    @Test
    void conTextoAdicionalSeAgrega() {
        motivo(1, TipoMotivo.DEVOLUCION, "Informe incompleto", false, true);
        assertEquals("Informe incompleto — falta la sección 4", service.resolver(1, TipoMotivo.DEVOLUCION, " falta la sección 4 ").descripcion());
    }

    @Test
    void otroExigeTexto() {
        motivo(2, TipoMotivo.CANCELACION, "Otro", true, true);
        ApiException e = assertThrows(ApiException.class, () -> service.resolver(2, TipoMotivo.CANCELACION, "  "));
        assertTrue(e.getMessage().contains("Otro"));
        assertEquals("Otro: lo pidió el cliente por teléfono", service.resolver(2, TipoMotivo.CANCELACION, "lo pidió el cliente por teléfono").descripcion());
    }

    @Test
    void sinMotivoOConOtroTipoOInactivoSeRechaza() {
        motivo(3, TipoMotivo.REPROGRAMACION, "Falla técnica o de conexión", false, true);
        motivo(4, TipoMotivo.CANCELACION, "Solicitud duplicada", false, false);
        assertThrows(ApiException.class, () -> service.resolver(null, TipoMotivo.CANCELACION, null));
        assertThrows(ApiException.class, () -> service.resolver(3, TipoMotivo.CANCELACION, null));   // es de otra lista
        assertThrows(ApiException.class, () -> service.resolver(4, TipoMotivo.CANCELACION, null));   // desactivado
        assertThrows(ApiException.class, () -> service.resolver(99, TipoMotivo.CANCELACION, null));  // no existe
    }
}
