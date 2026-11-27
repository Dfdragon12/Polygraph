package com.polygraph.erp.modules.trabajos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.trabajos.config.PropiedadesTrabajos;
import com.polygraph.erp.modules.trabajos.repository.TrabajoAsincronoRepository;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PC-01 · PB-H-23. El PDF del estudio quedaba en GENERANDO para siempre porque el trabajo se encolaba desde un {@code afterCommit} con un método
 * {@code REQUIRED}: se unía a la transacción ya cerrada y su INSERT nunca se confirmaba. Aquí se prueba con transacciones reales (H2) que
 * {@code encolarTrasCommit} (REQUIRES_NEW) sí deja el trabajo guardado, y que el método de siempre en ese punto lo pierde.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ColaTrabajosService.class, PC01EncolarTrasCommitTest.Configuracion.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("PC-01 — encolar un trabajo después del commit")
class PC01EncolarTrasCommitTest {

    @TestConfiguration
    static class Configuracion {
        @Bean PropiedadesTrabajos propiedadesTrabajos() { return new PropiedadesTrabajos(); }
        @Bean AlmacenTrabajos almacenTrabajos() { return mock(AlmacenTrabajos.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired private ColaTrabajosService cola;
    @Autowired private TrabajoAsincronoRepository trabajos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PlatformTransactionManager manager;

    @AfterEach
    void limpiar() {
        trabajos.deleteAll();
        usuarios.deleteAll();
    }

    private Usuario gestor() {
        return usuarios.save(Usuario.builder().nombre("Gestor").email("g@pc01.test").password("x").rol(Rol.GESTOR).activo(true).emailVerificado(true).build());
    }

    /** Una transacción que no hace nada y, al confirmarse, ejecuta {@code despuesDelCommit} (lo que hace EmisionEstudioService). */
    private void conAfterCommit(Runnable despuesDelCommit) {
        new TransactionTemplate(manager).executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { despuesDelCommit.run(); }
                }));
    }

    @Test
    @DisplayName("encolarTrasCommit desde un afterCommit deja el trabajo guardado")
    void quedaGuardado() {
        Usuario u = gestor();
        conAfterCommit(() -> cola.encolarTrasCommit(TipoTrabajo.ESTUDIO_PDF, u, Map.of("idDocumento", 7)));
        var guardados = trabajos.findAll();
        assertThat(guardados).hasSize(1);
        assertThat(guardados.get(0).getTipo()).isEqualTo(TipoTrabajo.ESTUDIO_PDF);
        assertThat(guardados.get(0).getEstado().name()).isEqualTo("EN_COLA");
    }

    @Test
    @DisplayName("Con el método de siempre (REQUIRED) el INSERT se pierde: es la causa del PDF que nunca se generaba")
    void requiredSePierde() {
        Usuario u = gestor();
        conAfterCommit(() -> cola.encolar(TipoTrabajo.ESTUDIO_PDF, u, Map.of("idDocumento", 7)));
        assertThat(trabajos.findAll()).isEmpty();
    }
}
