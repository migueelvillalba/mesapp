package mx.mesapp.printer;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulatedPrinterAdapterTest {
    @TempDir Path directory;
    @Test void recordsTheJobWithoutClaimingPaperWasPrinted() throws Exception {
        var job=new PrintJob(UUID.randomUUID(),UUID.randomUUID(),"COCINA",UUID.randomUUID(),"MESA",UUID.randomUUID(),null,"sin sal",new BigDecimal("45.00"),List.of(new PrintJob.Item("Sopa",1,"caliente")));
        var outcome=new SimulatedPrinterAdapter(directory).print(job);
        assertEquals("PENDIENTE_VERIFICACION",outcome.state());
        assertTrue(Files.readString(directory.resolve("simulated-print.log")).contains("Sopa"));
    }
}
