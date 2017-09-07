package com.tmModulos.controlador.procesador;

import com.tmModulos.controlador.servicios.VeriPreHorarios;
import com.tmModulos.controlador.utils.*;
import com.tmModulos.modelo.entity.tmData.*;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Time;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service("VerificacionHorario")
public class VerificacionHorarios {

    private List<LogDatos> logDatos;
    private String destination;
    private List<String> serviciosNoEncontrados;


    @Autowired
    private ProcessorUtils processorUtils;

    @Autowired
    private VeriPreHorarios veriPreHorarios;

    @Autowired
    private IntervalosVerificacionHorarios intervalosVeri;

    public VerificacionHorarios() {}

    public List<LogDatos> compararExpediciones (String fileName, InputStream in, String tipoValidacion, String tipoDia) {
        logDatos = new ArrayList<>();
        destination=PathFiles.PATH_FOR_FILES + "\\";
        processorUtils.copyFile(fileName,in,destination);
        destination=PathFiles.PATH_FOR_FILES+"\\"+fileName;

        if(tipoValidacion.equals("Pre")){

            veriPreHorarios.addEquivalenciasFromFile(destination);
            System.out.println("Guarde en Base de Datos");
            compareDataExcel(fileForTipoDia(tipoDia),tipoValidacion);
            veriPreHorarios.deleteEquivalencias();

        } else{
            veriPreHorarios.addTablaHorarioFromFile(destination);
            compareDataExcel(fileForTipoDia(tipoDia),tipoValidacion);
            veriPreHorarios.deleteTablaHorario();

        }

        return logDatos;
    }

