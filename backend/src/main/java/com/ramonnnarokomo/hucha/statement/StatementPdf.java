package com.ramonnnarokomo.hucha.statement;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.ramonnnarokomo.hucha.account.Iban;
import com.ramonnnarokomo.hucha.ledger.EntryType;
import com.ramonnnarokomo.hucha.shared.Money;

/** Extracto mensual en PDF con OpenPDF. */
@Component
public class StatementPdf {

    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color BRAND = new Color(15, 118, 110);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LINE = new Color(226, 232, 240);
    private static final Color NEGATIVE = new Color(185, 28, 28);

    private final Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND);
    private final Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    private final Font body = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private final Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);
    private final Font headerCell = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);

    public byte[] render(StatementService.Statement statement, ZoneId zone) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();

        document.add(new Paragraph("Hucha", title));
        document.add(new Paragraph("Extracto de cuenta · " + monthName(statement), heading));
        document.add(new Paragraph(statement.holderName() + "   ·   " + statement.account().getName()
                + "   ·   " + Iban.format(statement.account().getIban()), small));
        document.add(new Paragraph(" "));

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summaryCell(summary, "Saldo inicial", statement.openingBalance());
        summaryCell(summary, "Entradas", statement.totalIncome());
        summaryCell(summary, "Salidas", statement.totalExpenses().negate());
        summaryCell(summary, "Saldo final", statement.closingBalance());
        document.add(summary);
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(new float[] {1.6f, 5.4f, 1.8f, 1.8f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : new String[] {"FECHA", "CONCEPTO", "IMPORTE", "SALDO"}) {
            PdfPCell cell = cell(new Phrase(header, headerCell));
            if (!header.equals("FECHA") && !header.equals("CONCEPTO")) {
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            table.addCell(cell);
        }
        if (statement.movements().isEmpty()) {
            PdfPCell empty = cell(new Phrase("No hay movimientos en este periodo.", small));
            empty.setColspan(4);
            table.addCell(empty);
        }
        for (MovementView movement : statement.movements()) {
            table.addCell(cell(new Phrase(DATE.format(movement.occurredAt().atZone(zone)), body)));
            // En los pagos con tarjeta lo importante es el comercio; en el resto, el concepto.
            boolean card = movement.type() == EntryType.CARD_PAYMENT;
            String main = card ? movement.counterpartyName() : nonNull(movement.concept(), movement.counterpartyName());
            String detail = card ? movement.concept() : movement.counterpartyName();
            Phrase description = new Phrase(main != null ? main : "", body);
            if (detail != null && !detail.equals(main)) {
                description.add(new Phrase("\n" + detail, small));
            }
            table.addCell(cell(description));
            table.addCell(amountCell(movement.amount(), true));
            table.addCell(amountCell(movement.balanceAfter(), false));
        }
        document.add(table);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("Documento generado por Hucha, una banca de demostración. "
                + "No es un extracto bancario real.", small));
        document.close();
        return out.toByteArray();
    }

    private void summaryCell(PdfPTable table, String label, BigDecimal amount) {
        Phrase phrase = new Phrase(label + "\n", small);
        phrase.add(new Phrase(Money.format(amount), heading));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderColor(LINE);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private PdfPCell amountCell(BigDecimal amount, boolean colorNegative) {
        Font font = colorNegative && amount.signum() < 0
                ? FontFactory.getFont(FontFactory.HELVETICA, 9, NEGATIVE)
                : body;
        PdfPCell cell = cell(new Phrase(Money.format(amount), font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private static PdfPCell cell(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setPaddingTop(5);
        cell.setPaddingBottom(6);
        return cell;
    }

    private static String monthName(StatementService.Statement statement) {
        String month = statement.month().getMonth().getDisplayName(TextStyle.FULL, SPANISH);
        return month + " de " + statement.month().getYear();
    }

    private static String nonNull(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null ? second : "";
    }
}
