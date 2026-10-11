package mx.mesapp.printer;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public final class TicketFormatter {
    private static final int WIDTH = 40;
    private TicketFormatter() {}

    public static List<String> format(PrintJob job) {
        var lines = new ArrayList<String>();
        lines.add(center("MESAPP"));
        lines.add(center("COMANDA · " + value(job.estacion())));
        lines.add("ORDEN: " + shortId(job.pedido_id()));
        lines.add("FECHA: " + job.fechaLocal());
        lines.add("MESA: " + (job.mesa_numero() == null ? "--" : job.mesa_numero()));
        lines.add("MESERO: " + value(job.mesero()));
        String service = "DOMICILIO".equals(job.modalidad_servicio()) ? "DOMICILIO" : "PARA_LLEVAR".equals(job.modalidad_servicio()) ? "PARA LLEVAR" : "PARA SERVIRSE";
        lines.add("SERVICIO: " + service);
        if ("DOMICILIO".equals(job.tipo()) && job.destino() != null && !job.destino().isBlank()) wrap(lines, "DESTINO: " + job.destino());
        lines.add("-".repeat(WIDTH));
        lines.add("CANT  PRODUCTO                 IMPORTE");
        lines.add("-".repeat(WIDTH));
        if (job.items() != null) for (var item : job.items()) {
            BigDecimal price = item.precio() == null ? BigDecimal.ZERO : item.precio();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(item.cantidad()));
            String label = item.cantidad() + "x " + item.nombre();
            wrap(lines, fit(label, WIDTH - 12) + right(money(amount), 12));
            lines.add("    " + money(price) + " c/u");
            if (item.observaciones() != null && !item.observaciones().isBlank()) wrap(lines, "  Nota: " + item.observaciones());
        }
        lines.add("-".repeat(WIDTH));
        lines.add(right("TOTAL: " + money(job.total()), WIDTH));
        if (job.observaciones() != null && !job.observaciones().isBlank()) {
            lines.add("INDICACIONES:");
            wrap(lines, job.observaciones());
        }
        lines.add("-".repeat(WIDTH));
        lines.add(center("Gracias por su preferencia"));
        lines.add("");
        lines.add("");
        return lines.stream().map(TicketFormatter::printerSafe).toList();
    }

    private static String money(BigDecimal value) { return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static String shortId(java.util.UUID id) { return id == null ? "---" : id.toString().substring(0, 8).toUpperCase(); }
    private static String value(String value) { return value == null || value.isBlank() ? "--" : value; }
    private static String center(String value) { return " ".repeat(Math.max(0, (WIDTH - value.length()) / 2)) + value; }
    private static String right(String value, int width) { return " ".repeat(Math.max(0, width - value.length())) + value; }
    private static String fit(String value, int width) { return value.length() <= width ? value : value.substring(0, width - 1) + "…"; }
    private static void wrap(List<String> out, String value) {
        String remaining = printerSafe(value).trim();
        while (remaining.length() > WIDTH) {
            int split = remaining.lastIndexOf(' ', WIDTH);
            if (split < 1) split = WIDTH;
            out.add(remaining.substring(0, split));
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) out.add(remaining);
    }
    private static String printerSafe(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replace('ñ', 'n').replace('Ñ', 'N').replace('·', '-')
            .replaceAll("[^\\x20-\\x7E]", "?");
    }
}
