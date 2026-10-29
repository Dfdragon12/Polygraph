package com.polygraph.erp.modules.servicios.entity;

import jakarta.persistence.*;
import lombok.*;

/** Respuestas del cliente al formato adicional de su solicitud (pre-empleo, rutina o específica):
 *  JSON clave → valor, con las claves de FormatoSolicitud. */
@Entity
@Table(name = "servicio_datos_solicitud")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicioDatosSolicitud {

    @Id
    @Column(name = "id_servicio")
    private Integer idServicio;

    @Column(name = "formato", nullable = false, length = 20)
    private String formato;

    @Column(name = "datos", nullable = false, columnDefinition = "TEXT")
    private String datos;
}
