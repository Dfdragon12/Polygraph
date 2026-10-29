package com.polygraph.erp.modules.gestor.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.gestor.dto.GenerarLinkRequest;
import com.polygraph.erp.modules.servicios.entity.LinkCandidato;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.repository.LinkCandidatoRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Links de candidato — un gestor solo opera los de servicios de sus clientes; uno por servicio")
class LinkCandidatoGestorServiceAccesoTest {

    @Mock private LinkCandidatoRepository linkRepository;
    @Mock private ServicioRepository servicioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository hojaVidaRepository;
    @Mock private com.polygraph.erp.modules.catalogo.repository.CiudadRepository ciudadRepository;
    @Mock private com.polygraph.erp.modules.evaluados.repository.DocumentoEvaluadoRepository documentoRepository;
    @Mock private com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService almacenamientoService;
    @Mock private com.polygraph.erp.modules.auth.service.EmailService emailService;

    @InjectMocks
    private LinkCandidatoGestorService service;

    private Servicio servicioDelGestor1;
    private LinkCandidato link;

    @BeforeEach
    void setUp() {
        when(usuarioRepository.findByEmail("gestor1@test.com"))
                .thenReturn(Optional.of(Usuario.builder().idUsuario(1L).rol(Rol.GESTOR).nombre("Uno").build()));
        when(usuarioRepository.findByEmail("gestor2@test.com"))
                .thenReturn(Optional.of(Usuario.builder().idUsuario(2L).rol(Rol.GESTOR).nombre("Dos").build()));

        servicioDelGestor1 = Servicio.builder().idServicio(77).estado(EstadoServicio.PENDIENTE)
                .cliente(Cliente.builder().idGestor(1L).build()).build();
        link = LinkCandidato.builder().id(9L).servicio(servicioDelGestor1).token("t").usado(true)
                .fechaUso(LocalDateTime.now().minusDays(1)).fechaCreacion(LocalDateTime.now().minusDays(2))
                .fechaExpiracion(LocalDateTime.now()).revocado(false).intentosFallidos((short) 0).build();

        when(servicioRepository.findById(77)).thenReturn(Optional.of(servicioDelGestor1));
        when(linkRepository.findById(9L)).thenReturn(Optional.of(link));
        when(linkRepository.findByServicio_IdServicioOrderByFechaCreacionDesc(77)).thenReturn(List.of(link));
    }

    @Test
    @DisplayName("Un gestor de otro cliente no puede generar (ni borrar el link vigente), revocar, revertir, eliminar ni ver")
    void otroGestor_403() {
        for (org.junit.jupiter.api.function.Executable accion : List.<org.junit.jupiter.api.function.Executable>of(
                () -> service.generar(new GenerarLinkRequest(77), "gestor2@test.com", "127.0.0.1"),
                () -> service.revocar(9L, "gestor2@test.com"),
                () -> service.revertir(9L, "gestor2@test.com"),
                () -> service.eliminar(9L, "gestor2@test.com"),
                () -> service.enviarCorreo(9L, "gestor2@test.com"),
                () -> service.obtenerPorServicio(77, "gestor2@test.com"),
                () -> service.obtenerResumenPorServicio(77, "gestor2@test.com"))) {
            assertThatThrownBy(accion::execute)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(linkRepository, never()).deleteAll(anyList());
        verify(linkRepository, never()).save(any());
        assertThat(link.getRevocado()).isFalse();
    }

    @Test
    @DisplayName("Su gestor genera un link nuevo: el anterior se elimina y el nuevo conserva 'enviado'")
    void gestorPropio_generaYReemplaza() {
        when(linkRepository.save(any(LinkCandidato.class))).thenAnswer(inv -> inv.getArgument(0));

        service.generar(new GenerarLinkRequest(77), "gestor1@test.com", "127.0.0.1");

        verify(linkRepository).deleteAll(List.of(link));
        verify(linkRepository).save(argThat(nuevo -> Boolean.TRUE.equals(nuevo.getUsado())
                && nuevo.getFechaUso() != null && !"t".equals(nuevo.getToken())));
    }
}
