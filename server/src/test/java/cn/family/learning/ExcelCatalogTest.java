package cn.family.learning;

import cn.family.learning.catalog.*;
import cn.family.learning.catalog.CatalogDtos.*;
import cn.family.learning.common.ApiException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExcelCatalogTest {
    final ExcelCatalog excel=new ExcelCatalog();
    private MockMultipartFile file(byte[] bytes){return new MockMultipartFile("file","words.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);}
    @Test void templateRoundTripsSixColumnsWithPhonetics()throws Exception {
        Map<String,Object> preview=excel.preview(file(excel.template()));assertEquals(true,preview.get("valid"));
        List<Entry> entries=(List<Entry>)preview.get("entries");assertEquals(3,entries.size());assertEquals("/ˈæpəl/",entries.get(0).phonetic);assertEquals("Unit 2",entries.get(2).unit);assertEquals(3,entries.get(0).grade);
    }
    private byte[] modify(java.util.function.Consumer<Sheet> change)throws Exception {try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(excel.template()));ByteArrayOutputStream out=new ByteArrayOutputStream()){change.accept(book.getSheetAt(0));book.write(out);return out.toByteArray();}}
    @Test void reportsExactRowForInvalidGradeAndMissingMeaning()throws Exception {
        Map<String,Object> result=excel.preview(file(modify(s->{s.getRow(1).getCell(0).setCellValue("7");s.getRow(2).getCell(3).setCellValue("");})));
        assertEquals(false,result.get("valid"));List<Map<String,Object>> errors=(List<Map<String,Object>>)result.get("errors");assertEquals(2,errors.size());assertEquals(2,errors.get(0).get("row"));assertEquals(3,errors.get(1).get("row"));
    }
    @Test void doesNotEvaluateFormulaCellsAndAllowsBlankPhonetic()throws Exception {
        Map<String,Object> result=excel.preview(file(modify(s->{s.getRow(1).getCell(4).setCellFormula("1+1");s.getRow(2).getCell(5).setCellValue("");})));
        assertEquals(false,result.get("valid"));assertEquals(1,((List<?>)result.get("errors")).size());assertEquals("",((List<Entry>)result.get("entries")).get(0).phonetic);
    }
    @Test void rejectsWrongHeadersOldXlsEmptyFilesAndExcessiveRowCount()throws Exception {
        assertThrows(ApiException.class,()->excel.preview(file(modify(s->s.getRow(0).getCell(0).setCellValue("wrong")))));
        assertThrows(ApiException.class,()->excel.preview(new MockMultipartFile("file","words.xls","application/vnd.ms-excel",new byte[]{1})));
        assertThrows(ApiException.class,()->excel.preview(file(new byte[0])));
        assertThrows(ApiException.class,()->excel.preview(file(modify(s->s.createRow(5001).createCell(0).setCellValue("3")))));
    }
    @Test void reportsDuplicatesWithinSameScopeButAllowsDifferentUnits()throws Exception {
        Map<String,Object> result=excel.preview(file(modify(s->s.getRow(2).getCell(4).setCellValue(" APPLE "))));assertEquals(false,result.get("valid"));
        result=excel.preview(file(modify(s->{s.getRow(2).getCell(4).setCellValue("apple");s.getRow(2).getCell(2).setCellValue("Unit 3");})));assertEquals(true,result.get("valid"));
    }
    @Test void rejectsEmojiAndInvalidZipInsteadOfReachingMysql()throws Exception {
        Map<String,Object> result=excel.preview(file(modify(s->s.getRow(1).getCell(3).setCellValue("苹果😀"))));assertEquals(false,result.get("valid"));
        assertThrows(ApiException.class,()->excel.preview(file("not an xlsx".getBytes("UTF-8"))));
    }
}
