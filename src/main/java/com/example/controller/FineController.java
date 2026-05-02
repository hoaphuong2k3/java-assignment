package com.example.controller;

import com.example.dao.FineDAO;
import com.example.model.Fine;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class FineController {

    @FXML private Label totalUnpaidLabel;
    @FXML private Label unpaidCountLabel;

    @FXML private TextField    searchField;
    @FXML private ComboBox<String> paidFilter;
    @FXML private DatePicker   fromDatePicker;
    @FXML private DatePicker   toDatePicker;
    @FXML private TableView<Fine> fineTable;
    @FXML private TableColumn<Fine, Void>   colSTT;
    @FXML private TableColumn<Fine, String> colMember;
    @FXML private TableColumn<Fine, String> colBook;
    @FXML private TableColumn<Fine, String> colDueDate;
    @FXML private TableColumn<Fine, String> colReturnDate;
    @FXML private TableColumn<Fine, String> colAmount;
    @FXML private TableColumn<Fine, String> colReason;
    @FXML private TableColumn<Fine, String> colPaid;
    @FXML private TableColumn<Fine, Void>   colActions;
    @FXML private Label  statusLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private Label  pageInfoLabel;

    private static final int PAGE_SIZE  = 20;
    private int           currentPage   = 0;
    private List<Fine>    masterList    = new java.util.ArrayList<>();
    private List<Fine>    filteredList  = new java.util.ArrayList<>();

    private final FineDAO fineDAO = new FineDAO();
    private final NumberFormat currencyFormat = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

    @FXML
    public void initialize() {
        paidFilter.setItems(FXCollections.observableArrayList("Tất cả", "Chưa thanh toán", "Đã thanh toán"));
        paidFilter.setValue("Tất cả");
        setupColumns();
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        paidFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        fromDatePicker.valueProperty().addListener((obs, o, n) -> applyFilter());
        toDatePicker.valueProperty().addListener((obs, o, n) -> applyFilter());
        loadFines();
        updateSummary();
    }

    private void setupColumns() {
        colSTT.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(currentPage * PAGE_SIZE + getIndex() + 1));
            }
        });
        colMember.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getMemberCode() + " - " + c.getValue().getMemberName()));
        colBook.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBookTitle()));
        colDueDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getDueDate() != null ? c.getValue().getDueDate().toString() : ""));
        colReturnDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getReturnDate() != null ? c.getValue().getReturnDate().toString() : "—"));
        colAmount.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getAmount() != null ? currencyFormat.format(c.getValue().getAmount()) + "đ" : "0đ"));
        colReason.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReason()));
        colPaid.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isPaid() ? "Đã thanh toán" : "Chưa TT"));
        colPaid.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label badge = new Label(item);
                badge.getStyleClass().add(item.startsWith("Đã") ? "badge-success" : "badge-danger");
                setGraphic(badge);
            }
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button payBtn = new Button("Đã thu");
            private final HBox   box    = new HBox(payBtn);
            {
                payBtn.getStyleClass().add("btn-success");
                payBtn.setStyle("-fx-font-size:11px; -fx-padding:4 10 4 10;");
                payBtn.setOnAction(e -> handleMarkPaid(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Fine f = getTableView().getItems().get(getIndex());
                setGraphic(f.isPaid() ? null : box);
            }
        });
    }

    @FXML
    private void handleSearch() { applyFilter(); }

    @FXML
    private void clearDateFilter() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
    }

    private void applyFilter() {
        String kw      = searchField.getText().toLowerCase().trim();
        String paid    = paidFilter.getValue();
        LocalDate from = fromDatePicker.getValue();
        LocalDate to   = toDatePicker.getValue();
        filteredList = masterList.stream()
            .filter(f -> {
                boolean paidOk = "Tất cả".equals(paid)
                    || ("Chưa thanh toán".equals(paid) && !f.isPaid())
                    || ("Đã thanh toán".equals(paid) && f.isPaid());
                boolean kwOk = kw.isEmpty()
                    || f.getMemberName().toLowerCase().contains(kw)
                    || f.getBookTitle().toLowerCase().contains(kw)
                    || f.getMemberCode().toLowerCase().contains(kw);
                boolean dateOk = true;
                LocalDate createdDay = f.getCreatedAt() != null ? f.getCreatedAt().toLocalDate() : null;
                if (from != null) {
                    if (createdDay == null) dateOk = false;
                    else dateOk = !createdDay.isBefore(from);
                }
                if (to != null && dateOk) {
                    if (createdDay == null) dateOk = false;
                    else dateOk = !createdDay.isAfter(to);
                }
                return paidOk && kwOk && dateOk;
            })
            .toList();
        currentPage = 0;
        showPage();
    }

    private void showPage() {
        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, filteredList.size());
        fineTable.setItems(FXCollections.observableArrayList(
            from < filteredList.size() ? filteredList.subList(from, to) : List.of()));
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredList.size() / PAGE_SIZE));
        pageInfoLabel.setText("Trang " + (currentPage + 1) + " / " + totalPages);
        statusLabel.setText(filteredList.size() + " phiếu phạt");
        prevPageBtn.setDisable(currentPage == 0);
        nextPageBtn.setDisable((currentPage + 1) * PAGE_SIZE >= filteredList.size());
        fineTable.refresh();
    }

    @FXML private void prevPage() { if (currentPage > 0) { currentPage--; showPage(); } }
    @FXML private void nextPage() { if ((currentPage + 1) * PAGE_SIZE < filteredList.size()) { currentPage++; showPage(); } }

    private void handleMarkPaid(Fine fine) {
        String bookLine = fine.getBookTitle() != null ? fine.getBookTitle() : "—";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Xác nhận đã thu phí phạt " + currencyFormat.format(fine.getAmount()) + "đ\n" +
            "Từ: " + fine.getMemberName() + "\nSách / dịch vụ: " + bookLine,
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Thu phí phạt");
        confirm.showAndWait().ifPresent(t -> {
            if (t == ButtonType.YES) {
                fineDAO.markAsPaid(fine.getId());
                loadFines();
                updateSummary();
                showInfo("Đã ghi nhận thanh toán phí phạt.");
            }
        });
    }

    private void loadFines() {
        masterList = fineDAO.findAll();
        applyFilter();
    }

    private void updateSummary() {
        var unpaidAmount = fineDAO.totalUnpaidAmount();
        var unpaid = fineDAO.findUnpaid();
        totalUnpaidLabel.setText(currencyFormat.format(unpaidAmount) + "đ");
        unpaidCountLabel.setText(String.valueOf(unpaid.size()));
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }
}