    private void compareDataExcel(String file,String tipo) {
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            HSSFWorkbook workbook = new HSSFWorkbook(fileInputStream);
            HSSFSheet worksheet = workbook.getSheetAt(0);


            Iterator<Row> rowIterator = worksheet.iterator();
            Row r =rowIterator.next(); rowIterator.next(); // Skip first two lines

            while (rowIterator.hasNext()) {

                Row row = rowIterator.next();

                if( row.getCell(0) != null ){
                    Date horaInicio = processorUtils.convertirATime(processorUtils.getStringCellValue(row,ComparadorHorarioIndex.HORA_INICIO));
                    Date horaInicioB = processorUtils.convertirATime(processorUtils.getStringCellValue(row,ComparadorHorarioIndex.HORA_INICIO_2));
                    Date horaFin = processorUtils.convertirATime(processorUtils.getStringCellValue(row,ComparadorHorarioIndex.HORA_FIN));
                    Date horaFinB = processorUtils.convertirATime(processorUtils.getStringCellValue(row,ComparadorHorarioIndex.HORA_FIN_2));
                    int distancia = (int) row.getCell(ComparadorHorarioIndex.DISTANCIA).getNumericCellValue();
                    if(tipo.equals("Pre")){
                        verificacionPreHorario(row, horaInicio, horaInicioB, horaFin, horaFinB, distancia);
                    }else{
                        verificacionPostHorario(row, horaInicio, horaInicioB, horaFin, horaFinB, distancia);
                    }

                }

/*COPY temp_expediciones (hora_inicio,punto_inicio,hora_fin,punto_fin,identificador,km)
 FROM 'C:/temp/prueba.csv'  DELIMITER ';' CSV HEADER;*/
            }
            fileInputStream.close();
            FileOutputStream outFile =new FileOutputStream(new File(PathFiles.PATH_FOR_FILES+"\\update.xls"));
            workbook.write(outFile);
            outFile.close();
            System.out.println("Fin");
        } catch (FileNotFoundException e) {
            logDatos.add(new LogDatos(e.getMessage(), TipoLog.ERROR));
            e.printStackTrace();
        } catch (IOException e) {
            logDatos.add(new LogDatos(e.getMessage(), TipoLog.ERROR));
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void verificacionPostHorario(Row row, Date horaInicio, Date horaInicioB, Date horaFin, Date horaFinB, int distancia) {
        String id = row.getCell(ComparadorHorarioIndex.iD).getStringCellValue();
        String[] valores = id.split("-");
        int linea = Integer.parseInt(valores[0]);
        int sublinea = Integer.parseInt(valores[1]);
        int ruta = Integer.parseInt(valores[2]);
        int punto = Integer.parseInt(valores[3]);
        List<TempPos> tempHorarios = veriPreHorarios.getTablaHorarioByData(linea,sublinea,ruta,punto);
        if(tempHorarios.size()>0){

            List< String> validacion = validarHorarioPost(tempHorarios,horaInicio,horaInicioB,
                    horaFin,horaFinB,distancia);

            createCellResultados(row, validacion.get(0),ComparadorHorarioIndex.RES_HORA_INI);
            createCellResultados(row, validacion.get(1),ComparadorHorarioIndex.RES_HORA_FIN);
            createCellResultados(row, validacion.get(2),ComparadorHorarioIndex.RES_HORA_INI_2);
            createCellResultados(row, validacion.get(3),ComparadorHorarioIndex.RES_HORA_FIN_2);
            createCellResultados(row, "N/A",ComparadorHorarioIndex.RES_DISTANCIA);

        }else{
            registrosNoEncontrados(row,id);
        }
    }

    private void verificacionPreHorario(Row row, Date horaInicio, Date horaInicioB, Date horaFin, Date horaFinB, int distancia) {
        String id = row.getCell(ComparadorHorarioIndex.iD_PRE).getStringCellValue();
        List<ExpedicionesTemporal> expedicionesTemporals = veriPreHorarios.getExpedicionesTemporalsData(id);
        if(expedicionesTemporals.size()>0){

            List< String> validacion = validarHorario(expedicionesTemporals,horaInicio,horaInicioB,
                    horaFin,horaFinB,distancia);

            createCellResultados(row, validacion.get(0),ComparadorHorarioIndex.RES_HORA_INI);
            createCellResultados(row, validacion.get(1),ComparadorHorarioIndex.RES_HORA_FIN);
            createCellResultados(row, validacion.get(2),ComparadorHorarioIndex.RES_HORA_INI_2);
            createCellResultados(row, validacion.get(3),ComparadorHorarioIndex.RES_HORA_FIN_2);
            createCellResultados(row, validacion.get(4),ComparadorHorarioIndex.RES_DISTANCIA);

            List<String> intervalosExpediciones = intervalosVeri.calcularIntervalos(expedicionesTemporals,horaInicio,horaInicioB,
                    horaFin,horaFinB);
            createCellResultados(row, intervalosExpediciones.get(0),ComparadorHorarioIndex.INT_PROMEDIO_INI);
            createCellResultados(row, intervalosExpediciones.get(1),ComparadorHorarioIndex.INT_MINIMO_INI);
            createCellResultados(row, intervalosExpediciones.get(2),ComparadorHorarioIndex.INT_MAXIMO_INI);
            createCellResultados(row, intervalosExpediciones.get(3),ComparadorHorarioIndex.INT_PROMEDIO_PAM);
            createCellResultados(row, intervalosExpediciones.get(4),ComparadorHorarioIndex.INT_MINIMO_PAM);
            createCellResultados(row, intervalosExpediciones.get(5),ComparadorHorarioIndex.INT_MAXIMO_PAM);
            createCellResultados(row, intervalosExpediciones.get(6),ComparadorHorarioIndex.INT_PROMEDIO_VALLE);
            createCellResultados(row, intervalosExpediciones.get(7),ComparadorHorarioIndex.INT_MINIMO_VALLE);
            createCellResultados(row, intervalosExpediciones.get(8),ComparadorHorarioIndex.INT_MAXIMO_VALLE);
            createCellResultados(row, intervalosExpediciones.get(9),ComparadorHorarioIndex.INT_PROMEDIO_PPM);
            createCellResultados(row, intervalosExpediciones.get(10),ComparadorHorarioIndex.INT_MINIMO_PPM);
            createCellResultados(row, intervalosExpediciones.get(11),ComparadorHorarioIndex.INT_MAXIMO_PPM);
            createCellResultados(row, intervalosExpediciones.get(12),ComparadorHorarioIndex.INT_PROMEDIO_CI);
            createCellResultados(row, intervalosExpediciones.get(13),ComparadorHorarioIndex.INT_MINIMO_CI);
            createCellResultados(row, intervalosExpediciones.get(14),ComparadorHorarioIndex.INT_MAXIMO_CI);

        }else{
            registrosNoEncontrados(row,id);
        }
    }

    private void registrosNoEncontrados(Row row,String id) {
        String info = "N/A";
        createCellResultados(row, info, ComparadorHorarioIndex.RES_HORA_INI);
        createCellResultados(row, info,ComparadorHorarioIndex.RES_HORA_FIN);
        createCellResultados(row, info,ComparadorHorarioIndex.RES_HORA_INI_2);
        createCellResultados(row, info,ComparadorHorarioIndex.RES_HORA_FIN_2);
        createCellResultados(row, info,ComparadorHorarioIndex.RES_DISTANCIA);
    }

    private List<String> validarLimites(List<ExpedicionesTemporal> expedicionesTemporals, Date horaInicio, Date horaInicioB, Date horaFin, Date horaFinB) {
        List<String> limites = new ArrayList<>();
        String compHoraInicio = "OK";
        String compHoraInicioB = "OK";
        String compHoraFin = "OK";
        String compHoraFinB = "OK";
        SimpleDateFormat parser = new SimpleDateFormat("HH:mm:ss");
        if(horaInicioB==null && horaFinB==null){
            if(!horaInicio.equals(processorUtils.convertirATime(expedicionesTemporals.get(0).getHoraInicio()))) compHoraInicio = ErrorMessage.ERROR_INICIO+""+expedicionesTemporals.get(0).getHoraInicio();
            if(!horaFin.equals(processorUtils.convertirATime(expedicionesTemporals.get(expedicionesTemporals.size()-1).getHoraInicio()))) compHoraFin = ErrorMessage.ERROR_FIN+""+expedicionesTemporals.get(expedicionesTemporals.size()-1).getHoraInicio();
        }else{
            Date horarioFin = processorUtils.convertirATime(expedicionesTemporals.get(0).getHoraInicio());
            Date horarioInicioB = null;

            for(int i=0;i<expedicionesTemporals.size();i++){
                Date exp = processorUtils.convertirATime(expedicionesTemporals.get(i).getHoraInicio());
                if(exp.after(horaFin)){
                    horarioInicioB = exp;
                    break;
                }else {
                    horarioFin = exp;
                }
            }

            if(!horaInicio.equals(processorUtils.convertirATime(expedicionesTemporals.get(0).getHoraInicio()))) compHoraInicio = ErrorMessage.ERROR_INICIO+""+expedicionesTemporals.get(0).getHoraInicio();
            if(!horaFin.equals(horarioFin)) compHoraFin = ErrorMessage.ERROR_FIN+""+parser.format(horarioFin);
            if(!horaFinB.equals(processorUtils.convertirATime(expedicionesTemporals.get(expedicionesTemporals.size()-1).getHoraInicio()))) compHoraFinB = ErrorMessage.ERROR_FIN+""+expedicionesTemporals.get(expedicionesTemporals.size()-1).getHoraInicio();
            if(horarioInicioB!=null){
                if(!horaInicioB.equals(horarioInicioB)) compHoraInicioB = ErrorMessage.ERROR_INICIO+""+parser.format(horarioInicioB);
            }else {
                compHoraInicioB = ErrorMessage.ERROR_INICIO+""+parser.format(horaInicioB);
            }
        }

        limites.add(compHoraInicio);
        limites.add(compHoraFin);
        limites.add(compHoraInicioB);
        limites.add(compHoraFinB);

        return limites;

    }




    private void createCellResultados(Row row, String valor,int num) {
        Cell resultadoHoraIni= row.createCell(num);
        resultadoHoraIni.setCellValue(valor);
        resultadoHoraIni.setCellType(Cell.CELL_TYPE_STRING);
        resultadoHoraIni.setCellValue(valor);


    }




    private List<String> validarHorario(List<ExpedicionesTemporal> expedicionesTemporals, Date horaInicio, Date horaInicioB, Date horaFin, Date horaFinB, int distancia) {
        System.out.println("Entre a validar horario");
        List<String> comparaciones = new ArrayList<>();
        String compHoraIni="OK";
        String compHoraIni2="OK";
        String compHoraFin="OK";
        String compHoraFin2="OK";
        String compHoraDis="OK";
        SimpleDateFormat parser = new SimpleDateFormat("HH:mm:ss");
        for(ExpedicionesTemporal temporal: expedicionesTemporals){
            Date expInicio = processorUtils.convertirATime(temporal.getHoraInicio());
            Double km = Double.parseDouble(temporal.getKm().replaceAll("\\,","."))*1000;

            if( horaInicioB== null && horaFinB == null){
                if(expInicio.after(horaInicio) || expInicio.compareTo(horaInicio)==0 ){
                }else{
                    compHoraIni = ErrorMessage.ERROR_LIMITE+""+parser.format(expInicio);
                }

                if(expInicio.before(horaFin) || expInicio.compareTo(horaFin)==0 ){
                }else{
                    compHoraFin = ErrorMessage.ERROR_LIMITE+""+parser.format(expInicio);
                }
            }else{
                if( (expInicio.after(horaInicio) || expInicio.compareTo(horaInicio)==0)
                        && (expInicio.before(horaFin) || expInicio.compareTo(horaFin)==0)){

                }else{
                    if(expInicio.after(horaInicioB) || expInicio.compareTo(horaInicioB)==0 ){

                    }else{
                        compHoraIni2 = ErrorMessage.ERROR_LIMITE+""+parser.format(expInicio);
                    }

                    if(expInicio.before(horaFinB) || expInicio.compareTo(horaFinB)==0 ){

                    }else{
                        compHoraFin2 = ErrorMessage.ERROR_LIMITE+""+parser.format(expInicio);
                    }
                }

            }


            if(km==(double)distancia){
                compHoraDis = "OK";
            }else{
                compHoraDis = km+"";
            }
        }

        List<String> resultadosLimites = validarLimites(expedicionesTemporals,horaInicio,horaInicioB,
                horaFin,horaFinB);

        if(compHoraIni.equals("OK")) compHoraIni = resultadosLimites.get(0);
        if(compHoraFin.equals("OK")) compHoraFin = resultadosLimites.get(1);
        if(compHoraIni2.equals("OK")) compHoraIni2 = resultadosLimites.get(2);
        if(compHoraFin2.equals("OK")) compHoraFin2 = resultadosLimites.get(3);

        comparaciones.add(compHoraIni);
        comparaciones.add(compHoraFin);
        comparaciones.add(compHoraIni2);
        comparaciones.add(compHoraFin2);
        comparaciones.add(compHoraDis);

        return comparaciones;
    }


    private List<String> validarHorarioPost(List<TempPos> tempHorarios, Date horaInicio, Date horaInicioB, Date horaFin, Date horaFinB, int distancia) {
        List<String> comparaciones = new ArrayList<>();
        String compHoraIni="OK";
        String compHoraIni2="OK";
        String compHoraFin="OK";
        String compHoraFin2="OK";
        String compHoraDis="OK";
        SimpleDateFormat parser = new SimpleDateFormat("HH:mm:ss");
        for(TempPos temporal: tempHorarios){
            Date expInicio = processorUtils.convertirATime(temporal.getInstante().toString());

            if( horaInicioB== null && horaFinB == null){
                if(expInicio.after(horaInicio) || expInicio.compareTo(horaInicio)==0 ){
                }else{
                    compHoraIni = parser.format(expInicio);
                }

                if(expInicio.before(horaFin) || expInicio.compareTo(horaFin)==0 ){
                }else{
                    compHoraFin = parser.format(expInicio);
                }
            }else{
                if( (expInicio.after(horaInicio) || expInicio.compareTo(horaInicio)==0)
                        && (expInicio.before(horaFin) || expInicio.compareTo(horaFin)==0)){

                }else{
                    if(expInicio.after(horaInicioB) || expInicio.compareTo(horaInicioB)==0 ){

                    }else{
                        compHoraIni2 = parser.format(expInicio);
                    }

                    if(expInicio.before(horaFinB) || expInicio.compareTo(horaFinB)==0 ){

                    }else{
                        compHoraFin2 = parser.format(expInicio);
                    }
                }

            }
        }

        comparaciones.add(compHoraIni);
        comparaciones.add(compHoraFin);
        comparaciones.add(compHoraIni2);
        comparaciones.add(compHoraFin2);
        comparaciones.add(compHoraDis);

        return comparaciones;
    }


    private String fileForTipoDia(String tipoDia) {
        if(tipoDia.equals("SABADO")){
            return PathFiles.PATH_FOR_FILES+"\\Migracion\\resumenServiciosSabado.xls";
        }else if (tipoDia.equals("FESTIVO")){
            return PathFiles.PATH_FOR_FILES+"\\Migracion\\resumenServiciosFestivo.xls";
        }
        return PathFiles.PATH_FOR_FILES+"\\Migracion\\resumenServiciosHabil.xls";
    }




}
