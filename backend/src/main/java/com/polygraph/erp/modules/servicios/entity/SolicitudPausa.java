package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.OrigenPausa;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Un lapso en que el reloj de la solicitud no corre (espera la hoja de vida del candidato, una novedad imputable al candidato o al
 *  cliente...). Mientras fin sea null la pausa está abierta. */
@Entity
@Table(name = "solicitud_pausa")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "servicio")
public class SolicitudPausa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Servicio servicio;

    @Column(name = "inicio", nullable = false)
    private LocalDateTime inicio;

    @Column(name = "fin")
    private LocalDateTime fin;

    @Column(name = "motivo", nullable = false, length = 250)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 10)
    private OrigenPausa origen;

    @Column(name = "creado_por", length = 150)
    private String creadoPor;

    public boolean abierta() {
        return fin == null;
    }
}
