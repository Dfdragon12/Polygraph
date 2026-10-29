package com.polygraph.erp.modules.solicitudes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.catalogo.entity.PreguntaPoligrafia;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.PreguntaPoligrafiaRepository;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioDatosSolicitud;
import com.polygraph.erp.modules.servicios.entity.ServicioPreguntaPoligrafia;
import com.polygraph.erp.modules.servicios.repository.ServicioDatosSolicitudRepository;
import com.polygraph.erp.modules.servicios.repository.ServicioPreguntaPoligrafiaRepository;
import com.polygraph.erp.modules.solicitudes.dto.TemaPoligrafiaRequest;
import com.polygraph.erp.modules.solicitudes.formato.FormatoSolicitud;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SolicitudExtraService — formato adicional y temas de poligrafía")
class SolicitudExtraServiceTest {

    @Mock private PreguntaPoligrafiaRepository preguntaRepository;
    @Mock private ServicioPreguntaPoligrafiaRepository servicioPreguntaRepository;
    @Mock private ServicioDatosSolicitudRepository datosRepository;
    @Mock private ProcesoTipoProgresoRepository pasoRepository;

    private SolicitudExtraService service;
    private final List<ServicioPreguntaPoligrafia> temasGuardados = new ArrayList<>();
    private final List<ServicioDatosSolicitud> datosGuardados = new ArrayList<>();

    private Proceso preEmpleo, rutina, especifica, antecedentes;

