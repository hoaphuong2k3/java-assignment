package com.example.controller;

import com.example.service.ReportService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportController {

    @FXML private ComboBox<Integer> yearCombo;

    @FXML private Label lblYearBorrows;
    @FXML private Label lblTotalBooks;
    @FXML private Label lblActiveMembers;
    @FXML private Label lblOverdue;
    @FXML private Label lblRenewalFees;

    @FXML private DatePicker memberJoinFrom;
    @FXML private DatePicker memberJoinTo;
    @FXML private Label lblJoinCount;

    @FXML private BarChart<String, Number> monthlyChart;
    @FXML private CategoryAxis monthlyXAxis;
    @FXML private NumberAxis monthlyYAxis;

    @FXML private TableView<Map<String, Object>> topBooksTable;
    @FXML private TableColumn<Map<String, Object>, String> colBRank;
    @FXML private TableColumn<Map<String, Object>, String> colBTitle;
    @FXML private TableColumn<Map<String, Object>, String> colBAuthor;
    @FXML private TableColumn<Map<String, Object>, String> colBCount;
    @FXML private TableColumn<Map<String, Object>, String> colBBar;

    @FXML private TableView<Map<String, Object>> categoryTable;
    @FXML private TableColumn<Map<String, Object>, String> colCatName;
    @FXML private TableColumn<Map<String, Object>, String> colCatCount;
    @FXML private TableColumn<Map<String, Object>, String> colCatBar;

    @FXML private TableView<Map<String, Object>> topMembersTable;
    @FXML private TableColumn<Map<String, Object>, String> colMRank;
    @FXML private TableColumn<Map<String, Object>, String> colMCode;
    @FXML private TableColumn<Map<String, Object>, String> colMName;
    @FXML private TableColumn<Map<String, Object>, String> colMCount;
    @FXML private TableColumn<Map<String, Object>, String> colMBar;

    private final ReportService reportService = new ReportService();

    private static final String[] MONTH_NAMES = {
        "", "T1", "T2", "T3", "T4", "T5", "T6",
        "T7", "T8", "T9", "T10", "T11", "T12"
    };

    @FXML
    public void initialize() {
        int currentYear = LocalDate.now().getYear();
        yearCombo.getItems().addAll(currentYear - 2, currentYear - 1, currentYear);
        yearCombo.setValue(currentYear);
        yearCombo.setOnAction(e -> loadData());

        memberJoinFrom.valueProperty().addListener((o, a, n) -> refreshJoinCountOnly());
        memberJoinTo.valueProperty().addListener((o, a, n) -> refreshJoinCountOnly());

        setupTopBooksTable();
        setupCategoryTable();
        setupTopMembersTable();
        loadData();
    }

    @FXML
    private void refresh() {
        loadData();
    }

    private void loadData() {
        int year = yearCombo.getValue() != null ? yearCombo.getValue() : LocalDate.now().getYear();

        // Monthly chart
        List<Map<String, Object>> monthly = reportService.getMonthlyBorrowStats(year);
        populateMonthlyChart(monthly);

        long yearTotal = monthly.stream()
            .mapToLong(m -> ((Number) m.get("total")).longValue()).sum();
        lblYearBorrows.setText(String.valueOf(yearTotal));

        // Dashboard KPIs
        try {
            Map<String, Object> stats = reportService.getDashboardStats();
            lblTotalBooks.setText(String.valueOf(stats.get("totalBooks")));
            lblActiveMembers.setText(String.valueOf(stats.get("totalMembers")));
            lblOverdue.setText(String.valueOf(stats.get("overdueCount")));
        } catch (Exception e) {
            lblTotalBooks.setText("—");
            lblActiveMembers.setText("—");
            lblOverdue.setText("—");
        }

        NumberFormat money = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        try {
            Map<String, Object> renewal = reportService.getCardRenewalFineSummary(year);
            BigDecimal total = (BigDecimal) renewal.get("total");
            long rc = ((Number) renewal.get("count")).longValue();
            lblRenewalFees.setText(money.format(total.longValue()) + "đ · " + rc + " phiếu");
        } catch (Exception e) {
            lblRenewalFees.setText("—");
        }

        refreshJoinCountOnly();

        // Top books
        List<Map<String, Object>> topBooks = reportService.getTopBorrowedBooks(10);
        AtomicInteger rank = new AtomicInteger(1);
        long maxBook = topBooks.isEmpty() ? 1L
            : ((Number) topBooks.get(0).get("borrowCount")).longValue();
        topBooks.forEach(m -> {
            m.put("rank", String.valueOf(rank.getAndIncrement()));
            m.put("ratio", String.format("%.4f",
                ((Number) m.get("borrowCount")).doubleValue() / maxBook));
        });
        topBooksTable.setItems(FXCollections.observableArrayList(topBooks));

        // Category
        List<Map<String, Object>> cats = reportService.getBooksPerCategory();
        long maxCat = cats.stream()
            .mapToLong(m -> ((Number) m.get("bookCount")).longValue()).max().orElse(1);
        cats.forEach(m -> m.put("ratio", String.format("%.4f",
            ((Number) m.get("bookCount")).doubleValue() / maxCat)));
        categoryTable.setItems(FXCollections.observableArrayList(cats));

        // Top members
        List<Map<String, Object>> topMembers = reportService.getTopActiveMembers(10);
        rank.set(1);
        long maxMembers = topMembers.isEmpty() ? 1L
            : ((Number) topMembers.get(0).get("borrowCount")).longValue();
        topMembers.forEach(m -> {
            m.put("rank", String.valueOf(rank.getAndIncrement()));
            m.put("ratio", String.format("%.4f",
                ((Number) m.get("borrowCount")).doubleValue() / maxMembers));
        });
        topMembersTable.setItems(FXCollections.observableArrayList(topMembers));
    }

    private void refreshJoinCountOnly() {
        LocalDate jf = memberJoinFrom.getValue();
        LocalDate jt = memberJoinTo.getValue();
        if (jf != null && jt != null) {
            long jc = reportService.countMembersJoinedBetween(jf, jt);
            lblJoinCount.setText(jc + " đọc giả đăng ký (mở thẻ) trong khoảng ngày đã chọn");
        } else {
            lblJoinCount.setText("Chọn từ ngày và đến ngày để xem số thẻ mở");
        }
    }

    private void populateMonthlyChart(List<Map<String, Object>> monthly) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Lượt mượn");
        for (int i = 1; i <= 12; i++) {
            final int month = i;
            long val = monthly.stream()
                .filter(m -> ((Number) m.get("month")).intValue() == month)
                .mapToLong(m -> ((Number) m.get("total")).longValue())
                .findFirst().orElse(0L);
            series.getData().add(new XYChart.Data<>(MONTH_NAMES[month], val));
        }
        monthlyChart.getData().clear();
        monthlyChart.getData().add(series);
    }

    private void setupTopBooksTable() {
        colBRank.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("rank")));
        colBTitle.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("title")));
        colBAuthor.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("author")));
        colBCount.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("borrowCount"))));
        colBBar.setCellFactory(col -> buildProgressCell("#4F46E5"));
        colBBar.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("ratio")));
    }

    private void setupCategoryTable() {
        colCatName.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("category")));
        colCatCount.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("bookCount"))));
        colCatBar.setCellFactory(col -> buildProgressCell("#059669"));
        colCatBar.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("ratio")));
    }

    private void setupTopMembersTable() {
        colMRank.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("rank")));
        colMCode.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("memberCode")));
        colMName.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("fullName")));
        colMCount.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("borrowCount"))));
        colMBar.setCellFactory(col -> buildProgressCell("#D97706"));
        colMBar.setCellValueFactory(c -> new SimpleStringProperty((String) c.getValue().get("ratio")));
    }

    private TableCell<Map<String, Object>, String> buildProgressCell(String accent) {
        return new TableCell<>() {
            private final ProgressBar bar = new ProgressBar(0);
            {
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(10);
                bar.setStyle("-fx-accent: " + accent + ";");
                HBox.setHgrow(bar, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                bar.setProgress(Double.parseDouble(item));
                setGraphic(bar);
                setText(null);
            }
        };
    }
}
