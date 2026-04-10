package com.automatizacion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RangoResponse {

    private String mensaje;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String archivoAssignMsisdn;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String archivoMsisdn;

    private int cantidadSolicitada;
    private int cantidadDisponible;
    private List<String> rangosAsignados;

}