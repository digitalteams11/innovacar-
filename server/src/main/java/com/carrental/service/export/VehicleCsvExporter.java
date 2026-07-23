package com.carrental.service.export;

import com.carrental.dto.export.VehicleExportRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

/**
 * Real CSV — UTF-8 BOM (so Excel doesn't guess the wrong codepage and
 * corrupt accents), RFC 4180 quoting, CRLF line endings. This is the fix for
 * the "ModÃ¨le" mojibake bug: the old export had no BOM and no quoting at all.
 */
@Component
public class VehicleCsvExporter {

    private static final String CRLF = "\r\n";

    public void write(OutputStream out, List<VehicleExportRow> rows, String[] headers,
                       List<Function<VehicleExportRow, Object>> columns, char delimiter) throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        // UTF-8 BOM — without this, French/Arabic-locale Excel opens the file
        // assuming the system ANSI codepage and every accented character
        // (Modèle -> ModÃ¨le) is corrupted on screen even though the bytes are fine.
        writer.write('﻿');

        writeRow(writer, headers, delimiter);
        for (VehicleExportRow row : rows) {
            Object[] values = columns.stream().map(f -> f.apply(row)).toArray();
            String[] strings = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                strings[i] = values[i] == null ? "" : String.valueOf(values[i]);
            }
            writeRow(writer, strings, delimiter);
        }
        writer.flush();
    }

    private void writeRow(Writer writer, String[] values, char delimiter) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(delimiter);
            writer.write(quote(values[i], delimiter));
        }
        writer.write(CRLF);
    }

    /** RFC 4180: wrap in quotes and escape any embedded quote as a doubled quote. */
    private String quote(String value, char delimiter) {
        if (value == null) return "";
        boolean needsQuoting = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuoting ? "\"" + escaped + "\"" : escaped;
    }
}
