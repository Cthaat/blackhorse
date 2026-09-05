package com.ruoyi.lab.task;

import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** XLSX only, no formulas/macros; total expanded input is bounded before POI parses it. */
@Component
public class TaskWorkbook
{
    public static List<String> columns(String kind)
    {
        return switch (kind) {
            case "LABORATORY" -> List.of("labCode", "name", "deptId", "managerId", "location", "description");
            case "DEVICE" -> List.of("assetNo", "laboratoryId", "name", "categoryCode", "model", "riskLevel", "location", "managerId", "description");
            default -> throw new IllegalArgumentException("不支持此导入类型");
        };
    }

    public List<Map<String,String>> read(byte[] bytes, String kind) throws IOException
    {
        TaskRules.validateUpload(bytes.length);
        inspectZip(bytes);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes)))
        {
            if (book.isMacroEnabled() || book.getNumberOfSheets() != 1) throw new IllegalArgumentException("请使用单工作表且不含宏的模板");
            var sheet = book.getSheetAt(0);
            if (sheet.getLastRowNum() > 5000) throw new IllegalArgumentException("最多允许五千行");
            var headers = columns(kind);
            Row first = sheet.getRow(0);
            if (first == null || first.getLastCellNum() != headers.size()) throw new IllegalArgumentException("模板列不匹配");
            for (int c=0;c<headers.size();c++) if (!headers.get(c).equals(value(first.getCell(c)))) throw new IllegalArgumentException("模板列不匹配");
            List<Map<String,String>> rows = new ArrayList<>();
            for (int n=1;n<=sheet.getLastRowNum();n++)
            {
                Row row = sheet.getRow(n);
                if (row == null) continue;
                if (row.getLastCellNum() > headers.size()) throw new IllegalArgumentException("模板包含额外列");
                Map<String,String> data = new LinkedHashMap<>();
                for (int c=0;c<headers.size();c++) data.put(headers.get(c), value(row.getCell(c)));
                if (data.values().stream().allMatch(String::isBlank)) continue;
                data.put("_row", Integer.toString(n+1));
                rows.add(data);
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("文件没有数据行");
            return rows;
        }
    }

    private static String value(Cell cell)
    {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
            throw new IllegalArgumentException("文件不能包含公式或错误单元格");
        String text = new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
        if (text.length() > 1000) throw new IllegalArgumentException("单元格内容超过限制");
        return text;
    }

    private static void inspectZip(byte[] bytes) throws IOException
    {
        long expanded = 0; int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes)))
        {
            byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
            {
                if (++entries > 100 || entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject"))
                    throw new IllegalArgumentException("工作簿结构不受支持");
                int read;
                while ((read = zip.read(buffer)) >= 0)
                {
                    expanded += read;
                    if (expanded > 25L * 1024 * 1024) throw new IllegalArgumentException("工作簿展开大小超过限制");
                }
            }
        }
        if (entries == 0) throw new IllegalArgumentException("请上传有效工作簿");
    }

    public byte[] write(List<String> headers, List<List<String>> rows, String description) throws IOException
    {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream())
        {
            var sheet = book.createSheet("数据");
            put(sheet.createRow(0), headers);
            int n = 1; for (var row : rows) put(sheet.createRow(n++), row);
            sheet.createFreezePane(0, 1);
            for (int i=0;i<headers.size();i++) sheet.setColumnWidth(i, 24*256);
            if (description != null) put(book.createSheet("生成说明").createRow(0), List.of(description));
            book.write(output); return output.toByteArray();
        }
    }
    private static void put(Row row, List<String> values)
    {
        for (int i=0;i<values.size();i++)
            row.createCell(i, CellType.STRING).setCellValue(Objects.toString(values.get(i), ""));
    }
}
