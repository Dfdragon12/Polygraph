package com.polygraph.erp.config;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.modules.servicios.repository.CatalogoServicioRepository;
import com.polygraph.erp.shared.enums.CategoriaServicio;
import com.polygraph.erp.shared.enums.Rol;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final CatalogoServicioRepository catalogoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@polygraph.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin1234!}")
    private String adminPassword;

    @Value("${app.admin.nombre:Administrador}")
    private String adminNombre;

    @PostConstruct
    public void inicializar() {
        crearAdminSiNoExiste();
        crearUsuariosDePruebaSiNoExisten();
        if (catalogoRepository.count() > 0) {
            return;
        }
        log.info("Cargando catálogo inicial de servicios Polygraph...");
        catalogoRepository.saveAll(construirCatalogo());
        log.info("Catálogo cargado: {} servicios registrados.", catalogoRepository.count());
    }

    private List<CatalogoServicio> construirCatalogo() {
        return List.of(
                // ── PRUEBAS DE CONFIABILIDAD ────────────────────────────────────────────
                item("Polígrafo Pre-empleo",
                        "Examen poligráfico para candidatos en proceso de selección de personal.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 1),
                item("Polígrafo Rutina",
                        "Examen poligráfico periódico aplicado a empleados activos de la empresa.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 2),
                item("Polígrafo Específico",
                        "Examen poligráfico orientado a investigar un hecho o situación concreta.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 3),
                item("Verifeye",
                        "Prueba de detección de engaño mediante análisis del movimiento ocular.",
                        CategoriaServicio.PRUEBAS_CONFIABILIDAD, 4),

                // ── ESTUDIOS DE SEGURIDAD ────────────────────────────────────────────────
                item("Estudio Básico",
                        "Verificación de antecedentes judiciales y referencias básicas del candidato.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 1),
                item("Estudio Avanzado",
                        "Estudio completo: verificación laboral, académica, financiera y personal.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 2),
                item("Estudio Quick",
                        "Validación express de antecedentes con entrega en 24 horas hábiles.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 3, 1),
                item("Estudio OEA",
                        "Estudio de seguridad especial para Operadores Económicos Autorizados (DIAN).",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 4),
                item("Combo Básico + Visita",
                        "Estudio básico de antecedentes combinado con visita domiciliaria.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 5),
                item("Combo Avanzado + Polígrafo",
                        "Estudio avanzado completo combinado con examen poligráfico.",
                        CategoriaServicio.ESTUDIOS_SEGURIDAD, 6),

                // ── HOJA DE VIDA ─────────────────────────────────────────────────────────
                item("Verificación de Antecedentes",
                        "Consulta de antecedentes judiciales, penales, fiscales y disciplinarios.",
                        CategoriaServicio.HOJA_VIDA, 1),
                item("Validación Laboral",
                        "Verificación de referencias y experiencia laboral declarada en la hoja de vida.",
                        CategoriaServicio.HOJA_VIDA, 2),
                item("Validación Académica",
                        "Verificación de títulos, diplomas y certificados académicos ante las instituciones.",
                        CategoriaServicio.HOJA_VIDA, 3),
                item("Validación Personal",
                        "Verificación de referencias personales y datos básicos de identificación.",
                        CategoriaServicio.HOJA_VIDA, 4),
                item("Visita Domiciliaria",
                        "Visita al entorno familiar y habitacional del candidato con informe detallado.",
                        CategoriaServicio.HOJA_VIDA, 5),

                // ── OTROS ────────────────────────────────────────────────────────────────
                item("Estudio Persona Jurídica",
                        "Estudio de confiabilidad y seguridad aplicado a personas jurídicas y empresas.",
                        CategoriaServicio.OTROS, 1),
                item("Psicotécnicas",
                        "Aplicación de pruebas psicotécnicas de aptitudes y competencias laborales.",
                        CategoriaServicio.OTROS, 2)
        );
    }

    private CatalogoServicio item(String nombre, String descripcion, CategoriaServicio categoria, int orden) {
        return item(nombre, descripcion, categoria, orden, 5);
    }

    private void crearAdminSiNoExiste() {
        if (usuarioRepository.existsByEmail(adminEmail)) {
            log.info("Admin ya existe en BD: {}", adminEmail);
            return;
        }
        Usuario admin = Usuario.builder()
                .nombre(adminNombre)
                .apellido("Polygraph")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .rol(Rol.ADMIN_POLYGRAPH)
                .activo(true)
                .requiere2fa(false)
                .emailVerificado(true)
                .fechaCreacion(LocalDateTime.now())
                .build();
        usuarioRepository.save(admin);
        log.info(">>> ADMIN creado en BD: {} / pass: {}", adminEmail, adminPassword);
    }

    private void crearUsuariosDePruebaSiNoExisten() {
        crearOActualizarUsuario("gestor@polygraph.com", "Gestor1234!", "Gestor", "Prueba",
                Rol.GESTOR, null);

        Integer idClienteAbc = clienteRepository.findByEmailPrincipal("contacto@empresa-abc.com")
                .map(c -> c.getIdCliente())
                .orElse(null);

        crearOActualizarUsuario("cliente1@empresa-abc.com", "Cliente1234!", "Cliente", "ABC",
                Rol.ADMIN_CLIENTE, idClienteAbc);
        crearOActualizarUsuario("analista@empresa-abc.com", "Analista1234!", "Analista", "ABC",
                Rol.ANALISTA_CLIENTE, idClienteAbc);
    }

    private void crearOActualizarUsuario(String email, String password, String nombre,
                                         String apellido, Rol rol, Integer idCliente) {
        String hashPassword = passwordEncoder.encode(password);
        usuarioRepository.findByEmail(email).ifPresentOrElse(
                usuario -> {
                    usuario.setPassword(hashPassword);
                    usuario.setActivo(true);
                    usuario.setEmailVerificado(true);
                    usuarioRepository.save(usuario);
                    log.info(">>> Usuario actualizado: {} / rol: {}", email, rol);
                },
                () -> {
                    Usuario nuevo = Usuario.builder()
                            .nombre(nombre)
                            .apellido(apellido)
                            .email(email)
                            .password(hashPassword)
                            .rol(rol)
                            .activo(true)
                            .requiere2fa(false)
                            .emailVerificado(true)
                            .idCliente(idCliente)
                            .fechaCreacion(LocalDateTime.now())
                            .build();
                    usuarioRepository.save(nuevo);
                    log.info(">>> Usuario creado: {} / rol: {}", email, rol);
                }
        );
    }

    private CatalogoServicio item(String nombre, String descripcion, CategoriaServicio categoria, int orden, int diasHabiles) {
        return CatalogoServicio.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .categoria(categoria)
                .orden(orden)
                .activo(true)
                .diasHabilesEntrega(diasHabiles)
                .build();
    }
}
