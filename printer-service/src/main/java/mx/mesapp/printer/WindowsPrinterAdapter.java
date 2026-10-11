package mx.mesapp.printer;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;

/** Sends a receipt to the installed Windows printer queue and its configured driver. */
public class WindowsPrinterAdapter implements PrinterAdapter {
    private static final double PRINT_WIDTH_POINTS = 72d * 72d / 25.4d;
    private final String printerName;
    public WindowsPrinterAdapter(String printerName) { this.printerName = printerName == null ? "" : printerName.trim(); }

    @Override public PrintOutcome print(PrintJob job) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) throw new IllegalStateException("El adaptador WINDOWS requiere un equipo Windows con el controlador instalado.");
        if (printerName.isBlank()) throw new IllegalStateException("Define MESAPP_PRINTER_NAME con el nombre de la cola de Windows.");
        PrintService service = findPrinter(printerName);
        PrinterJob printerJob = PrinterJob.getPrinterJob();
        printerJob.setPrintService(service);
        var lines = TicketFormatter.format(job);
        double lineHeight = 12d;
        double height = Math.max(120d, (lines.size() * lineHeight) + 18d);
        Paper paper = new Paper();
        paper.setSize(PRINT_WIDTH_POINTS, height);
        paper.setImageableArea(4d, 5d, PRINT_WIDTH_POINTS - 8d, height - 10d);
        PageFormat format = new PageFormat();
        format.setOrientation(PageFormat.PORTRAIT);
        format.setPaper(paper);
        printerJob.setPrintable((Graphics graphics, PageFormat page, int pageIndex) -> {
            if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(page.getImageableX(), page.getImageableY());
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
                int y = 9;
                for (String line : lines) { g.drawString(line, 0, y); y += (int) lineHeight; }
                return java.awt.print.Printable.PAGE_EXISTS;
            } finally { g.dispose(); }
        }, format);
        printerJob.print();
        return new PrintOutcome("CONFIRMADO", "Windows acepto el ticket en la cola de impresion; verifica el papel y el corte.");
    }

    private static PrintService findPrinter(String requested) {
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null))
            if (service.getName().equalsIgnoreCase(requested)) return service;
        throw new IllegalStateException("No se encontro la impresora de Windows configurada: " + requested);
    }
}
