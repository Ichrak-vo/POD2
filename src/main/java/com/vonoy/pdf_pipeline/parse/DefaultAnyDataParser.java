package com.vonoy.pdf_pipeline.parse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.vonoy.pdf_pipeline.transport.RawPayload;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class DefaultAnyDataParser implements AnyDataParser {
    private final ObjectMapper json = new ObjectMapper();
    private final XmlMapper xml = new XmlMapper();

    @Override public JsonNode parse(RawPayload payload){
        try {
            byte[] bytes = payload.stream().readAllBytes();
            String ct = payload.contentType() != null ? payload.contentType() : "";

            if (ct.contains("json")) return json.readTree(bytes);
            if (ct.contains("xml"))  return xml.readTree(bytes);
            if (ct.contains("csv"))  return csvToArray(bytes);
            if (ct.contains("spreadsheetml") || ct.contains("excel")) return xlsxToArray(bytes);

            // Fallback: essayer JSON puis XML
            try { return json.readTree(bytes); } catch (Exception ignore){}
            try { return xml.readTree(bytes); } catch (Exception ignore){}
            // Dernier recours : envelopper en objet
            ObjectNode o = json.createObjectNode();
            o.put("raw", new String(bytes));
            return o;
        } catch (IOException e){ throw new RuntimeException(e); }
    }

    private ArrayNode csvToArray(byte[] bytes) throws IOException {
        CsvMapper mapper = new CsvMapper();
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        var it = mapper.readerFor(Object.class).with(schema).readValues(bytes);
        ArrayNode arr = json.createArrayNode();
        while (it.hasNext()) arr.addPOJO(it.next());
        return arr;
    }

    private ArrayNode xlsxToArray(byte[] bytes) throws IOException {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            ArrayNode arr = json.createArrayNode();
            Row header = sheet.getRow(0);
            if (header == null) return arr;
            for (int i=1; i<=sheet.getLastRowNum(); i++){
                Row r = sheet.getRow(i); if (r==null) continue;
                ObjectNode obj = json.createObjectNode();
                for (int c=0; c<header.getLastCellNum(); c++){
                    String key = header.getCell(c).getStringCellValue();
                    Cell cell = r.getCell(c);
                    obj.put(key, cell==null? "" : cell.toString());
                }
                arr.add(obj);
            }
            return arr;
        }
    }
}