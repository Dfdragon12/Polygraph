package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.TipoSeguimientoNovedad;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Una entrada del seguimiento de una NovedadSubproceso — solo inserción, nunca se edita. */
@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "novedad_seguimientos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"novedad", "autor"})
public class NovedadSeguimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_novedad", nullable = false)
    private NovedadSubproceso novedad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoSeguimientoNovedad tipo;

    @Column(name = "texto", columnDefinition = "TEXT")
    private String texto;

    @Column(name = "fecha_anterior")
    private LocalDateTime fechaAnterior;

    @Column(name = "fecha_nueva")
    private LocalDateTime fechaNueva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_autor", nullable = false)
    private Usuario autor;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void alCrear() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
