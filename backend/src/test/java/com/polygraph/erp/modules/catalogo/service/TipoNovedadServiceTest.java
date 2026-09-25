package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.TipoNovedadRequest;
import com.polygraph.erp.modules.catalogo.entity.CategoriaNovedad;
import com.polygraph.erp.modules.catalogo.entity.TipoNovedad;
import com.polygraph.erp.modules.catalogo.repository.CategoriaNovedadRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoNovedadRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.repository.NovedadSubprocesoRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TipoNovedadService — catálogo de tipos de novedad")
class TipoNovedadServiceTest {

    @Mock private CategoriaNovedadRepository categoriaRepository;
    @Mock private TipoNovedadRepository tipoRepository;
    @Mock private NovedadSubprocesoRepository novedadRepository;
    @Mock private NotificacionService notificacionService;

    @InjectMocks
    private TipoNovedadService service;

    @Test
    @DisplayName("La clave se genera del nombre: sin tildes, mayúsculas, guion bajo y máx. 36 (deja espacio para _2)")
    void claveBase_desdeNombre() {
        assertThat(TipoNovedadService.claveBase("El candidato llegó en estado de embriaguez"))
                .isEqualTo("EL_CANDIDATO_LLEGO_EN_ESTADO_DE_EMBR");
        assertThat(TipoNovedadService.claveBase("Dirección errada / no existe")).isEqualTo("DIRECCION_ERRADA_NO_EXISTE");
        assertThat(TipoNovedadService.claveBase("¿?")).isEqualTo("NOVEDAD");
    }

    @Test
    @DisplayName("Crear un tipo con una clave que ya existe le agrega sufijo _2")
    void crearTipo_claveRepetida_sufijo() {
        CategoriaNovedad c = CategoriaNovedad.builder().idCategoriaNovedad(1).clave("OTRA").nombre("Otras").color("gray").build();
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(c));
        when(tipoRepository.existsByClave("OTRA_NOVEDAD")).thenReturn(true);
        when(tipoRepository.existsByClave("OTRA_NOVEDAD_2")).thenReturn(false);
        when(tipoRepository.findAll()).thenReturn(List.of());
        when(tipoRepository.save(any(TipoNovedad.class))).thenAnswer(inv -> inv.getArgument(0));

        var r = service.crearTipo(new TipoNovedadRequest(1, "Otra novedad", Set.of("VISITADOR"), null, null));

        assertThat(r.clave()).isEqualTo("OTRA_NOVEDAD_2");
        assertThat(r.roles()).containsExactly("VISITADOR");
    }

    @Test
    @DisplayName("Un rol que no ejecuta subprocesos (gestor) no se acepta")
    void crearTipo_rolNoEjecutor_400() {
        CategoriaNovedad c = CategoriaNovedad.builder().idCategoriaNovedad(1).clave("OTRA").nombre("Otras").color("gray").build();
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.crearTipo(new TipoNovedadRequest(1, "X", Set.of("GESTOR"), null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(tipoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un tipo que ya se usó en novedades no se elimina (hay que desactivarlo)")
    void eliminarTipo_enUso_409() {
        TipoNovedad t = TipoNovedad.builder().idTipoNovedad(9).clave("X").nombre("X").roles(Set.of(Rol.VISITADOR)).build();
        when(tipoRepository.findById(9)).thenReturn(Optional.of(t));
        when(novedadRepository.countByTipo_IdTipoNovedad(9)).thenReturn(3L);

        assertThatThrownBy(() -> service.eliminarTipo(9))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(tipoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Una categoría con tipos no se elimina")
    void eliminarCategoria_conTipos_409() {
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(CategoriaNovedad.builder().idCategoriaNovedad(1).build()));
        when(tipoRepository.countByCategoria_IdCategoriaNovedad(1)).thenReturn(2L);

        assertThatThrownBy(() -> service.eliminarCategoria(1))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
