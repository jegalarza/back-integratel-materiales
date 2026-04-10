package com.automatizacion.dto.response;

import com.automatizacion.dto.request.RangoRequest;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonPropertyOrder({
        "Mensaje",
        "Lista_de_ambientes_disponibles",
        "Lista_de_rangos_disponibles",
        "Se_requiere_carga_acaptados",
        "Ingreso_campo_solicitante",
        "Ingreso_campo_cantidad",
        "Ejemplo"
})
public class GuiaResponse {

    private String Mensaje;
    private List<String> Lista_de_ambientes_disponibles;
    private List<String> Lista_de_rangos_disponibles;
    private List<String> Se_requiere_carga_acaptados;
    private String Ingreso_campo_solicitante;
    private String Ingreso_campo_cantidad;
    private RangoRequest Ejemplo;

}
