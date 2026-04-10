package com.automatizacion.service.impl;

import com.automatizacion.component.SftpConfig;
import com.automatizacion.component.SshConfig;
import com.automatizacion.dto.request.RangoRequest;
import com.automatizacion.dto.response.GuiaResponse;
import com.automatizacion.dto.response.RangoResponse;
import com.automatizacion.service.RangoService;
import com.jcraft.jsch.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class RangoServiceImpl implements RangoService {

    @Autowired
    private SshConfig SshConfig;

    @Autowired
    private SftpConfig SftpConfig;

    private static final Logger log = LoggerFactory.getLogger(RangoServiceImpl.class);

    @Override
    public GuiaResponse obtenerListaRangosDesdeExcel() throws Exception {
        GuiaResponse response = new GuiaResponse();
        List<String> listaRangos = new ArrayList<>();
        List<String> listaAmbientes = new ArrayList<>();
        List<String> listaRequiereCarga = new ArrayList<>();

        RangoRequest ejemplo = new RangoRequest();
        ejemplo.setAmbiente("UAT3");
        ejemplo.setRango("92097");
        ejemplo.setRequiereCarga("SI");
        ejemplo.setSolicitante("JAVIER GALARZA");
        ejemplo.setCantidad(10);

        Session session = null;
        ChannelSftp sftp = null;
        try {
            log.info("Conectando a SFTP para leer Excel...");
            JSch jsch = new JSch();
            session = jsch.getSession(
                    SshConfig.getUser(),
                    SshConfig.getHost(),
                    SshConfig.getPort()
            );
            session.setPassword(SshConfig.getPassword());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            log.info("Conectado a SFTP → {}", sftp.pwd());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sftp.get(SshConfig.getRemotePath(), baos);
            Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()));
            int totalHojas = workbook.getNumberOfSheets();
            for (int i = 0; i < totalHojas; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String nombreHoja = sheet.getSheetName();
                if ("BITACORA".equalsIgnoreCase(nombreHoja)) {
                    continue;
                }
                if (nombreHoja.startsWith("PORT_OUT")) {
                    String numero = nombreHoja.replace("PORT_OUT", "").trim();
                    if (numero.matches("\\d+")) {
                        listaRangos.add(numero);
                        log.info("Rango encontrado: {}", numero);
                    }
                }
            }
            workbook.close();
            Collections.sort(listaRangos);
            log.info("Total rangos encontrados: {}", listaRangos.size());

            listaAmbientes.add("UAT1");
            listaAmbientes.add("UAT2");
            listaAmbientes.add("UAT3");
            listaAmbientes.add("UAT4");

            listaRequiereCarga.add("SI");
            listaRequiereCarga.add("NO");

            response.setMensaje("En este apartado podras visualizar los posibles datos que deberas ingresar en el request de la solicitud de numeros");
            response.setLista_de_rangos_disponibles(listaRangos);
            response.setLista_de_ambientes_disponibles(listaAmbientes);
            response.setSe_requiere_carga_acaptados(listaRequiereCarga);
            response.setIngreso_campo_solicitante("Se debera ingresar el primer nombre y apellido del usuario solicitante");
            response.setIngreso_campo_cantidad("Se debera ingresar un numero entero positivo");
            response.setEjemplo(ejemplo);
            return response;
        } catch (Exception e) {
            log.error("Error obteniendo rangos desde Excel", e);
            throw e;
        } finally {
            if (sftp != null) sftp.exit();
            if (session != null) session.disconnect();
        }
    }

    @Override
    public synchronized RangoResponse procesarRangos(RangoRequest request) throws Exception {

        log.info("*** Inicializando metodo de procesos de rangos ***");
        log.info("Request: " + request.toString());
        Session sshSession = null;
        ChannelSftp sftpExcel = null;

        Session sftpSession = null;
        ChannelSftp sftpDestino = null;

        RangoResponse response = new RangoResponse();
        List<String> rangosAsignados = new ArrayList<>();
        String ambienteSeteado = "PMX_".concat(request.getAmbiente().toUpperCase());

        try {
            JSch jsch = new JSch();
            log.info("Conectando SSH (Excel)...");
            sshSession = jsch.getSession(
                    SshConfig.getUser(),
                    SshConfig.getHost(),
                    SshConfig.getPort()
            );
            sshSession.setPassword(SshConfig.getPassword());
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect();

            Channel channelExcel = sshSession.openChannel("sftp");
            channelExcel.connect();
            sftpExcel = (ChannelSftp) channelExcel;

            log.info("SSH conectado OK → {}", sftpExcel.pwd());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sftpExcel.get(SshConfig.getRemotePath(), baos);

            Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()));

            Sheet sheet = obtenerHojaPorRango(workbook, request.getRango());

            int totalDisponibles = contarRangosDisponibles(sheet) - 1;
            log.info("TotalDisponibles: {}", totalDisponibles);

            if(totalDisponibles < request.getCantidad()) {
                response.setArchivoAssignMsisdn(null);
                response.setArchivoMsisdn(null);
                response.setRangosAsignados(null);
                response.setCantidadSolicitada(request.getCantidad());
                response.setCantidadDisponible(totalDisponibles);
                response.setMensaje("No hay suficiente cantidad disponible para poder brindarte los números solicitados");
                return response;
            }

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                Cell rangoCell = row.getCell(0);
                Cell proyectoCell = row.getCell(1);
                if (rangoCell != null &&
                        !getCellValue(rangoCell).isEmpty() &&
                        (proyectoCell == null || proyectoCell.getCellType() == CellType.BLANK)) {

                    String rango = getCellValue(rangoCell);

                    row.createCell(1).setCellValue("Gestion de Materiales");

                    if (request.getRequiereCarga().equalsIgnoreCase("NO")) {
                        row.createCell(2).setCellValue("-");
                        row.createCell(3).setCellValue("NO PORT INT");
                    } else {
                        row.createCell(2).setCellValue(ambienteSeteado);
                        row.createCell(3).setCellValue("PORT INT");
                    }

                    row.createCell(4).setCellValue(request.getSolicitante().toUpperCase());
                    row.createCell(5).setCellValue(obtenerFechaActual());
                    row.createCell(6).setCellValue(obtenerFechaActual());
                    row.createCell(7).setCellValue("NO");

                    rangosAsignados.add(rango);
                    log.info("Rango asignado: {}", rango);

                    if (rangosAsignados.size() == request.getCantidad()) {
                        break;
                    }
                }
            }

            if (rangosAsignados.size() < request.getCantidad()) {
                throw new RuntimeException("No hay suficientes rangos disponibles");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            sftpExcel.put(
                    new ByteArrayInputStream(out.toByteArray()),
                    SshConfig.getRemotePath()
            );

            workbook.close();
            log.info("Excel actualizado");

            if (request.getRequiereCarga().equalsIgnoreCase("SI")) {

                log.info("Conectando SFTP destino (tefabp1)...");

                sftpSession = jsch.getSession(
                        SftpConfig.getUser(),
                        obtenerHostPorAmbiente(request.getAmbiente()),
                        SftpConfig.getPort()
                );
                sftpSession.setPassword(SftpConfig.getPassword());
                sftpSession.setConfig("StrictHostKeyChecking", "no");
                sftpSession.connect();

                Channel channelDestino = sftpSession.openChannel("sftp");
                channelDestino.connect();
                sftpDestino = (ChannelSftp) channelDestino;

                log.info("SFTP destino conectado → {}", sftpDestino.pwd());

                sftpDestino.cd(SftpConfig.getRemotePath());
                Map<String, String> nombres = generarNombresDesdeBitacora(sftpExcel);

                byte[] archivoTxtAssignMsisdn = generarArchivoAssignMsisdn(rangosAsignados, request.getRango());
                String nombreArchivoAssignMsisdn = nombres.get("ASSIGN");
                sftpDestino.put(
                        new ByteArrayInputStream(archivoTxtAssignMsisdn),
                        nombreArchivoAssignMsisdn
                );
                log.info("Archivo ASSIGNMSISDN subido: {}", nombreArchivoAssignMsisdn);

                byte[] archivoLoadMsisdn = generarArchivoMsisdn(rangosAsignados, request.getRango());
                String nombreArchivoMsisdn = nombres.get("MSISDN");
                sftpDestino.put(
                        new ByteArrayInputStream(archivoLoadMsisdn),
                        nombreArchivoMsisdn);
                log.info("Archivo MSISDN subido: {}", nombreArchivoMsisdn);

                ejecutarShellsParalelo(request.getAmbiente(), SftpConfig.getScriptOrder(), SftpConfig.getScriptRequest());

                response.setArchivoAssignMsisdn(nombreArchivoAssignMsisdn);
                response.setArchivoMsisdn(nombreArchivoMsisdn);
            }

            response.setRangosAsignados(rangosAsignados);
            response.setCantidadSolicitada(request.getCantidad());
            response.setCantidadDisponible(totalDisponibles);
            response.setMensaje("OK");

            return response;

        } finally {

            if (sftpExcel != null) sftpExcel.exit();
            if (sshSession != null) sshSession.disconnect();

            if (sftpDestino != null) sftpDestino.exit();
            if (sftpSession != null) sftpSession.disconnect();
        }
    }

    private int extraerCorrelativo(String nombreArchivo) {
        try {
            String[] partes = nombreArchivo.split("_");
            String correlativoStr = partes[3].replace(".txt", "");
            return Integer.parseInt(correlativoStr);
        } catch (Exception e) {
            return -1;
        }
    }

    private Map<String, String> generarNombresDesdeBitacora(ChannelSftp sftp) throws Exception {
        LocalDate hoy = LocalDate.now();
        String fechaFormato = hoy.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        sftp.get(SshConfig.getRemotePath(), baos);
        Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()));
        Sheet bitacora = workbook.getSheet("BITACORA");
        if (bitacora == null) {
            workbook.close();
            throw new RuntimeException("No existe la hoja BITACORA");
        }
        int max = -1;
        for (Row row : bitacora) {
            if (row.getRowNum() == 0) continue;
            Cell fechaExcelCell = row.getCell(1);
            Cell archivoCell = row.getCell(2);
            if (fechaExcelCell == null || archivoCell == null) continue;
            LocalDate fechaExcel = obtenerFechaCelda(fechaExcelCell);
            if (fechaExcel == null || !fechaExcel.equals(hoy)) continue;
            String nombre = archivoCell.getStringCellValue();
            if (!nombre.startsWith("ASSIGN_MSISDN")) continue;
            int correlativo = extraerCorrelativo(nombre);
            if (correlativo > max) {
                max = correlativo;
            }
        }
        int siguiente = max + 1;
        String correlativoFinal = String.format("%02d", siguiente);
        String nombreAssign = "ASSIGN_MSISDN_" + fechaFormato + "_" + correlativoFinal + ".txt";
        String nombreMsisdn = "MSISDN_" + fechaFormato + "_" + correlativoFinal + ".txt";
        log.info("Generados: {}, {}", nombreAssign, nombreMsisdn);
        int lastRow = bitacora.getLastRowNum() + 1;
        Row row1 = bitacora.createRow(lastRow++);
        row1.createCell(0).setCellValue(obtenerFechaActual());
        row1.createCell(1).setCellValue(java.sql.Date.valueOf(hoy));
        row1.createCell(2).setCellValue(nombreAssign);
        Row row2 = bitacora.createRow(lastRow);
        row2.createCell(0).setCellValue(obtenerFechaActual());
        row2.createCell(1).setCellValue(java.sql.Date.valueOf(hoy));
        row2.createCell(2).setCellValue(nombreMsisdn);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        sftp.put(
                new ByteArrayInputStream(out.toByteArray()),
                SshConfig.getRemotePath()
        );
        workbook.close();
        Map<String, String> resultado = new HashMap<>();
        resultado.put("ASSIGN", nombreAssign);
        resultado.put("MSISDN", nombreMsisdn);
        return resultado;
    }

    private byte[] generarArchivoAssignMsisdn(List<String> rangos, String numero) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos));
        writer.write("$H:INVOKE_UNIFIED_RESOURCE_ACTIVITY");
        writer.newLine();
        writer.write("$H:Type, Value, Pool, Activity Name, Activity Parameters, Attributes");
        writer.newLine();
        String tipo = esNumeroMovil(numero) ? "MSISDN" : "TN";
        for (String rango : rangos) {
            writer.write("$R:" + tipo + "," + rango + ",1,LOAD,,");
            writer.newLine();
        }
        writer.write("$F");
        writer.flush();
        return baos.toByteArray();
    }

    private byte[] generarArchivoMsisdn(List<String> rangos, String numero) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos));
        writer.write("$H:INVOKE_UNIFIED_RESOURCE_ACTIVITY");
        writer.newLine();
        writer.write("$H:Type, Value, Pool, Activity Name, Activity Parameters, Attributes");
        writer.newLine();
        String tipo = esNumeroMovil(numero) ? "MSISDN" : "TN";
        for (String rango : rangos) {
            writer.write("$R:" + tipo + "," + rango + ",1,LOAD,,");
            writer.newLine();
        }
        writer.write("$F");
        writer.flush();
        return baos.toByteArray();
    }

    private Session crearSesion(String ambiente) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(
                SftpConfig.getUser(),
                obtenerHostPorAmbiente(ambiente),
                SftpConfig.getPort()
        );
        session.setPassword(SftpConfig.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        return session;
    }

    private void ejecutarShell(Session session, String comando) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        log.info("Ejecutando comando: {}", comando);
        channel.setCommand(comando);
        channel.setInputStream(null);
        InputStream input = channel.getInputStream();
        InputStream error = channel.getErrStream();
        channel.connect();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(error));
        String line;
        while ((line = reader.readLine()) != null) {
            log.info("OUTPUT: {}", line);
        }
        while ((line = errorReader.readLine()) != null) {
            log.error("ERROR: {}", line);
        }
        log.info("Exit Status: {}", channel.getExitStatus());
        channel.disconnect();
    }

    private Future<?> procesoActual;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public synchronized void ejecutarShellsParalelo(String ambiente, String comando1, String comando2) {
        log.info("Validando ejecución previa...");
        if (procesoActual != null && !procesoActual.isDone()) {
            log.warn("Ya existe un proceso en ejecución. Se omite nueva ejecución.");
            return;
        }
        log.info("Iniciando nueva ejecución paralela...");
        procesoActual = executor.submit(() -> {
            Session s1 = null;
            Session s2 = null;
            try {
                final Session session1 = crearSesion(ambiente);
                final Session session2 = crearSesion(ambiente);
                s1 = session1;
                s2 = session2;
                log.info("Matando procesos previos...");
                ejecutarShell(session1, "pkill -f RM1OrderSync_Sh || true");
                ejecutarShell(session1, "pkill -f RM1Request_Sh || true");
                log.info("Limpiando archivos .control...");
                ejecutarShell(session1,
                        "rm -f /tefabp/tefabp1/var/tfp/projs/rm/work/*.control || true");
                Thread t1 = new Thread(() -> {
                    try {
                        log.info("Ejecutando comando 1...");
                        ejecutarShell(session1, comando1);
                    } catch (Exception e) {
                        log.error("Error en comando 1", e);
                    }
                });
                Thread t2 = new Thread(() -> {
                    try {
                        log.info("Ejecutando comando 2...");
                        ejecutarShell(session2, comando2);
                    } catch (Exception e) {
                        log.error("Error en comando 2", e);
                    }
                });
                t1.start();
                t2.start();
                t1.join();
                t2.join();
                log.info("Ejecución COMPLETA finalizada correctamente.");
            } catch (InterruptedException e) {
                log.warn("Ejecución interrumpida.");
            } catch (Exception e) {
                log.error("Error en ejecución paralela", e);
            } finally {
                if (s1 != null && s1.isConnected()) s1.disconnect();
                if (s2 != null && s2.isConnected()) s2.disconnect();
            }
        });
    }

    public String ejecutarShellController(String ambiente){
        try {
            ejecutarShellsParalelo(ambiente, SftpConfig.getScriptOrder(), SftpConfig.getScriptRequest());
            return "Ejecucion realiza exitosamente";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }


    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return "";
        }
    }

    private Sheet obtenerHojaPorRango(Workbook workbook, String rangoSolicitud) {
        String nombreHoja = "PORT_OUT " + rangoSolicitud;
        Sheet sheet = workbook.getSheet(nombreHoja);
        if (sheet == null) {
            throw new RuntimeException("No existe la hoja: " + nombreHoja);
        }
        return sheet;
    }

    private int contarRangosDisponibles(Sheet sheet) {
        int disponibles = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            Cell rangoCell = row.getCell(0);
            Cell proyectoCell = row.getCell(1);
            if (rangoCell != null &&
                    !getCellValue(rangoCell).isEmpty() &&
                    (proyectoCell == null || proyectoCell.getCellType() == CellType.BLANK)) {
                disponibles++;
            }
        }
        return disponibles;
    }

    public String obtenerHostPorAmbiente(String ambiente) {
        switch (ambiente.toUpperCase()) {
            case "UAT1":
                return SftpConfig.getHostUat1();
            case "UAT2":
                return SftpConfig.getHostUat2();
            case "UAT3":
                return SftpConfig.getHostUat3();
            case "UAT4":
                return SftpConfig.getHostUat4();
            default:
                throw new IllegalArgumentException("Ambiente no válido: " + ambiente);
        }
    }

    public String obtenerFechaActual() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return LocalDate.now().format(formatter);
    }

    private LocalDate obtenerFechaCelda(Cell cell) {

        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {

                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue()
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                } else {
                    return DateUtil.getJavaDate(cell.getNumericCellValue())
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                }
            }

        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public boolean esNumeroMovil(String numero) {
        return numero != null && numero.startsWith("9");
    }
}