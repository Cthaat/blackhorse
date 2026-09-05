package com.ruoyi.lab.task;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.ruoyi.lab.mapper.TaskSourceMapper;
import static org.junit.jupiter.api.Assertions.*;
class TaskWorkbookTest
{
    private final TaskWorkbook workbook=new TaskWorkbook();
    @Test void textRoundTripsAndFormulaCellsAreRejected() throws Exception
    {
        var headers=TaskWorkbook.columns("LABORATORY");
        byte[] bytes=workbook.write(headers,List.of(List.of("CODE","=1+1","100","1","楼","")),null);
        assertEquals("=1+1",workbook.read(bytes,"LABORATORY").get(0).get("name"));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes));var output=new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(1).getCell(1).setCellFormula("1+1");book.write(output);
            assertThrows(IllegalArgumentException.class,()->workbook.read(output.toByteArray(),"LABORATORY"));
        }
    }
    @Test void invalidStructureIsRejected() throws Exception
    {
        assertThrows(IllegalArgumentException.class,()->workbook.read(new byte[]{1,2,3},"DEVICE"));
        byte[] bytes=workbook.write(List.of("wrong"),List.of(List.of("data")),null);
        assertThrows(IllegalArgumentException.class,()->workbook.read(bytes,"DEVICE"));
    }
    @Test void exportUsesBoundedCursorAndSameReservationFilters()
    {
        String sql=TaskSourceMapper.Sql.batch(Map.of("kind","RESERVATION"));
        assertTrue(sql.contains("x.id &gt; #{after}"));assertTrue(sql.contains("x.id &lt;= #{maximum}"));
        assertTrue(sql.contains("x.end_time &gt; #{filters.from}"));assertTrue(sql.contains("x.start_time &lt; #{filters.to}"));
        assertTrue(sql.contains("x.reservation_no=#{filters.reservationNo}"));assertFalse(sql.contains("${"));
        assertThrows(IllegalArgumentException.class,()->TaskSourceMapper.Sql.maximum(Map.of("kind","sys_user")));
    }
}
