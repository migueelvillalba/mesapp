package mx.mesapp.printer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Writes a human-readable trace. It cannot confirm physical paper output. */
public class SimulatedPrinterAdapter implements PrinterAdapter {
    private final Path spool;
    public SimulatedPrinterAdapter(){this(Path.of(System.getenv().getOrDefault("MESAPP_PRINTER_SPOOL", "./printer-spool")));}
    public SimulatedPrinterAdapter(Path spool){this.spool=spool;}
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @Override public PrintOutcome print(PrintJob job) throws Exception {
        Files.createDirectories(spool);
        Path trace = spool.resolve("simulated-print.log");
        String rendered = "--- SIMULACIÓN " + Instant.now() + " ---\n" + json.writerWithDefaultPrettyPrinter().writeValueAsString(job) + "\n";
        Files.writeString(trace, rendered, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return new PrintOutcome("PENDIENTE_VERIFICACION", "Salida de prueba escrita; no se verificó papel físico");
    }
}
