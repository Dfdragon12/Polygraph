package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.BulkUploadResponse;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.modules.trabajos.service.ProcesadorTrabajo;
import com.polygraph.erp.modules.trabajos.service.ResultadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.*;

/** Carga masiva de solicitudes desde Excel, por la cola.
 *
 *  Antes corría entera dentro del request HTTP y dentro de UNA transacción de SolicitudService:
 *  el usuario esperaba con la conexión abierta, se retenía una conexión de BD todo el tiempo y,
 *  si una fila fallaba dentro de un servicio @Transactional, la transacción quedaba marcada
 *  rollback-only y se perdían TODAS las filas (UnexpectedRollbackException al final). Ahora:
 *
 *  · El request solo guarda el archivo en disco (streaming, sin cargarlo en RAM) y responde 202.
 *  · Cada fila es su propia transacción (crearSolicitud se llama a través del proxy de Spring):
 *    una fila mala no deshace las buenas, y la conexión se devuelve al pool entre fila y fila.
 *  · El pool CARGA_MASIVA tiene 1 hilo por defecto: a lo sumo un libro de Excel abierto en RAM a
 *    la vez, y un tope de filas (app.trabajos.max-filas-carga-masiva).
 *  · Sin reintento automático (max-intentos 1): no es idempotente. Si el admin la reintenta a
 *    mano, las filas ya creadas se rechazan solas por la regla de duplicados de 3 meses. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CargaMasivaProcesador implements ProcesadorTrabajo {

    private final SolicitudService solicitudService;
    private final ProcesoRepository procesoRepository;
    private final ColaTrabajosService cola;
    private final AlmacenTrabajos almacen;
    private final UsuarioRepository usuarioRepository;
    private final PropiedadesTrabajos propiedades;
    private final CargaMasivaValidador validador;

    @Override
    public TipoTrabajo tipo() {
        return TipoTrabajo.CARGA_MASIVA;
    }

    public TrabajoResponse solicitar(MultipartFile archivo, UserDetails userDetails) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ApiException("El archivo está vacío.", HttpStatus.BAD_REQUEST);
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new ApiException("Solo se aceptan archivos .xlsx", HttpStatus.BAD_REQUEST);
        }
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        UUID uuid = UUID.randomUUID();
        String ruta = almacen.guardarEntrada(uuid, archivo, ".xlsx");
        try {
            TrabajoAsincrono trabajo = cola.encolar(tipo(), usuario, Map.of("rutaEntrada", ruta, "nombreArchivo", nombre), uuid);
            return cola.aRespuesta(trabajo);
        } catch (RuntimeException e) {
            almacen.borrar(ruta);
            throw e;
        }
    }


    /** Columnas de la plantilla (NS-12) y los nombres alternos que se aceptan, sin importar mayúsculas, tildes, espacios ni guiones bajos. */
    static final Map<String, List<String>> COLUMNAS = new LinkedHashMap<>();
    static {
        COLUMNAS.put("cedula", List.of("cedula", "documento"));
        COLUMNAS.put("nombres", List.of("nombres", "nombre"));
        COLUMNAS.put("apellidos", List.of("apellidos", "apellido"));
        COLUMNAS.put("cargo", List.of("cargo"));
        COLUMNAS.put("criticidad", List.of("criticidadcargo", "criticidaddelcargo", "criticidad"));
        COLUMNAS.put("celular", List.of("celular", "telefono"));
        COLUMNAS.put("email", List.of("email", "correo", "correoelectronico"));
        COLUMNAS.put("ciudad", List.of("ciudad"));
        COLUMNAS.put("direccion", List.of("direccion", "direccionvisita", "direccionlavisita"));
        COLUMNAS.put("servicio", List.of("servicio", "tiposervicio"));
    }
    /** La dirección solo se exige cuando el servicio de la fila la pide; el resto de columnas deben existir en el encabezado. */
    private static final Set<String> OPCIONALES_EN_ENCABEZADO = Set.of("direccion");

    @Override
    public ResultadoTrabajo procesar(TrabajoAsincrono trabajo, Map<String, Object> parametros, ContextoTrabajo contexto) {
        String ruta = (String) parametros.get("rutaEntrada");
        Path archivo = almacen.resolver(ruta);
        if (!almacen.existe(ruta)) {
            throw new ApiException("El archivo de la carga ya no está disponible; vuelve a subirlo.", HttpStatus.GONE);
        }
        String email = trabajo.getUsuario().getEmail();

        // Catálogo una sola vez (antes era un findAll() por cada fila del Excel).
        Map<String, Proceso> procesosPorNombre = new HashMap<>();
        for (Proceso p : procesoRepository.findAll()) {
            if (p.getNombreProceso() != null) {
                procesosPorNombre.putIfAbsent(CargaMasivaValidador.normalizar(p.getNombreProceso()), p);
            }
        }

        List<CargaMasivaValidador.FilaCarga> filas = leerFilas(archivo, contexto);
        almacen.borrar(ruta);

        // NS-12: se revisa TODO el archivo antes de crear nada. Si algo falla, no se crea ninguna solicitud.
        var revision = validador.validar(filas, email, procesosPorNombre);
        if (!revision.valido()) {
            long filasConError = revision.errores().stream().filter(e -> e.fila() > 0).count();
            log.info("Carga masiva {} rechazada: {} filas con error de {}", trabajo.getUuid(), filasConError, filas.size());
            return ResultadoTrabajo.datos(new BulkUploadResponse(0, (int) filasConError, revision.errores(),
                    "No se creó ninguna solicitud. Corrige lo que se indica y vuelve a subir el archivo completo."));
        }

        // Una sola transacción: si algo falla ya con todo validado, tampoco queda nada a medias.
        int creadas = solicitudService.crearLote(revision.solicitudes(), email);
        log.info("Carga masiva {}: {} solicitudes creadas", trabajo.getUuid(), creadas);
        return ResultadoTrabajo.datos(new BulkUploadResponse(creadas, 0, List.of(), creadas + " solicitudes creadas."));
    }

    /** Lee el Excel por NOMBRE de columna (no por posición). Falta una columna obligatoria → se rechaza el archivo entero. */
    private List<CargaMasivaValidador.FilaCarga> leerFilas(Path archivo, ContextoTrabajo contexto) {
        try (Workbook workbook = WorkbookFactory.create(archivo.toFile(), null, true)) {
            Sheet hoja = workbook.getSheetAt(0);
            int ultima = hoja.getLastRowNum();
            int max = propiedades.getMaxFilasCargaMasiva();
            if (ultima > max) {
                throw new ApiException("El archivo tiene " + ultima + " filas y el máximo por carga es " + max
                        + ". Divídelo en varios archivos.", HttpStatus.BAD_REQUEST);
            }
            Map<String, Integer> indice = indiceDeColumnas(hoja.getRow(0));

            List<CargaMasivaValidador.FilaCarga> filas = new ArrayList<>();
            for (int i = 1; i <= ultima; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null || vacia(fila, indice)) continue;
                filas.add(new CargaMasivaValidador.FilaCarga(i + 1,
                        valor(fila, indice, "cedula"), valor(fila, indice, "nombres"), valor(fila, indice, "apellidos"), valor(fila, indice, "celular"),
                        valor(fila, indice, "email"), valor(fila, indice, "ciudad"), valor(fila, indice, "direccion"), valor(fila, indice, "cargo"),
                        valor(fila, indice, "criticidad"), valor(fila, indice, "servicio")));
                contexto.progreso(i * 50 / Math.max(1, ultima));
            }
            if (filas.isEmpty()) throw new ApiException("El archivo no tiene filas con datos.", HttpStatus.BAD_REQUEST);
            return filas;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("No se pudo leer el archivo Excel: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private static Map<String, Integer> indiceDeColumnas(Row encabezado) {
        if (encabezado == null) throw new ApiException("El archivo no tiene encabezado. Descarga la plantilla y úsala.", HttpStatus.BAD_REQUEST);
        Map<String, Integer> porNombre = new HashMap<>();
        for (Cell c : encabezado) {
            if (c.getCellType() != CellType.STRING) continue;
            porNombre.putIfAbsent(CargaMasivaValidador.normalizar(c.getStringCellValue()).replaceAll("[^a-z0-9]", ""), c.getColumnIndex());
        }
        Map<String, Integer> indice = new HashMap<>();
        List<String> faltan = new ArrayList<>();
        COLUMNAS.forEach((clave, alternos) -> {
            alternos.stream().map(porNombre::get).filter(Objects::nonNull).findFirst().ifPresentOrElse(
                    col -> indice.put(clave, col), () -> { if (!OPCIONALES_EN_ENCABEZADO.contains(clave)) faltan.add(clave); });
        });
        if (!faltan.isEmpty()) {
            throw new ApiException("Al archivo le faltan columnas: " + String.join(", ", faltan) + ". Descarga la plantilla actualizada y úsala.", HttpStatus.BAD_REQUEST);
        }
        return indice;
    }

    private static boolean vacia(Row fila, Map<String, Integer> indice) {
        return indice.keySet().stream().allMatch(k -> valor(fila, indice, k).isBlank());
    }

    private static String valor(Row fila, Map<String, Integer> indice, String clave) {
        Integer col = indice.get(clave);
        return col == null ? "" : celda(fila, col);
    }

    private static String celda(Row fila, int columna) {
        Cell celda = fila.getCell(columna);
        if (celda == null) return "";
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            default -> "";
        };
    }
}
