package dev.kevin.gallbladder;

import dev.kevin.gallbladder.ChartService.ChartData;
import dev.kevin.gallbladder.ChartService.Point;
import dev.kevin.gallbladder.SummaryService.Summary;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** A one-page PDF for appointments: the two-by-two count, the chart, and the episodes in a date range. */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final Color MEAL = new Color(0x2a5d9f);
    private static final Color PAIN = new Color(0x7b52b9);
    private static final Color LIMIT = new Color(0xb00020);
    private static final Color GRID = new Color(0xdddddd);
    private static final Color MUTED = new Color(0x666666);

    private final SummaryService summaryService;
    private final ChartService chartService;
    private final EpisodeRepository episodes;
    private final Clock clock;

    public ReportService(SummaryService summaryService, ChartService chartService, EpisodeRepository episodes,
                         Clock clock) {
        this.summaryService = summaryService;
        this.chartService = chartService;
        this.episodes = episodes;
        this.clock = clock;
    }

    /** {@code from} and {@code to} are both included. */
    @Transactional(readOnly = true)
    public byte[] pdf(LocalDate from, LocalDate to) {
        Summary summary = summaryService.summary(from, to);
        ChartData chart = chartService.between(from, to);
        List<Episode> episodeList = episodes.findStartedBetween(from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 36, 36);
        try {
            BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            Fonts f = new Fonts(new Font(bold, 15), new Font(bold, 10.5f), new Font(regular, 9.5f),
                    new Font(bold, 9), new Font(regular, 9), new Font(regular, 8, Font.NORMAL, MUTED), regular);

            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("Meal fat intake and abdominal pain episodes");
            document.open();

            long days = ChronoUnit.DAYS.between(from, to) + 1;
            document.add(new Paragraph("Meal fat intake and abdominal pain episodes", f.title()));
            document.add(paragraph("Patient-recorded log, " + from.format(DATE) + " to " + to.format(DATE)
                    + " (" + days + (days == 1 ? " day" : " days") + "). Prepared " + LocalDate.now(clock).format(DATE) + ".", f.small(), 2));

            addCounts(document, summary, f);
            addChart(document, writer, chart, f);
            addEpisodes(document, episodeList, f);

            document.add(paragraph("Self-recorded by the patient with a personal tracking app. Fat values are estimates "
                    + "from food tables or the patient's own entries. This record is not a diagnosis.", f.small(), 10));
        } catch (IOException e) {
            throw new IllegalStateException("Built-in PDF fonts are missing", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private record Fonts(Font title, Font heading, Font body, Font cellBold, Font cell, Font small, BaseFont chart) {
    }

    private void addCounts(Document document, Summary s, Fonts f) {
        document.add(heading("Meals followed by pain", f));
        String window = s.lookbackHours() + (s.lookbackHours() == 1 ? " hour" : " hours");
        if (s.threshold() == null) {
            document.add(paragraph("No fat threshold per meal has been set, so meals are not split by fat content. "
                    + s.followed() + " of " + s.meals() + " meals were followed by pain onset within " + window + ".", f.body(), 2));
        } else {
            String limit = grams(s.threshold());
            document.add(paragraph("Fat threshold per meal: " + limit + " grams. A meal counts as followed by pain "
                    + "when an episode began at the time of the meal or up to " + window + " after it.", f.body(), 2));

            PdfPTable table = new PdfPTable(new float[]{2.2f, 1.6f, 1.6f, 1f});
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            for (String h : new String[]{"", "Pain onset within " + window, "No pain within " + window, "Meals"}) {
                table.addCell(cell(h, f.cellBold(), Element.ALIGN_CENTER));
            }
            row(table, "Over " + limit + " g fat", s.overFollowed(), s.overNotFollowed(), s.over(), s, f);
            row(table, "At or under " + limit + " g fat", s.withinFollowed(), s.withinNotFollowed(), s.within(), s, f);
            table.addCell(cell("All meals", f.cellBold(), Element.ALIGN_LEFT));
            table.addCell(cell(String.valueOf(s.followed()), f.cell(), Element.ALIGN_CENTER));
            table.addCell(cell(String.valueOf(s.notFollowed()), f.cell(), Element.ALIGN_CENTER));
            table.addCell(cell(String.valueOf(s.meals()), f.cell(), Element.ALIGN_CENTER));
            document.add(table);
        }

        StringBuilder notes = new StringBuilder();
        notes.append(s.medianOnset() == null
                ? "Median interval from meal to pain onset: none, as no meal was followed by pain. "
                : "Median interval from meal to pain onset: " + duration(s.medianOnset()) + " (from " + s.followed()
                  + (s.followed() == 1 ? " meal). " : " meals). "));
        notes.append("Based on ").append(s.meals()).append(s.meals() == 1 ? " meal" : " meals").append(" and ")
                .append(s.episodes()).append(s.episodes() == 1 ? " episode. " : " episodes. ");
        if (!s.showPercentages()) {
            notes.append("Percentages are not shown with fewer than ")
                    .append(SummaryService.MIN_EPISODES_FOR_PERCENTAGES).append(" episodes. ");
        }
        if (s.pendingMeals() > 0) {
            notes.append(s.pendingMeals() == 1 ? "1 recent meal is" : s.pendingMeals() + " recent meals are")
                    .append(" excluded because their window has not ended. ");
        }
        notes.append("One episode can follow several meals and is then counted for each.");
        document.add(paragraph(notes.toString(), f.small(), 4));
    }

    private static void row(PdfPTable table, String label, int followed, int notFollowed, int total, Summary s, Fonts f) {
        table.addCell(cell(label, f.cellBold(), Element.ALIGN_LEFT));
        table.addCell(cell(count(followed, total, s), f.cell(), Element.ALIGN_CENTER));
        table.addCell(cell(count(notFollowed, total, s), f.cell(), Element.ALIGN_CENTER));
        table.addCell(cell(String.valueOf(total), f.cell(), Element.ALIGN_CENTER));
    }

    private static String count(int part, int whole, Summary s) {
        Long percent = s.showPercentages() ? s.percent(part, whole) : null;
        return percent == null ? String.valueOf(part) : part + " (" + percent + "%)";
    }

    private void addChart(Document document, PdfWriter writer, ChartData data, Fonts f) {
        document.add(heading("Fat per meal and pain episodes", f));
        float width = document.right() - document.left();
        float height = 190;
        PdfTemplate t = writer.getDirectContent().createTemplate(width, height);
        drawChart(t, data, width, height, f.chart());
        try {
            Image image = Image.getInstance(t);
            image.setSpacingBefore(4);
            document.add(image);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Fat in grams on the left axis, pain severity 0 to 10 on the right, the threshold as a dashed line. */
    private static void drawChart(PdfContentByte c, ChartData data, float width, float height, BaseFont font) {
        float left = 34, right = width - 26, bottom = 34, top = height - 8;
        LocalDateTime start = data.from().atStartOfDay();
        double span = Duration.between(start, data.to().plusDays(1).atStartOfDay()).toMinutes();

        double maxFat = data.meals().stream().mapToDouble(p -> p.value().doubleValue()).max().orElse(0);
        if (data.threshold() != null) {
            maxFat = Math.max(maxFat, data.threshold().doubleValue());
        }
        double step = niceStep(Math.max(maxFat, 1) * 1.1 / 4);
        double fatTop = step * Math.ceil(Math.max(maxFat, 1) * 1.1 / step);

        // Grid, axis labels
        c.setLineWidth(0.5f);
        c.setFontAndSize(font, 7);
        for (double v = 0; v <= fatTop + 1e-9; v += step) {
            float y = (float) (bottom + v / fatTop * (top - bottom));
            c.setColorStroke(GRID);
            c.moveTo(left, y);
            c.lineTo(right, y);
            c.stroke();
            text(c, Element.ALIGN_RIGHT, trim(v), left - 3, y - 2.5f, MUTED);
        }
        for (int v = 0; v <= 10; v += 5) {
            text(c, Element.ALIGN_LEFT, String.valueOf(v), right + 3, bottom + v / 10f * (top - bottom) - 2.5f, PAIN);
        }
        long days = ChronoUnit.DAYS.between(data.from(), data.to()) + 1;
        long every = Math.max(1, (long) Math.ceil(days / 6.0));
        for (long d = 0; d < days; d += every) {
            float x = (float) (left + d * 1440 / span * (right - left));
            text(c, Element.ALIGN_LEFT, data.from().plusDays(d).format(SHORT_DAY), x, bottom - 10, MUTED);
        }

        // Threshold
        if (data.threshold() != null) {
            float y = (float) (bottom + data.threshold().doubleValue() / fatTop * (top - bottom));
            c.saveState();
            c.setColorStroke(LIMIT);
            c.setLineWidth(1);
            c.setLineDash(4, 3, 0);
            c.moveTo(left, y);
            c.lineTo(right, y);
            c.stroke();
            c.restoreState();
        }

        // Points
        c.setColorFill(MEAL);
        for (Point p : data.meals()) {
            c.circle(x(p, start, span, left, right), (float) (bottom + p.value().doubleValue() / fatTop * (top - bottom)), 2.2f);
            c.fill();
        }
        c.setColorFill(PAIN);
        for (Point p : data.episodes()) {
            triangle(c, x(p, start, span, left, right), bottom + p.value().floatValue() / 10f * (top - bottom), 3.5f);
        }

        // Legend
        float ly = 6;
        c.setColorFill(MEAL);
        c.circle(left + 3, ly + 2.5f, 2.2f);
        c.fill();
        text(c, Element.ALIGN_LEFT, "Meal, grams of fat (left axis)", left + 9, ly, MUTED);
        c.setColorFill(PAIN);
        triangle(c, left + 143, ly + 2.5f, 3.5f);
        text(c, Element.ALIGN_LEFT, "Pain episode, severity 0 to 10 (right axis)", left + 149, ly, MUTED);
        if (data.threshold() != null) {
            c.saveState();
            c.setColorStroke(LIMIT);
            c.setLineDash(4, 3, 0);
            c.moveTo(left + 318, ly + 2.5f);
            c.lineTo(left + 336, ly + 2.5f);
            c.stroke();
            c.restoreState();
            text(c, Element.ALIGN_LEFT, "Fat threshold per meal", left + 340, ly, MUTED);
        }
    }

    private static float x(Point p, LocalDateTime start, double span, float left, float right) {
        return (float) (left + Duration.between(start, p.at()).toMinutes() / span * (right - left));
    }

    private static void triangle(PdfContentByte c, float x, float y, float r) {
        c.moveTo(x, y + r);
        c.lineTo(x - r, y - r * 0.8f);
        c.lineTo(x + r, y - r * 0.8f);
        c.closePath();
        c.fill();
    }

    private static void text(PdfContentByte c, int align, String s, float x, float y, Color color) {
        c.beginText();
        c.setColorFill(color);
        c.showTextAligned(align, s, x, y, 0);
        c.endText();
    }

    /** 1, 2, 5 or 10 times a power of ten, at least {@code raw}. */
    static double niceStep(double raw) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double m : new double[]{1, 2, 5, 10}) {
            if (m * magnitude >= raw) {
                return m * magnitude;
            }
        }
        return 10 * magnitude;
    }

    private void addEpisodes(Document document, List<Episode> list, Fonts f) {
        document.add(heading("Pain episodes (" + list.size() + ")", f));
        if (list.isEmpty()) {
            document.add(paragraph("No episodes were recorded in this period.", f.body(), 2));
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{1.5f, 0.6f, 2.0f, 0.9f, 1.6f, 2.0f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setHeaderRows(1);
        for (String h : new String[]{"Date", "Onset", "Ended", "Severity (0 to 10)", "Site", "Notes"}) {
            table.addCell(cell(h, f.cellBold(), Element.ALIGN_LEFT));
        }
        for (Episode e : list) {
            table.addCell(cell(e.getStartedAt().format(DAY), f.cell(), Element.ALIGN_LEFT));
            table.addCell(cell(e.getStartedAt().format(TIME), f.cell(), Element.ALIGN_LEFT));
            table.addCell(cell(ended(e), f.cell(), Element.ALIGN_LEFT));
            table.addCell(cell(e.getSeverity() + " / 10", f.cell(), Element.ALIGN_LEFT));
            table.addCell(cell(e.getLocation() == null ? "Not recorded" : e.getLocation(), f.cell(), Element.ALIGN_LEFT));
            String note = e.getNote() == null ? "" : e.getNote();
            if (e.isIncomplete()) {
                note = (note + " Logged quickly; details not filled in.").trim();
            }
            table.addCell(cell(note, f.cell(), Element.ALIGN_LEFT));
        }
        document.add(table);
    }

    /** End time and duration, e.g. "16:30 (2 hours 30 minutes)". */
    static String ended(Episode e) {
        if (e.getEndedAt() == null) {
            return "Not recorded";
        }
        String when = e.getEndedAt().toLocalDate().equals(e.getStartedAt().toLocalDate())
                ? e.getEndedAt().format(TIME)
                : e.getEndedAt().format(SHORT_DAY) + " " + e.getEndedAt().format(TIME);
        return when + " (" + duration(Duration.between(e.getStartedAt(), e.getEndedAt())) + ")";
    }

    /** Written out, e.g. "2 hours 5 minutes" or "45 minutes". */
    static String duration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        String h = hours == 1 ? "1 hour" : hours + " hours";
        String m = minutes == 1 ? "1 minute" : minutes + " minutes";
        if (hours == 0) {
            return m;
        }
        return minutes == 0 ? h : h + " " + m;
    }

    private static String grams(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static Paragraph heading(String text, Fonts f) {
        Paragraph p = new Paragraph(text, f.heading());
        p.setSpacingBefore(12);
        return p;
    }

    private static Paragraph paragraph(String text, Font font, float spacingBefore) {
        Paragraph p = new Paragraph(text, font);
        p.setSpacingBefore(spacingBefore);
        return p;
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        cell.setBorderColor(GRID);
        return cell;
    }
}