    @BeforeEach
    void setUp() {
        service = new SolicitudExtraService(preguntaRepository, servicioPreguntaRepository, datosRepository, pasoRepository, new ObjectMapper());
        preEmpleo = proceso(9, "Poligrafia Pre Empleo", FormatoSolicitud.PRE_EMPLEO);
        rutina = proceso(10, "Poligrafía de Rutina", FormatoSolicitud.RUTINA);
        especifica = proceso(11, "Poligrafia Especifica", FormatoSolicitud.ESPECIFICA);
        antecedentes = proceso(1, "Antecedentes", null);

        when(pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(any())).thenReturn(List.of());
        when(preguntaRepository.findById(1)).thenReturn(Optional.of(tema(1, "Robos", false, true)));
        when(preguntaRepository.findById(2)).thenReturn(Optional.of(tema(2, "Sobornos", false, true)));
        when(preguntaRepository.findById(3)).thenReturn(Optional.of(tema(3, "Lavado de dinero", false, true)));
        when(preguntaRepository.findById(4)).thenReturn(Optional.of(tema(4, "Drogas", false, true)));
        when(preguntaRepository.findById(5)).thenReturn(Optional.of(tema(5, "Fraude", false, true)));
        when(preguntaRepository.findById(14)).thenReturn(Optional.of(tema(14, "Otros ¿Cuál?", true, true)));
        when(preguntaRepository.findById(99)).thenReturn(Optional.of(tema(99, "Retirado", false, false)));
        when(servicioPreguntaRepository.save(any(ServicioPreguntaPoligrafia.class))).thenAnswer(inv -> {
            temasGuardados.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(datosRepository.save(any(ServicioDatosSolicitud.class))).thenAnswer(inv -> {
            datosGuardados.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    private static Proceso proceso(int id, String nombre, FormatoSolicitud formato) {
        return Proceso.builder().idProceso(id).nombreProceso(nombre).formatoSolicitud(formato).build();
    }

    private static PreguntaPoligrafia tema(int id, String nombre, boolean requiereDetalle, boolean activo) {
        return PreguntaPoligrafia.builder().id(id).nombre(nombre).requiereDetalle(requiereDetalle).activo(activo).orden(id).build();
    }

    private static Map<String, String> datosRutina() {
        Map<String, String> d = new HashMap<>();
        d.put("empresa", "Cliente S.A.S.");
        d.put("fecha_prueba", "2026-11-03");
        d.put("objetivo", "Confirmar manejo de inventarios");
        d.put("solicitante_nombre", "Ana Gómez");
        d.put("solicitante_cargo", "Jefe de talento humano");
        return d;
    }

    private static Map<String, String> datosEspecifica() {
        Map<String, String> d = new HashMap<>();
        d.put("empresa_cliente", "Cliente S.A.S.");
        d.put("cantidad_poligrafias", "3");
        d.put("correo_informes", "gerencia@cliente.com");
        d.put("fecha_hechos", "2026-10-20");
        d.put("lugar_hechos", "Bodega norte");
        d.put("objetivo", "Hallar el faltante");
        d.put("descripcion_hechos", "Faltaron 10 cajas");
        d.put("solicitante_nombre", "Ana Gómez");
        d.put("solicitante_cargo", "Gerente");
        return d;
    }

    private void esperar400(Runnable accion, String contiene) {
        assertThatThrownBy(accion::run).isInstanceOf(ApiException.class).hasMessageContaining(contiene)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Poligrafía es un servicio (o lleva un subproceso) con la palabra «poligrafía», con o sin tilde")
    void detectaPoligrafia() {
        assertThat(service.admitePreguntas(preEmpleo)).isTrue();
        assertThat(service.admitePreguntas(rutina)).isTrue();
        assertThat(service.admitePreguntas(antecedentes)).isFalse();

        Proceso avanzado = proceso(16, "AVANZADO", null);
        var paso = ProcesoTipoProgreso.builder().tipoProgreso(TipoProgreso.builder().nombreProgreso("Poligrafía Específica").build()).build();
        when(pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(16)).thenReturn(List.of(paso));
        assertThat(service.admitePreguntas(avanzado)).isTrue();
    }

    @Test
    @DisplayName("Una solicitud sin poligrafía ni temas no guarda nada")
    void sinNada() {
        var extras = service.validar(List.of(antecedentes), null, null);
        service.guardar(Servicio.builder().idServicio(5).proceso(antecedentes).build(), extras);
        assertThat(temasGuardados).isEmpty();
        assertThat(datosGuardados).isEmpty();
    }

    @Test
    @DisplayName("Rutina: guarda el formato y los temas elegidos, y los devuelve legibles")
    void rutinaCompleta() {
        var extras = service.validar(List.of(rutina), datosRutina(), List.of(new TemaPoligrafiaRequest(1, null), new TemaPoligrafiaRequest(14, "  Uso de recursos  ")));
        service.guardar(Servicio.builder().idServicio(5).proceso(rutina).build(), extras);

        assertThat(datosGuardados).hasSize(1);
        assertThat(datosGuardados.get(0).getFormato()).isEqualTo("RUTINA");
        assertThat(temasGuardados).extracting(ServicioPreguntaPoligrafia::getNombre).containsExactly("Robos", "Otros ¿Cuál?");
        assertThat(temasGuardados.get(1).getDetalle()).isEqualTo("Uso de recursos");

        when(datosRepository.findById(5)).thenReturn(Optional.of(datosGuardados.get(0)));
        when(servicioPreguntaRepository.findByServicio_IdServicioOrderByOrdenAscIdAsc(5)).thenReturn(temasGuardados);
        var r = service.obtener(5);
        assertThat(r.titulo()).isEqualTo("Solicitud de prueba de poligrafía de rutina");
        assertThat(r.datos()).extracting("etiqueta", "valor").contains(
                org.assertj.core.groups.Tuple.tuple("Fecha de la presentación de la prueba", "03/11/2026"),
                org.assertj.core.groups.Tuple.tuple("Empresa", "Cliente S.A.S."));
        assertThat(r.temas()).extracting("nombre", "detalle").containsExactly(
                org.assertj.core.groups.Tuple.tuple("Robos", null), org.assertj.core.groups.Tuple.tuple("Otros ¿Cuál?", "Uso de recursos"));
        assertThat(service.tablasParaWord(5).get("preguntas_cliente")).hasSize(2);
        assertThat(service.tablasParaWord(5).get("solicitud_cliente")).isNotEmpty();
    }

    @Test
    @DisplayName("Pre-empleo: exige la empresa solicitante; lo demás es opcional")
    void preEmpleo() {
        esperar400(() -> service.validar(List.of(preEmpleo), Map.of(), null), "Empresa solicitante");
        var extras = service.validar(List.of(preEmpleo), Map.of("empresa_solicitante", "Cliente S.A.S."), List.of(new TemaPoligrafiaRequest(2, null)));
        assertThat(extras.temas()).hasSize(1);
    }

    @Test
    @DisplayName("Específica: valida fecha, cantidad y correo")
    void especifica() {
        assertThat(service.validar(List.of(especifica), datosEspecifica(), null).datosPorFormato()).containsKey(FormatoSolicitud.ESPECIFICA);

        Map<String, String> fechaMala = datosEspecifica();
        fechaMala.put("fecha_hechos", "20/10/2026");
        esperar400(() -> service.validar(List.of(especifica), fechaMala, null), "fecha válida");

        Map<String, String> cantidadMala = datosEspecifica();
        cantidadMala.put("cantidad_poligrafias", "0");
        esperar400(() -> service.validar(List.of(especifica), cantidadMala, null), "número entero");

        Map<String, String> correoMalo = datosEspecifica();
        correoMalo.put("correo_informes", "gerencia-sin-arroba");
        esperar400(() -> service.validar(List.of(especifica), correoMalo, null), "correo válido");

        Map<String, String> sinObjetivo = datosEspecifica();
        sinObjetivo.remove("objetivo");
        esperar400(() -> service.validar(List.of(especifica), sinObjetivo, null), "objetivo");
    }

    @Test
    @DisplayName("Máximo 4 temas, sin repetir, solo activos, y «Otros» exige escribir cuál")
    void reglasDeTemas() {
        var cinco = List.of(new TemaPoligrafiaRequest(1, null), new TemaPoligrafiaRequest(2, null), new TemaPoligrafiaRequest(3, null),
                new TemaPoligrafiaRequest(4, null), new TemaPoligrafiaRequest(5, null));
        esperar400(() -> service.validar(List.of(preEmpleo), Map.of("empresa_solicitante", "X"), cinco), "máximo 4");
        assertThat(service.validar(List.of(preEmpleo), Map.of("empresa_solicitante", "X"), cinco.subList(0, 4)).temas()).hasSize(4);

        esperar400(() -> service.validar(List.of(preEmpleo), Map.of("empresa_solicitante", "X"),
                List.of(new TemaPoligrafiaRequest(1, null), new TemaPoligrafiaRequest(1, null))), "mismo tema");
        esperar400(() -> service.validar(List.of(preEmpleo), Map.of("empresa_solicitante", "X"), List.of(new TemaPoligrafiaRequest(99, null))), "ya no está disponible");
        esperar400(() -> service.validar(List.of(preEmpleo), Map.of("empresa_solicitante", "X"), List.of(new TemaPoligrafiaRequest(14, "  "))), "cuál es el tema");
    }

    @Test
    @DisplayName("Un servicio que no es de poligrafía no admite temas")
    void sinPoligrafiaNoAdmiteTemas() {
        esperar400(() -> service.validar(List.of(antecedentes), null, List.of(new TemaPoligrafiaRequest(1, null))), "no admite temas");
        verify(servicioPreguntaRepository, never()).save(any());
    }
}
