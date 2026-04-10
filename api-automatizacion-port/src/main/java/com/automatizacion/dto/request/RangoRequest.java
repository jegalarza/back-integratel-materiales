package com.automatizacion.dto.request;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class RangoRequest {

    private String ambiente;
    private String rango;
    private String requiereCarga;
    private String solicitante;
    private int cantidad;

}
