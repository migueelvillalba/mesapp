package mx.mesapp.printer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

public class PrinterService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final String base = System.getenv().getOrDefault("MESAPP_LOCAL_URL", "http://localhost:8080").replaceAll("/$", "");
    private final String token = System.getenv("MESAPP_PRINTER_TOKEN");
    private final String station = System.getenv().getOrDefault("MESAPP_PRINTER_STATION", "COCINA").trim().toUpperCase(java.util.Locale.ROOT);
    private final String adapterName = System.getenv().getOrDefault("MESAPP_PRINTER_ADAPTER", "SIMULADO").trim().toUpperCase(java.util.Locale.ROOT);
    private final PrinterAdapter adapter = createAdapter(adapterName);
    public static void main(String[] args) throws Exception { new PrinterService().run(); }
    void run() throws Exception {
        if (token == null || token.isBlank()) throw new IllegalStateException("Define MESAPP_PRINTER_TOKEN antes de iniciar el agente.");
        if (!java.util.Set.of("COCINA", "CAJA").contains(station)) throw new IllegalStateException("MESAPP_PRINTER_STATION debe ser COCINA o CAJA.");
        System.out.println("MesApp Printer Service activo; estacion " + station + ", adaptador " + adapterName + ", servidor " + base);
        var executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "mesapp-printer"); t.setDaemon(false); return t; });
        executor.scheduleWithFixedDelay(this::poll, 0, 4, java.util.concurrent.TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));
    }
    void poll() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/printer/jobs/claim?station=" + station)).timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) return;
            if (response.statusCode() != 200) throw new IllegalStateException("Claim respondió HTTP " + response.statusCode());
            PrintJob job = json.readValue(response.body(), PrintJob.class);
            PrinterAdapter.PrintOutcome outcome;
            try { outcome = adapter.print(job); }
            catch (Exception failure) { outcome = new PrinterAdapter.PrintOutcome("FALLIDO", safeMessage(failure)); }
            report(job, outcome);
        } catch (Exception e) { System.err.println("No se pudo consultar o procesar la cola: " + safeMessage(e)); }
    }
    private static PrinterAdapter createAdapter(String name) {
        return switch (name) {
            case "SIMULADO" -> new SimulatedPrinterAdapter();
            case "WINDOWS" -> new WindowsPrinterAdapter(System.getenv("MESAPP_PRINTER_NAME"));
            default -> throw new IllegalStateException("MESAPP_PRINTER_ADAPTER debe ser SIMULADO o WINDOWS.");
        };
    }
    private void report(PrintJob job, PrinterAdapter.PrintOutcome outcome) throws Exception {
        String body = json.writeValueAsString(Map.of("estado", outcome.state(), "error", outcome.detail(), "claimToken", job.claim_token()));
        String id = job.id().toString();
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/printer/jobs/" + id + "/result")).timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("No se pudo registrar resultado (HTTP " + response.statusCode() + "); el trabajo queda enviado para revisión");
        System.out.println("Trabajo " + id + " → " + outcome.state());
    }
    private static String safeMessage(Exception e) { String message=e.getMessage(); return message == null ? e.getClass().getSimpleName() : message.replaceAll("[\\r\\n]", " ").substring(0, Math.min(300, message.length())); }
}
