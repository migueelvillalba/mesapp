package mx.mesapp.printer;

public interface PrinterAdapter {
    PrintOutcome print(PrintJob job) throws Exception;
    record PrintOutcome(String state, String detail) {}
}
